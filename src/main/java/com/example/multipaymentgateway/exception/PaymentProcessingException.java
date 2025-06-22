package com.example.multipaymentgateway.exception;

public class PaymentProcessingException extends RuntimeException {
    private final String transactionId;
    private final String gatewayName;

    public PaymentProcessingException(String message, String transactionId, String gatewayName) {
        super(message);
        this.transactionId = transactionId;
        this.gatewayName = gatewayName;
    }

    public PaymentProcessingException(String message, String transactionId, String gatewayName, Throwable cause) {
        super(message, cause);
        this.transactionId = transactionId;
        this.gatewayName = gatewayName;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getGatewayName() {
        return gatewayName;
    }
}
