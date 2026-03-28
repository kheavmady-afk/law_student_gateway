# Spring Cloud API Gateway

This project serves as the **API Gateway** for a microservices architecture. It is built using **Spring Boot 3.4.1** and **Spring Cloud Gateway**.

## Architecture Overview

The API Gateway acts as the single entry point for all client requests. Its main responsibilities are:
1. **Routing**: Forwarding requests to the appropriate microservice.
2. **Authentication**: Verifying the presence of security tokens before allowing requests to reach business services.

### Logic Flow
`Client Request` -> `API Gateway` -> `Auth Service (Validation)` -> `Business API Services`

---

## Configuration

The gateway is configured in `src/main/resources/application.yaml`.

### Routes Defined:
1. **Auth Service (`/auth/**`)**:
   - Port: `8081`
   - Purpose: Handles login, registration, and token generation.
   - Security: Public access.

2. **Business Services (`/api/v1/**`)**:
   - Port: `8082`
   - Purpose: Main application logic.
   - Security: Protected by `AuthenticationFilter`.

---

## Security Implementation

### Authentication Filter
A custom `AuthenticationFilter` is implemented in `com.kheavmady.gateway.filter.AuthenticationFilter`.

**Current Implementation:**
- Extracts the `Authorization` header.
- Checks for a `Bearer ` prefix.
- Rejects requests with `401 Unauthorized` if the header is missing or invalid.

**Future Integration:**
In the filter's logic, you can integrate a `WebClient` call to your dedicated Auth Service to validate JWT tokens.

```java
// Example future logic in AuthenticationFilter:
// boolean isValid = authClient.validate(token);
// if (!isValid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
```

---

## Tech Stack
- **Java 21**
- **Spring Boot 3.4.1**
- **Spring Cloud Gateway 2024.0.0**
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

### Testing Routes
- **Auth**: `GET http://localhost:8080/auth/login` (Routes to `localhost:8081/auth/login`)
- **API**: `GET http://localhost:8080/api/v1/resource` (Requires `Authorization: Bearer <token>` header)
