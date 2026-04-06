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

---

## Deployment (Production Ready - Raspberry Pi 4B)

This section describes how to deploy the Gateway to a **Raspberry Pi 4B** running **Ubuntu Server 24.04** using **Docker Compose**. To save time and space on the Pi, we build the JAR locally on your development machine and transfer only the required files.

### 1. Prerequisites (On Raspberry Pi)
Ensure Docker and Docker Compose are installed. If `curl` is missing, install it first:
```bash
# Update and install curl
sudo apt update && sudo apt install curl -y

# Install Docker (Includes Compose Plugin)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Verify installation (This should now work)
docker compose version

# Add your user to the docker group (Re-log after this)
sudo usermod -aG docker $USER

# Fix permissions if needed
sudo chown -R $USER:$USER ~/apps
```

### 2. Build the JAR Locally
On your **Development Machine** (PC/Mac), navigate to the `gateway/` directory and run:
```bash
./gradlew bootJar
```
This will create an executable JAR in `build/libs/gateway-0.0.1-SNAPSHOT.jar`.

### 3. Transfer Files to the Pi
Replace `<PI_IP>` with your Pi's actual IP address (e.g., `192.168.0.179`) and `<USER>` with your username (e.g., `kheavmady`). Run these from your **Development Machine**:
```bash
# Create the deployment directory on the Pi
ssh kheavmady@192.168.0.179 "mkdir -p ~/apps/lawstudent/gateway/"

# Transfer the JAR file
scp build/libs/*.jar kheavmady@192.168.0.179:~/apps/lawstudent/gateway/

# Transfer the Docker configuration files
scp Dockerfile kheavmady@192.168.0.179:~/apps/lawstudent/gateway/
scp docker-compose.yml kheavmady@192.168.0.179:~/apps/lawstudent/gateway/
```

### 4. Configure and Start on the Pi
SSH into your Raspberry Pi and start the service:
```bash
ssh kheavmady@192.168.0.179
cd ~/apps/lawstudent/gateway/

# Important: Update the environment variables in docker-compose.yml 
# with the correct physical IPs of your Auth and User services.
nano docker-compose.yml

# Start the Gateway container
docker compose up -d --build
```

### 5. Monitoring
To view the logs or check the container status:
```bash
docker ps
docker logs -f gateway
```

**Note:** Since this is a production-ready setup, the Gateway communicates with other services via their **Physical IP addresses**. Ensure the `AUTH_SERVICE_URL` and `USER_SERVICE_URL` in `docker-compose.yml` match your actual Pi cluster configuration.

---

## 🔍 Troubleshooting & Microservices Debugging (The "Mady" Protocol)

If you encounter **500 Internal Server Error** or **401 Unauthorized** when accessing services through the Gateway, follow this debugging order:

### 1. Check Gateway Filter Logs (Pi 179)
The Gateway is the "brain" of the network. If it can't authenticate, everything fails.
```bash
docker logs gateway --tail 100 | grep "AuthFilter"
```
*   **Success:** `[AuthFilter] Processing request: GET /api/v1/user/profile` followed by a successful call to the Auth Service.
*   **The 404 URL Trap:** Look for `404 Not Found from GET http://192.168.0.179:8081/validate`. This means your `AUTH_VALIDATION_URL` is missing the `/auth` prefix. Correct URL: `http://192.168.0.179:8081/auth/validate`.
*   **The Filter Name Trap:** If you see NO logs starting with `[AuthFilter]`, the Gateway isn't triggering the filter. Check `application.yaml` to ensure the filter is named `- name: AuthFilter`.

### 2. Painful Lessons Learned (Zero-Trust Security)
*   **Variable Collision:** Never name your internal validation URL `AUTH_SERVICE_URL` if that variable is already used to define the Gateway route. Use a unique name like `AUTH_VALIDATION_URL`.
*   **Localhost is a Trap:** Inside a Docker container, `localhost` refers to the container itself, NOT the Raspberry Pi. Always use the Pi's physical IP (`192.168.0.179`) for inter-service communication.
*   **JWT Secret Sync:** The `JWT_SECRET` must be exactly the same across all services. A single character mismatch will cause a 401 error.

