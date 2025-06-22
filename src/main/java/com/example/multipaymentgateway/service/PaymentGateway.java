package com.example.multipaymentgateway.service;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;

import java.math.BigDecimal;

/**
 * Interface for payment gateway operations.
 * Each payment gateway implementation (Stripe, Razorpay, etc.) should implement this interface.
 */
public interface PaymentGateway {

    /**
     * Processes a payment request.
     *
     * @param paymentRequest The payment request details.
     * @return A response object containing the status and transaction ID.
     */
    PaymentResponse processPayment(PaymentRequest paymentRequest);

    /**
     * Retrieves the status of a specific payment.
     *
     * @param transactionId The unique identifier of the transaction.
     * @return A response object containing the payment status.
     */
    PaymentResponse getPaymentStatus(String transactionId);

    /**
     * Processes a refund for a specific payment.
     *
     * @param transactionId The unique identifier of the transaction to be refunded.
     * @param amount The amount to refund. If null or less than or equal to zero,
     *               it might imply a full refund depending on gateway capability.
     * @return A response object containing the refund status.
     */
    PaymentResponse refundPayment(String transactionId, BigDecimal amount);

    /**
     * Returns the name of the payment gateway provider.
     * e.g., "stripe", "razorpay"
     * @return The name of the gateway.
     */
    String getGatewayName();
}
