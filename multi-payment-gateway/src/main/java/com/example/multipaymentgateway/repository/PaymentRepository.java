package com.example.multipaymentgateway.repository;

import com.example.multipaymentgateway.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Finds a payment by its unique transaction ID.
     *
     * @param transactionId The system-generated unique transaction ID.
     * @return An Optional containing the Payment if found, or empty otherwise.
     */
    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Finds a payment by the gateway's transaction ID.
     *
     * @param gatewayTransactionId The transaction ID provided by the payment gateway.
     * @return An Optional containing the Payment if found, or empty otherwise.
     */
    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Finds a payment by the client's order ID.
     * Note: Multiple payments could potentially exist for the same orderId if retries create new transactions.
     * This method returns the first one found or an Optional of a list if multiple are expected.
     * For simplicity, returning Optional<Payment> assuming orderId is unique for successful/pending payments for now.
     *
     * @param orderId The client's order ID.
     * @return An Optional containing the Payment if found, or empty otherwise.
     */
    Optional<Payment> findByOrderId(String orderId);
}
