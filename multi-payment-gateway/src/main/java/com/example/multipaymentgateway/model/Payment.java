package com.example.multipaymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Internal primary key

    @Column(nullable = false, unique = true)
    private String transactionId; // Our system's unique transaction ID, could be a UUID

    @Column(unique = true)
    private String gatewayTransactionId; // Transaction ID from the payment gateway

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3) // ISO 4217 currency code (e.g., USD, INR)
    private String currency;

    @Column(nullable = false, length = 50)
    private String status; // e.g., PENDING, SUCCESS, FAILED, REFUND_INITIATED, REFUNDED

    @Column(nullable = false, length = 50)
    private String paymentGateway; // Name of the gateway used (e.g., "stripe", "razorpay")

    @Column(length = 100)
    private String paymentMethod; // e.g., "card", "upi"

    @Column(length = 255)
    private String customerEmail;

    @Column(length = 255)
    private String orderId; // Client's order identifier

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String gatewayResponsePayload; // Store raw response from gateway for auditing/debugging

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
