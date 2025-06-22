package com.example.multipaymentgateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    // Define a DTO for webhook events later
    @PostMapping("/payment-updates")
    public ResponseEntity<String> handlePaymentUpdate(@RequestBody WebhookEvent eventPayload) {
        logger.info("Received payment update webhook: {}", eventPayload);
        // Process the webhook payload here
        // For now, we just acknowledge receipt
        return ResponseEntity.status(HttpStatus.OK).body("Webhook received: " + eventPayload.getEventId());
    }
}
