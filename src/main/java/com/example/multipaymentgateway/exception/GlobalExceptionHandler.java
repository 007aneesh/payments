package com.example.multipaymentgateway.exception;

import com.example.multipaymentgateway.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors for @Valid annotated request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        logger.warn("Validation error for request {}: {}", request.getDescription(false), errors);

        PaymentResponse errorResponse = new PaymentResponse();
        errorResponse.setStatus("VALIDATION_ERROR");
        errorResponse.setMessage("Input validation failed. Check details.");
        errorResponse.setTimestamp(LocalDateTime.now());
        errorResponse.setGatewaySpecificResponse(errors); // Put field errors in gatewaySpecificResponse for detail

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles custom payment processing exceptions.
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<PaymentResponse> handlePaymentProcessingException(
            PaymentProcessingException ex, WebRequest request) {
        logger.error("Payment processing error for request {}: {}", request.getDescription(false), ex.getMessage());

        PaymentResponse errorResponse = new PaymentResponse();
        errorResponse.setStatus("PAYMENT_ERROR");
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setTransactionId(ex.getTransactionId());
        errorResponse.setGatewayName(ex.getGatewayName());
        errorResponse.setTimestamp(LocalDateTime.now());

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles resource not found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<PaymentResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found for request {}: {}", request.getDescription(false), ex.getMessage());

        PaymentResponse errorResponse = new PaymentResponse();
        errorResponse.setStatus("NOT_FOUND");
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setTimestamp(LocalDateTime.now());

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    /**
     * Handles any other generic exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        logger.error("Unhandled exception for request {}: {}", request.getDescription(false), ex.getMessage(), ex);

        PaymentResponse errorResponse = new PaymentResponse();
        errorResponse.setStatus("INTERNAL_SERVER_ERROR");
        errorResponse.setMessage("An unexpected error occurred. Please try again later.");
        errorResponse.setTimestamp(LocalDateTime.now());

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
