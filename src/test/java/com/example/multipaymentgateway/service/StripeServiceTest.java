package com.example.multipaymentgateway.service;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class StripeServiceTest {

    private StripeService stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService();
    }

    @Test
    void processPayment_shouldReturnSuccessResponse() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("100.50"));
        request.setCurrency("USD");
        request.setCustomerEmail("test@example.com");
        request.setPaymentMethod("card");

        PaymentResponse response = stripeService.processPayment(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("stripe", response.getGatewayName());
        assertNotNull(response.getTransactionId());
        assertTrue(response.getMessage().contains("Payment processed successfully by Stripe."));
    }

    @Test
    void getPaymentStatus_shouldReturnSuccessResponse() {
        String transactionId = "txn_test_stripe_123";
        PaymentResponse response = stripeService.getPaymentStatus(transactionId);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("stripe", response.getGatewayName());
        assertEquals(transactionId, response.getTransactionId());
        assertTrue(response.getMessage().contains("Payment status retrieved successfully from Stripe."));
    }

    @Test
    void refundPayment_shouldReturnRefundSuccessResponse() {
        String transactionId = "txn_test_stripe_456";
        BigDecimal refundAmount = new BigDecimal("50.25");
        PaymentResponse response = stripeService.refundPayment(transactionId, refundAmount);

        assertNotNull(response);
        assertEquals("REFUND_SUCCESS", response.getStatus());
        assertEquals("stripe", response.getGatewayName());
        assertEquals(transactionId, response.getTransactionId());
        assertTrue(response.getMessage().contains("Refund processed successfully by Stripe."));
    }

    @Test
    void getGatewayName_shouldReturnStripe() {
        assertEquals("stripe", stripeService.getGatewayName());
    }
}
