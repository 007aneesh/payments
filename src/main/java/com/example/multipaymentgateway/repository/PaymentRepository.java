package com.example.multipaymentgateway.repository;

import com.example.multipaymentgateway.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId); // Added for flexibility
}
