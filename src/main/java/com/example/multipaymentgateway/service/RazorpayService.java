package com.example.multipaymentgateway.service;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;
import com.example.multipaymentgateway.exception.PaymentProcessingException;
import com.example.multipaymentgateway.model.Payment;
import com.example.multipaymentgateway.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service("razorpayService")
public class RazorpayService implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(RazorpayService.class);

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpayKeySecret;

    private RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;

    public RazorpayService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void init() {
        try {
            // Ensure keys are not null or empty before initializing
            if (razorpayKeyId == null || razorpayKeyId.isEmpty() || razorpayKeyId.equals("YOUR_RAZORPAY_KEY_ID") ||
                razorpayKeySecret == null || razorpayKeySecret.isEmpty() || razorpayKeySecret.equals("YOUR_RAZORPAY_KEY_SECRET")) {
                logger.warn("Razorpay API Key ID or Secret is not configured or using default placeholders. Razorpay client will not be initialized.");
                return; // Do not initialize if keys are placeholders or missing
            }
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            logger.info("Razorpay client initialized successfully.");
        } catch (RazorpayException e) {
            logger.error("Error initializing Razorpay client", e);
            // Application can continue running, but Razorpay transactions will fail.
            // Or, throw new RuntimeException("Failed to initialize Razorpay client", e); to halt startup.
        }
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        if (razorpayClient == null) {
            logger.error("Razorpay client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Razorpay service is not available. Please check configuration.");
        }

        String internalTransactionId = UUID.randomUUID().toString();
        Payment payment = new Payment();
        payment.setTransactionId(internalTransactionId);
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency(paymentRequest.getCurrency().toUpperCase());
        payment.setPaymentGateway(getGatewayName());
        payment.setStatus("PENDING");
        // @PrePersist will set createdAt and updatedAt
        payment = paymentRepository.save(payment);

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", paymentRequest.getAmount().multiply(new BigDecimal(100)).intValue()); // Amount in paise
            orderRequest.put("currency", paymentRequest.getCurrency().toUpperCase());
            orderRequest.put("receipt", internalTransactionId);

            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");
            logger.info("Razorpay Order created: {} for internal transaction ID: {}", razorpayOrderId, internalTransactionId);

            // SIMULATION: In a real scenario, client-side handles payment using this order_id.
            // Then Razorpay sends a webhook or client confirms, providing a `razorpay_payment_id`.
            // For this backend-only example, we'll simulate capture and use a placeholder payment_id.
            // This step is crucial: The `gatewayTransactionId` should be the `razorpay_payment_id`.
            // The `order_id` is just for initiating payment.
            // If we only have order_id, we can't directly fetch payment status or refund without payment_id.

            // Let's assume for now the order_id is what we store as gatewayTransactionId if no payment_id is obtained server-side.
            // This is a simplification. Ideally, a webhook updates with actual payment_id.
            payment.setGatewayTransactionId(razorpayOrderId); // Storing Order ID for now.
            payment.setStatus("AUTHORIZED"); // Status after order creation, actual payment not yet captured by this backend call.
                                          // Real status would be PENDING or AWAITING_USER_ACTION.
                                          // Or if it's a direct server-to-server, it might be different.
                                          // For Razorpay, 'created' is order status, payment happens after.
            paymentRepository.save(payment);

            PaymentResponse response = createPaymentResponse(payment, "Razorpay order created. Client must complete payment.", null);
            response.setRedirectUrl(null); // No redirect URL in this flow yet, but could be if using specific methods
            // Add razorpay_order_id to gatewaySpecificResponse for client
            JSONObject specificDetails = new JSONObject();
            specificDetails.put("razorpay_order_id", razorpayOrderId);
            response.setGatewaySpecificResponse(specificDetails.toMap());
            response.setStatus("PENDING_USER_ACTION"); // Custom status indicating client needs to act
            response.setMessage("Razorpay order created successfully. Please complete the payment using the order_id: " + razorpayOrderId);


            return response;

        } catch (RazorpayException e) {
            logger.error("Razorpay API error during payment processing for transactionId {}: {}", internalTransactionId, e.getMessage(), e);
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Razorpay payment failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during Razorpay payment processing for transactionId {}: {}", internalTransactionId, e.getMessage(), e);
            payment.setStatus("ERROR");
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Unexpected error during Razorpay payment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse getPaymentStatus(String transactionId) {
        // This `transactionId` is OUR internal system's transaction ID.
        if (razorpayClient == null) {
            logger.error("Razorpay client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Razorpay service is not available. Please check configuration.");
        }
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentProcessingException("Payment not found with internal transaction ID: " + transactionId));

        // To get actual payment status from Razorpay, we need either:
        // 1. The Razorpay Payment ID (`razorpay_payment_id`) - preferred.
        // 2. The Razorpay Order ID (`razorpay_order_id`) - can fetch payments for an order.

        // Current `payment.getGatewayTransactionId()` stores the Order ID from processPayment.
        String razorpayOrderId = payment.getGatewayTransactionId();
        if (razorpayOrderId == null || razorpayOrderId.isEmpty()) {
            logger.warn("Razorpay Order ID is missing for internal transaction ID: {}. Cannot query Razorpay.", transactionId);
            return createPaymentResponse(payment, "Cannot fetch status from Razorpay: Gateway Order ID is missing.", null);
        }

        try {
            // Fetch all payments for the order. There could be multiple attempts.
            // We'd typically look for a 'captured' one.
            java.util.List<com.razorpay.Payment> paymentsForOrder = razorpayClient.orders.fetchPayments(razorpayOrderId);

            if (paymentsForOrder.isEmpty()) {
                // No payment attempts yet or none successful. Order status might be 'created' or 'attempted'.
                // Fetch order status itself
                Order order = razorpayClient.orders.fetch(razorpayOrderId);
                String orderStatus = order.get("status"); // e.g., created, attempted, paid
                payment.setStatus(mapRazorpayOrderStatusToInternalStatus(orderStatus, null));
                paymentRepository.save(payment);
                return createPaymentResponse(payment, "Razorpay order status: " + orderStatus + ". No successful payment captured yet.", razorpayOrderId);
            }

            // Find a successful payment (captured or authorized)
            com.razorpay.Payment successfulPayment = null;
            for (com.razorpay.Payment rzpPayment : paymentsForOrder) {
                String paymentStatus = rzpPayment.get("status");
                if ("captured".equalsIgnoreCase(paymentStatus)) {
                    successfulPayment = rzpPayment;
                    break;
                }
                if (successfulPayment == null && "authorized".equalsIgnoreCase(paymentStatus)) {
                    successfulPayment = rzpPayment; // Prefer captured, but authorized is also a form of success
                }
            }

            if (successfulPayment != null) {
                String rzpPaymentId = successfulPayment.get("id");
                String rzpPaymentStatus = successfulPayment.get("status");
                logger.info("Found Razorpay payment {} with status {} for order {}", rzpPaymentId, rzpPaymentStatus, razorpayOrderId);
                payment.setGatewayTransactionId(rzpPaymentId); // Update to actual payment_id
                payment.setStatus(mapRazorpayOrderStatusToInternalStatus(null, rzpPaymentStatus));
                paymentRepository.save(payment);
                return createPaymentResponse(payment, "Payment status retrieved successfully from Razorpay: " + rzpPaymentStatus, razorpayOrderId);
            } else {
                // No successful payment, update status based on the latest attempt or order status
                Order order = razorpayClient.orders.fetch(razorpayOrderId); // Re-fetch order for latest overall status
                String orderStatus = order.get("status");
                logger.info("No successful (captured/authorized) payment found for order {}. Order status: {}", razorpayOrderId, orderStatus);
                payment.setStatus(mapRazorpayOrderStatusToInternalStatus(orderStatus, null));
                paymentRepository.save(payment);
                return createPaymentResponse(payment, "No successful payment captured for order. Order status: " + orderStatus, razorpayOrderId);
            }

        } catch (RazorpayException e) {
            logger.error("Razorpay API error fetching status for order {}: {}", razorpayOrderId, e.getMessage(), e);
            // Don't change local status based on a fetch failure, just report error
            throw new PaymentProcessingException("Razorpay status fetch failed for order " + razorpayOrderId + ": " + e.getMessage(), e);
        }
    }


    @Override
    @Transactional
    public PaymentResponse refundPayment(String transactionId, BigDecimal amountToRefund) {
        // This `transactionId` is OUR internal system's transaction ID.
         if (razorpayClient == null) {
            logger.error("Razorpay client not initialized. Check API key configuration.");
            throw new PaymentProcessingException("Razorpay service is not available. Please check configuration.");
        }
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentProcessingException("Payment not found for refund with internal transaction ID: " + transactionId));

        // We need the Razorpay Payment ID (not Order ID) to process a refund.
        // The getPaymentStatus method should have updated gatewayTransactionId to be the payment_id if successful.
        String razorpayPaymentId = payment.getGatewayTransactionId();

        if (razorpayPaymentId == null || razorpayPaymentId.startsWith("order_")) {
            // If it's still an order_id, or null, we can't refund.
            // Attempt to fetch payment status first to get the payment_id.
            logger.warn("Attempting to fetch payment status to get Razorpay Payment ID before refund for internal transaction ID: {}", transactionId);
            getPaymentStatus(transactionId); // This will attempt to update payment.gatewayTransactionId
            payment = paymentRepository.findByTransactionId(transactionId).get(); // Re-fetch
            razorpayPaymentId = payment.getGatewayTransactionId();
            if (razorpayPaymentId == null || razorpayPaymentId.startsWith("order_")) {
                 throw new PaymentProcessingException("Cannot refund: Razorpay Payment ID not found for " + transactionId + ". Please check payment status first.");
            }
        }

        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus()) && !"CAPTURED".equalsIgnoreCase(payment.getStatus())) { // CAPTURED is Razorpay's term for successful
             throw new PaymentProcessingException("Cannot refund: Payment " + transactionId + " (Razorpay ID: "+razorpayPaymentId+") is not in a refundable state (current status: " + payment.getStatus() + ")");
        }

        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountToRefund.multiply(new BigDecimal(100)).intValue()); // Amount in paise
            // Optional: notes, speed, receipt for refund
            // refundRequest.put("speed", "normal"); // "normal" or "optimum"

            Refund refund = razorpayClient.payments.refund(razorpayPaymentId, refundRequest);
            String refundId = refund.get("id");
            String refundStatus = refund.get("status"); // e.g., pending, processed
            logger.info("Razorpay refund initiated for payment {}. Refund ID: {}, Status: {}", razorpayPaymentId, refundId, refundStatus);

            // Update our payment status based on refund.
            // If a refund is "processed" or "pending" (accepted by Razorpay), we mark as REFUNDED.
            // A more granular system might track partial refunds or use a status like "PARTIALLY_REFUNDED".
            if ("processed".equalsIgnoreCase(refundStatus) || "pending".equalsIgnoreCase(refundStatus)) {
                payment.setStatus("REFUNDED"); // Or "PARTIALLY_REFUNDED" if you track amounts
            } else {
                // if refund fails immediately, this path might be taken.
                // However, usually it goes to pending then processed or failed via webhooks.
                payment.setStatus("REFUND_FAILED");
            }
            paymentRepository.save(payment);

            PaymentResponse response = createPaymentResponse(payment, "Refund request processed by Razorpay. Current refund status: " + refundStatus, null); // No order_id needed here typically
            response.setGatewaySpecificResponse(refund.toJson().toMap());
            return response;

        } catch (RazorpayException e) {
            logger.error("Razorpay API error during refund for payment {}: {}", razorpayPaymentId, e.getMessage(), e);
            payment.setStatus("REFUND_FAILED"); // Keep our internal status reflective
            paymentRepository.save(payment);
            throw new PaymentProcessingException("Razorpay refund failed for payment " + razorpayPaymentId + ": " + e.getMessage(), e);
        }
    }

    private PaymentResponse createPaymentResponse(Payment payment, String message, String razorpayOrderId) {
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(payment.getTransactionId()); // Our internal ID
        response.setGatewayTransactionId(payment.getGatewayTransactionId()); // Razorpay Payment ID or Order ID
        response.setStatus(payment.getStatus());
        response.setMessage(message);
        response.setGatewayName(getGatewayName());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setTimestamp(payment.getUpdatedAt());

        JSONObject specificDetails = new JSONObject();
        if (razorpayOrderId != null) {
            specificDetails.put("razorpay_order_id", razorpayOrderId);
        }
        // If gatewayTransactionId is the payment_id, include it clearly
        if (payment.getGatewayTransactionId() != null && payment.getGatewayTransactionId().startsWith("pay_")) {
            specificDetails.put("razorpay_payment_id", payment.getGatewayTransactionId());
        }
        if (!specificDetails.isEmpty()) {
            response.setGatewaySpecificResponse(specificDetails.toMap());
        }
        return response;
    }

    // Maps Razorpay's order status or payment status to our internal system status
    private String mapRazorpayOrderStatusToInternalStatus(String orderStatus, String paymentStatus) {
        if (paymentStatus != null) { // Payment status takes precedence
            switch (paymentStatus.toLowerCase()) {
                case "created": return "PENDING"; // Payment link created, user hasn't acted
                case "authorized": return "AUTHORIZED";
                case "captured": return "SUCCESS";
                case "failed": return "FAILED";
                case "refunded": return "REFUNDED"; // This is for full refund on payment object
                default: return paymentStatus.toUpperCase();
            }
        }
        if (orderStatus != null) { // Fallback to order status
            switch (orderStatus.toLowerCase()) {
                case "created": return "PENDING_USER_ACTION"; // Order created, waiting for user
                case "attempted": return "PENDING"; // User attempted, might succeed or fail
                case "paid": return "SUCCESS"; // Order is marked paid (all payments captured)
                default: return orderStatus.toUpperCase();
            }
        }
        return "UNKNOWN";
    }


    @Override
    public String getGatewayName() {
        return "razorpay";
    }
}
