# Multi-Payment Gateway Microservice

This project is a Spring Boot microservice designed to integrate with multiple payment gateways (e.g., Stripe, Razorpay). It provides a unified API for processing payments, checking transaction statuses, and handling refunds.

## Features

*   **Multiple Gateway Support**: Easily extendable to support various payment providers.
*   **Common Payment API**: A single set of endpoints for all payment operations.
*   **Configuration-driven**: Gateway credentials and preferences managed via `application.properties`.
*   **JPA for Persistence**: Uses Spring Data JPA to store payment transaction details (with H2 for local development).
*   **Basic Error Handling**: Global exception handling for common errors.
*   **DTOs for API Contracts**: Clear data structures for requests and responses.
*   **Actuator Endpoints**: Includes standard Spring Boot Actuator endpoints (health, info, metrics).

## Prerequisites

*   Java 17 or later
*   Maven 3.6.x or later

## Getting Started

### Configuration

1.  **Clone the repository:**
    ```bash
    # If you haven't already:
    # git clone <repository-url>
    # cd multi-payment-gateway
    ```
2.  **Configure Payment Gateways:**
    Open `multi-payment-gateway/src/main/resources/application.properties`.
    The application is configured to read API keys from environment variables. You will need to set these variables in your environment before running the application.

    For example, you might set them in your shell profile (e.g., `.bashrc`, `.zshrc`) or use a `.env` file with a tool like `dotenv` if you are running locally.

    **Required Environment Variables:**

    *   `PAYMENT_GATEWAY_API_KEY`: Your API key for the payment gateway.
    *   `PAYMENT_GATEWAY_SECRET_KEY`: Your secret key for the payment gateway.

    The `application.properties` file has placeholders like:
    ```properties
    payment.gateway.apiKey=${PAYMENT_GATEWAY_API_KEY:YOUR_API_KEY_HERE}
    payment.gateway.secretKey=${PAYMENT_GATEWAY_SECRET_KEY:YOUR_SECRET_KEY_HERE}
    ```
    The part after the colon (e.g., `YOUR_API_KEY_HERE`) is a default value that will be used if the environment variable is not set. **It is strongly recommended to set these via environment variables and not rely on these default values, especially in production.**

    The existing gateway-specific keys like `razorpay.api.key` or `stripe.api.key` in `application.properties` should be removed or updated to also use environment variables if you plan to use multiple distinct configurations. For this initial setup, we've added generic keys.

### Building the Application

Use Maven to build the project:

```bash
./mvnw clean package
```
(On Windows, use `mvnw.cmd clean package`)

### Running the Application

You can run the application using Maven:

```bash
./mvnw spring-boot:run
```
The application will start on the default port (usually 8080).

### H2 Console

For local development, an in-memory H2 database is used. You can access its console at:
`http://localhost:8080/h2-console`

**JDBC URL:** `jdbc:h2:mem:paymentdb`
**User Name:** `sa`
**Password:** (leave blank)

## API Endpoints

The main API endpoints are under `/api/payments`:

*   **Process Payment:**
    *   `POST /api/payments`
    *   **Request Body** (`application/json`):
        ```json
        {
          "amount": 100.00,
          "currency": "USD",
          "paymentMethod": "card",
          "customerEmail": "customer@example.com",
          "preferredGateway": "stripe", // Optional: "stripe" or "razorpay"
          // "paymentDetails": { "token": "tok_visa" } // Optional: Gateway-specific details
        }
        ```
    *   **Response Body** (`PaymentResponse`):
        ```json
        {
          "transactionId": "some-unique-id", // System-generated or gateway-provided
          "gatewayTransactionId": "gateway-specific-id", // From the gateway
          "status": "SUCCESS", // or PENDING, FAILED
          "message": "Payment processed successfully.",
          "gatewayName": "stripe",
          "amount": 100.00,
          "currency": "USD",
          "timestamp": "2023-10-27T10:30:00"
          // "redirectUrl": "if-any-for-3ds"
        }
        ```

*   **Get Payment Status:**
    *   `GET /api/payments/{transactionId}/status?gatewayName=<gateway>`
    *   Example: `GET /api/payments/some-unique-id/status?gatewayName=stripe`
    *   **Response Body** (`PaymentResponse`)

*   **Refund Payment:**
    *   `POST /api/payments/{transactionId}/refund?gatewayName=<gateway>&amount=<optional_amount>`
    *   Example: `POST /api/payments/some-unique-id/refund?gatewayName=stripe&amount=50.00`
    *   **Response Body** (`PaymentResponse`)

## Project Structure

*   `src/main/java/com/example/multipaymentgateway/`: Main application code
    *   `controller/`: REST API controllers.
    *   `dto/`: Data Transfer Objects for API requests/responses.
    *   `exception/`: Custom exceptions and global exception handler.
    *   `model/`: JPA entities.
    *   `repository/`: Spring Data JPA repositories.
    *   `service/`: Business logic, including payment gateway implementations.
*   `src/main/resources/`: Configuration files.
    *   `application.properties`: Main application configuration.
*   `src/test/`: Unit tests.

## Further Development (TODOs)

*   Implement actual API calls to Stripe, Razorpay, and other gateways (currently stubbed).
*   Secure API keys and sensitive configuration (e.g., using Spring Cloud Config, HashiCorp Vault, or environment variables).
*   Implement webhook handling for asynchronous payment updates from gateways.
*   Enhance the default gateway selection strategy in `PaymentController` (e.g., based on configuration, round-robin, or lowest cost).
*   Add more comprehensive validation rules and error handling scenarios.
*   Implement robust logging (e.g., structured logging) and consider distributed tracing for microservice architecture.
*   Add integration tests covering the database and interactions between components.
*   For production environments, switch from H2 to a persistent database and use a migration tool (e.g., Flyway, Liquibase).
*   Implement security measures: authentication (e.g., OAuth2/OIDC) and authorization for API endpoints.
*   Add detailed Javadoc comments to public APIs and complex logic.
*   Containerize the application using Docker for easier deployment.
*   Set up a CI/CD pipeline.

---
_This project was initialized using Spring Initializr with basic dependencies._
