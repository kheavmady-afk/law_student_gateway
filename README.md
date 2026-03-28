# Spring Cloud API Gateway

This project serves as the **API Gateway** (The Shield) for a microservices architecture. It is built using **Spring Boot 3.4.1** and **Spring Cloud Gateway**.

## Architecture Overview

The API Gateway acts as the single entry point and security layer for all client requests. 

### Logic Flow
1. **Client Request** hits Gateway (`:8080`).
2. **Route Validation**: Gateway checks if the path is Public or Private.
3. **Authentication (Private only)**: Gateway calls **Auth Service** (`:8081/auth/validate`) to verify the JWT.
4. **Passport Injection**: Gateway injects a secret `X-Gateway-Secret` into the request header.
5. **Routing**: Request is forwarded to the target **Business Service** (`:8082+`).

---

## Configuration

The gateway is configured in `src/main/resources/application.yaml`.

### Routes Defined:
1. **Auth Service (`/auth/**`)**:
   - Port: `8081`
   - Purpose: Handles login, registration, and token generation.
2. **User/Business Services (`/api/v1/**`)**:
   - Port: `8082`
   - Purpose: Main application logic and user management.
   - Security: Protected by `AuthenticationFilter`.

---

## Security Hardening (Production Ready)

### 1. Route Validator
Defined in `com.kheavmady.gateway.config.RouteValidator`.
- **Public Endpoints** (No Token Required):
    - `/api/v1/user/register`
    - `/api/v1/user/login`
    - `/auth/validate`
- **Private Endpoints**: All other paths require a valid Bearer Token.

### 2. JWT Introspection
The `AuthenticationFilter` does not just check for a header; it performs a reactive call to the Auth Service to ensure the token is valid and not expired.

### 3. Gateway Passport (Internal Security)
Every request forwarded by the Gateway is injected with a "Passport":
- **Header**: `X-Gateway-Secret`
- **Value**: `PROD_GATEWAY_SECRET_KEY_12345` (Configured in `application.yaml`)

**Note:** Internal services should check for this header to ensure the request originated from the Gateway and not an external source trying to bypass the shield.

---

## Tech Stack
- **Java 21**
- **Spring Boot 3.4.1**
- **Spring Cloud Gateway 2024.0.0**
- **Spring WebFlux (WebClient)**
- **Gradle**

## Getting Started

### Prerequisites
- JDK 21
- Gradle

### Running the Application
```bash
./gradlew bootRun
```
The gateway will start on port `8080` by default.

### Testing Protected Routes
- **Fail (No Token)**: `curl -I http://localhost:8080/api/v1/user/profile` -> `401 Unauthorized`
- **Pass (With Token)**: Use the provided Postman Collection with a `Bearer <token>`.
