package com.example.multipaymentgateway.service;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;
import com.example.multipaymentgateway.exception.PaymentProcessingException;
import com.example.multipaymentgateway.model.Payment;
import com.example.multipaymentgateway.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service("stripeService")
public class StripeService implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    private final PaymentRepository paymentRepository;
    private boolean stripeInitialized = false;

    public StripeService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isEmpty() || stripeSecretKey.equals("sk_test_YOUR_STRIPE_SECRET_KEY") || stripeSecretKey.equals("YOUR_STRIPE_SECRET_KEY")) {
            logger.warn("Stripe API Key is not configured or using default placeholder. Stripe client will not be initialized.");
            return;
        }
        Stripe.apiKey = stripeSecretKey;
        stripeInitialized = true;
        logger.info("Stripe client initialized successfully.");
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        if (!stripeInitialized) {
            logger.error("Stripe client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Stripe service is not available. Please check configuration.");
        }

        String internalTransactionId = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setTransactionId(internalTransactionId);
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency(paymentRequest.getCurrency().toLowerCase()); // Stripe expects lowercase currency
        payment.setPaymentGateway(getGatewayName());
        payment.setStatus("PENDING");
        payment = paymentRepository.save(payment);

        try {
            PaymentIntentCreateParams.Builder paramsBuilder =
                PaymentIntentCreateParams.builder()
                    .setAmount(paymentRequest.getAmount().multiply(new BigDecimal(100)).longValue()) // Amount in cents
                    .setCurrency(paymentRequest.getCurrency().toLowerCase())
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC) // Or MANUAL
                    .setConfirm(true); // Attempt to confirm the PaymentIntent immediately.
                                       // Requires a payment method.

            // For this server-side flow, we'd typically use `payment_method_types` and then handle client-side confirmation.
            // Or, if a payment_method_id is passed from client: paramsBuilder.setPaymentMethod("pm_card_visa");
            // To make this runnable without client-side, we can use a test payment method if available
            // or rely on a flow where payment_method is attached later.
            // For now, let's use automatic payment methods if enabled in Stripe dashboard, or a test card.
            // This example assumes you might pass a payment method ID from the request or use automatic_payment_methods.
            // If paymentRequest.getPaymentMethodId() is available:
            // paramsBuilder.setPaymentMethod(paymentRequest.getPaymentMethodId());
            // else, rely on automatic_payment_methods or default test cards for this example.
            // For a simple charge (not recommended for new integrations, use PaymentIntents):
            // ChargeCreateParams chargeParams = ChargeCreateParams.builder()...

            // Using a common test payment method for off-session/server-initiated like behavior
            // This is for testing and might not work for all scenarios or live mode without proper setup.
            // In real scenario: the client would provide a PaymentMethod ID (e.g., "pm_card_visa")
            // which you would include here.
             if (paymentRequest.getPaymentMethodId() != null && !paymentRequest.getPaymentMethodId().isBlank()){
                 paramsBuilder.setPaymentMethod(paymentRequest.getPaymentMethodId());
             } else {
                 // If no payment method ID is provided, this PaymentIntent will be created
                 // but will require client-side action to confirm with a payment method.
                 // For testing, sometimes Stripe allows confirming without a PM if you have a default test source
                 // or if you are using specific test card numbers that don't require a full PM object.
                 // However, the robust way is to get a PM from the client.
                 // For this example, let's assume the intent is created and needs client action.
                 // We will not setConfirm(true) if no payment method is provided.
                 paramsBuilder.setConfirm(false); // Create the intent, client will confirm
                 paramsBuilder.addPaymentMethodType("card"); // Specify acceptable payment types
             }


            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
            logger.info("Stripe PaymentIntent created: {} for internal transaction ID: {}", paymentIntent.getId(), internalTransactionId);

            payment.setGatewayTransactionId(paymentIntent.getId());
            payment.setStatus(mapStripePaymentIntentStatus(paymentIntent.getStatus()));
            paymentRepository.save(payment);

            PaymentResponse response = createPaymentResponse(payment, "Stripe PaymentIntent created. Status: " + paymentIntent.getStatus(), paymentIntent.getClientSecret());

            // If requires_action or requires_confirmation, client_secret is needed by the client.
            if ("requires_action".equals(paymentIntent.getStatus()) || "requires_confirmation".equals(paymentIntent.getStatus()) || "requires_payment_method".equals(paymentIntent.getStatus())) {
                response.setRedirectUrl(null); // No specific redirect URL, client uses client_secret with Stripe.js
                response.setStatus("PENDING_USER_ACTION");
                Map<String, Object> gatewaySpecific = new HashMap<>();
                gatewaySpecific.put("stripe_payment_intent_id", paymentIntent.getId());
                gatewaySpecific.put("stripe_client_secret", paymentIntent.getClientSecret());
                response.setGatewaySpecificResponse(gatewaySpecific);
                 response.setMessage("Stripe PaymentIntent created. Client action required using client_secret.");
            } else if ("succeeded".equals(paymentIntent.getStatus())) {
                 response.setMessage("Stripe payment processed successfully.");
            }


            return response;

        } catch (StripeException e) {
            logger.error("Stripe API error during payment processing for transactionId {}: {} - {}", internalTransactionId, e.getCode(), e.getMessage(), e);
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Stripe payment failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during Stripe payment processing for transactionId {}: {}", internalTransactionId, e.getMessage(), e);
            payment.setStatus("ERROR");
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Unexpected error during Stripe payment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse getPaymentStatus(String transactionId) {
        // This `transactionId` is OUR internal system's transaction ID.
        if (!stripeInitialized) {
            logger.error("Stripe client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Stripe service is not available. Please check configuration.");
        }
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentProcessingException("Payment not found with internal transaction ID: " + transactionId));

        String stripePaymentIntentId = payment.getGatewayTransactionId();
        if (stripePaymentIntentId == null || stripePaymentIntentId.isEmpty()) {
            logger.warn("Stripe PaymentIntent ID is missing for internal transaction ID: {}. Cannot query Stripe.", transactionId);
            return createPaymentResponse(payment, "Cannot fetch status from Stripe: Gateway Transaction ID (PaymentIntent ID) is missing.", null);
        }

        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(stripePaymentIntentId);
            String stripeStatus = paymentIntent.getStatus();
            logger.info("Stripe PaymentIntent {} status: {}", stripePaymentIntentId, stripeStatus);

            payment.setStatus(mapStripePaymentIntentStatus(stripeStatus));
            // Update amount if it can change (e.g. for some payment methods or if not set initially from intent)
            // payment.setAmount(new BigDecimal(paymentIntent.getAmountReceived()).divide(new BigDecimal(100)));
            paymentRepository.save(payment);

            return createPaymentResponse(payment, "Payment status retrieved successfully from Stripe: " + stripeStatus, paymentIntent.getClientSecret());

        } catch (StripeException e) {
            logger.error("Stripe API error fetching status for PaymentIntent {}: {}", stripePaymentIntentId, e.getMessage(), e);
            throw new PaymentProcessingException("Stripe status fetch failed for " + stripePaymentIntentId + ": " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(String transactionId, BigDecimal amountToRefund) {
        // This `transactionId` is OUR internal system's transaction ID.
        if (!stripeInitialized) {
            logger.error("Stripe client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Stripe service is not available. Please check configuration.");
        }
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentProcessingException("Payment not found for refund with internal transaction ID: " + transactionId));

        String stripePaymentIntentId = payment.getGatewayTransactionId();
        if (stripePaymentIntentId == null || stripePaymentIntentId.isEmpty()) {
            throw new PaymentProcessingException("Cannot refund: Stripe PaymentIntent ID is missing for " + transactionId);
        }

        // Stripe refunds are against a Charge ID or PaymentIntent ID.
        // If status is not "succeeded", it might not be refundable.
        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) { // Our internal status
             // We could also check Stripe's status directly if needed: getPaymentStatus(transactionId); payment = ...
             throw new PaymentProcessingException("Cannot refund: Payment " + transactionId + " (Stripe PI: "+stripePaymentIntentId+") is not in a refundable state (current status: " + payment.getStatus() + ")");
        }

        try {
            RefundCreateParams.Builder refundParamsBuilder = RefundCreateParams.builder()
                .setPaymentIntent(stripePaymentIntentId);

            if (amountToRefund != null && amountToRefund.compareTo(BigDecimal.ZERO) > 0 && amountToRefund.compareTo(payment.getAmount()) <= 0) {
                refundParamsBuilder.setAmount(amountToRefund.multiply(new BigDecimal(100)).longValue());
            } // If amountToRefund is null or zero, Stripe will attempt a full refund.

            Refund refund = Refund.create(refundParamsBuilder.build());
            String refundId = refund.getId();
            String refundStatus = refund.getStatus(); // e.g., succeeded, pending, failed, canceled
            logger.info("Stripe refund initiated for PaymentIntent {}. Refund ID: {}, Status: {}", stripePaymentIntentId, refundId, refundStatus);

            // Update our payment status
            if ("succeeded".equalsIgnoreCase(refundStatus) || "pending".equalsIgnoreCase(refundStatus)) {
                // Check if it's a partial refund
                if (amountToRefund != null && payment.getAmount().compareTo(amountToRefund) > 0 && "succeeded".equalsIgnoreCase(refundStatus)) {
                    payment.setStatus("PARTIALLY_REFUNDED");
                } else {
                    payment.setStatus("REFUNDED");
                }
            } else {
                payment.setStatus("REFUND_FAILED");
            }
            paymentRepository.save(payment);

            PaymentResponse response = createPaymentResponse(payment, "Refund request processed by Stripe. Current refund status: " + refundStatus, null);
            response.setGatewaySpecificResponse(refund.toJson()); // Convert Stripe object to map/json string
            return response;

        } catch (StripeException e) {
            logger.error("Stripe API error during refund for PaymentIntent {}: {}", stripePaymentIntentId, e.getMessage(), e);
            payment.setStatus("REFUND_FAILED");
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Stripe refund failed for " + stripePaymentIntentId + ": " + e.getMessage(), e);
        }
    }

    private PaymentResponse createPaymentResponse(Payment payment, String message, String clientSecret) {
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(payment.getTransactionId());
        response.setGatewayTransactionId(payment.getGatewayTransactionId()); // Stripe PaymentIntent ID
        response.setStatus(payment.getStatus());
        response.setMessage(message);
        response.setGatewayName(getGatewayName());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setTimestamp(payment.getUpdatedAt());

        Map<String, Object> specificDetails = new HashMap<>();
        specificDetails.put("stripe_payment_intent_id", payment.getGatewayTransactionId());
        if (clientSecret != null && !clientSecret.isEmpty()) {
            specificDetails.put("stripe_client_secret", clientSecret);
        }
        response.setGatewaySpecificResponse(specificDetails);
        return response;
    }

    private String mapStripePaymentIntentStatus(String stripeStatus) {
        if (stripeStatus == null) return "UNKNOWN";
        switch (stripeStatus.toLowerCase()) {
            case "requires_payment_method":
            case "requires_confirmation":
            case "requires_action":
            case "processing":
                return "PENDING"; // Or more specific if needed, e.g. PENDING_USER_ACTION
            case "succeeded":
                return "SUCCESS";
            case "canceled":
                return "CANCELED";
            case "requires_capture": // Relevant if using manual capture
                return "AUTHORIZED";
            default:
                logger.warn("Unknown Stripe PaymentIntent status: {}", stripeStatus);
                return stripeStatus.toUpperCase(); // Or FAILED as a fallback
        }
    }

    @Override
    public String getGatewayName() {
        return "stripe";
    }
}
