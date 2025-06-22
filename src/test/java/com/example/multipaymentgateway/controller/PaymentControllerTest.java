package com.example.multipaymentgateway.controller;

import com.example.multipaymentgateway.dto.PaymentRequest;
import com.example.multipaymentgateway.dto.PaymentResponse;
import com.example.multipaymentgateway.service.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentGateway mockStripeService; // Mock one specific gateway

    @Mock
    private PaymentGateway mockRazorpayService; // Mock another specific gateway

    @InjectMocks
    private PaymentController paymentController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Manually create the map of gateways for the controller
        Map<String, PaymentGateway> paymentGatewaysMap = new HashMap<>();
        paymentGatewaysMap.put("stripeService", mockStripeService);
        paymentGatewaysMap.put("razorpayService", mockRazorpayService);

        paymentController = new PaymentController(paymentGatewaysMap);
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();

        // Common mock behavior for getGatewayName()
        when(mockStripeService.getGatewayName()).thenReturn("stripe");
        when(mockRazorpayService.getGatewayName()).thenReturn("razorpay");
    }

    @Test
    void processPayment_stripePreferred_shouldSucceed() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setPaymentMethod("card");
        request.setCustomerEmail("test@example.com");
        request.setPreferredGateway("stripe"); // Preferred gateway

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setTransactionId(UUID.randomUUID().toString());
        mockResponse.setStatus("SUCCESS");
        mockResponse.setGatewayName("stripe");

        when(mockStripeService.processPayment(any(PaymentRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.gatewayName").value("stripe"))
                .andExpect(jsonPath("$.transactionId").exists());
    }

    @Test
    void processPayment_noPreferredGateway_shouldUseDefault() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("INR");
        request.setPaymentMethod("upi");
        request.setCustomerEmail("default@example.com");
        // No preferred gateway, controller will pick one (e.g., the first one, stripeService in this setup)

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setTransactionId(UUID.randomUUID().toString());
        mockResponse.setStatus("SUCCESS");
        // Assuming stripeService is picked by default implementation in controller's selectGateway
        mockResponse.setGatewayName("stripe");


        // If Stripe is the default (first in map usually)
        when(mockStripeService.processPayment(any(PaymentRequest.class))).thenReturn(mockResponse);


        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                // This depends on the default selection logic in PaymentController.
                // For this test, we assume Stripe is selected if no preference.
                .andExpect(jsonPath("$.gatewayName").value("stripe"))
                .andExpect(jsonPath("$.transactionId").exists());
    }

    @Test
    void processPayment_invalidGateway_shouldReturnBadRequest() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setCurrency("EUR");
        request.setPaymentMethod("card");
        request.setCustomerEmail("error@example.com");
        request.setPreferredGateway("unknownGateway");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid or unsupported payment gateway specified."));
    }


    @Test
    void getPaymentStatus_shouldSucceed() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String gatewayName = "stripe";

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setTransactionId(transactionId);
        mockResponse.setStatus("SUCCESS");
        mockResponse.setGatewayName(gatewayName);

        when(mockStripeService.getPaymentStatus(anyString())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/payments/{transactionId}/status", transactionId)
                        .param("gatewayName", gatewayName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.gatewayName").value(gatewayName))
                .andExpect(jsonPath("$.transactionId").value(transactionId));
    }

    @Test
    void getPaymentStatus_invalidGateway_shouldReturnBadRequest() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String gatewayName = "nonExistentGateway";

        mockMvc.perform(get("/api/payments/{transactionId}/status", transactionId)
                        .param("gatewayName", gatewayName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid or unsupported payment gateway specified for status check."));
    }

    @Test
    void refundPayment_shouldSucceed() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String gatewayName = "razorpay"; // Test with a different gateway
        BigDecimal refundAmount = new BigDecimal("25.00");

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setTransactionId(transactionId);
        mockResponse.setStatus("REFUND_SUCCESS");
        mockResponse.setGatewayName(gatewayName);

        when(mockRazorpayService.refundPayment(anyString(), any(BigDecimal.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/payments/{transactionId}/refund", transactionId)
                        .param("gatewayName", gatewayName)
                        .param("amount", refundAmount.toPlainString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_SUCCESS"))
                .andExpect(jsonPath("$.gatewayName").value(gatewayName))
                .andExpect(jsonPath("$.transactionId").value(transactionId));
    }
}
