package com.example.multipaymentgateway.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Data Transfer Object for incoming payment requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    private String currency; // e.g., "USD", "INR"

    @NotBlank(message = "Payment method cannot be blank")
    private String paymentMethod; // e.g., "card", "upi", "netbanking"

    @NotBlank(message = "Customer email cannot be blank")
    private String customerEmail;

    // Optional: For specific gateway selection, otherwise a default or configured gateway might be used.
    private String preferredGateway; // e.g., "stripe", "razorpay"

    // Optional: Additional details specific to the payment method or gateway
    // For example, for card payments: cardNumber, expiryMonth, expiryYear, cvv
    // For UPI: upiId
    private Map<String, Object> paymentDetails;

    // Optional: Order ID from the client's system
    private String orderId;

    // Optional: Description of the payment
    private String description;
}
