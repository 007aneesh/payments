package com.example.multipaymentgateway.controller;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;
import com.example.multipaymentgateway.service.PaymentGateway;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final Map<String, PaymentGateway> paymentGateways;

    // Using a Map to inject all beans that implement PaymentGateway
    // The key will be the bean name (e.g., "stripeService", "razorpayService")
    @Autowired
    public PaymentController(Map<String, PaymentGateway> paymentGateways) {
        this.paymentGateways = paymentGateways;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        PaymentGateway gateway = selectGateway(paymentRequest.getPreferredGateway());
        if (gateway == null) {
            PaymentResponse errorResponse = new PaymentResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("Invalid or unsupported payment gateway specified.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        // Populate response DTO fields that are known at this stage
        PaymentResponse response = gateway.processPayment(paymentRequest);
        response.setAmount(paymentRequest.getAmount());
        response.setCurrency(paymentRequest.getCurrency());
        response.setTimestamp(java.time.LocalDateTime.now());

        if ("SUCCESS".equals(response.getStatus()) || "PENDING".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{transactionId}/status")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String transactionId, @RequestParam String gatewayName) {
        PaymentGateway gateway = selectGateway(gatewayName);
        if (gateway == null) {
            PaymentResponse errorResponse = new PaymentResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("Invalid or unsupported payment gateway specified for status check.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        PaymentResponse response = gateway.getPaymentStatus(transactionId);
        response.setTimestamp(java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable String transactionId,
                                                       @RequestParam String gatewayName,
                                                       @RequestParam(required = false) BigDecimal amount) {
        PaymentGateway gateway = selectGateway(gatewayName);
        if (gateway == null) {
            PaymentResponse errorResponse = new PaymentResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("Invalid or unsupported payment gateway specified for refund.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        PaymentResponse response = gateway.refundPayment(transactionId, amount);
        response.setTimestamp(java.time.LocalDateTime.now());

        if (response.getStatus() != null && response.getStatus().contains("SUCCESS")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private PaymentGateway selectGateway(String preferredGateway) {
        if (preferredGateway != null && !preferredGateway.trim().isEmpty()) {
            return paymentGateways.get(preferredGateway.toLowerCase() + "Service");
        }
        // TODO: Implement a default gateway selection strategy (e.g., from config, round-robin)
        // For now, if no preference, try to pick the first one available or a configured default.
        // This is a naive default, should be improved.
        if (!paymentGateways.isEmpty()) {
            return paymentGateways.values().iterator().next();
        }
        return null;
    }
}
