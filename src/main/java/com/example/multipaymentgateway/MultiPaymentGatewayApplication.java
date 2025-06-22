package com.example.multipaymentgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("com.example.multipaymentgateway.repository")
@EntityScan("com.example.multipaymentgateway.model")
public class MultiPaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiPaymentGatewayApplication.class, args);
    }

}
