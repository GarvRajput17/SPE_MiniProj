# Calculator REST API — End-to-End CI/CD Pipeline

Production-style Calculator REST API built with Java (Spring Boot) and automated with a complete DevOps CI/CD pipeline using **Git/GitHub**, **Jenkins**, **Docker**, and **Docker Compose**.

---

## 1. Architecture Overview

Every code commit pushed to GitHub triggers an automated Jenkins pipeline that builds, tests, containerizes, pushes to Docker Hub, and performs a zero-downtime deployment with Docker Compose behind an Nginx reverse proxy.

```
+-------------------+      git push       +---------------------+      webhook       +-------------------+
| Developer Machine | ------------------> |  GitHub Repository  | -----------------> | Jenkins CI/CD     |
+-------------------+                     +---------------------+                    +-------------------+
                                                                                               |
                                                                                               v
                                                                                     +-------------------+
                                                                                     | 1. Checkout       |
                                                                                     | 2. Maven Build    |
                                                                                     | 3. JUnit Tests    |
                                                                                     | 4. Docker Build   |
                                                                                     | 5. Docker Push    |
                                                                                     | 6. Compose Deploy |
                                                                                     +-------------------+
                                                                                               |
                                             +-------------------------------------------------+
                                             v                                                 v
                                  +----------------------+                         +----------------------+
                                  | Docker Hub Registry  |                         | Email Notification   |
                                  | (Image Repository)   |                         | (Success / Failure)  |
                                  +----------------------+                         +----------------------+
                                             |
                                             v
                      +-----------------------------------------------+
                      | Docker Compose Multi-Container Network (calc-net)
                      |
                      |          +--------------------------+
                      |          | Nginx Reverse Proxy (80) |
                      |          +--------------------------+
                      |             /                    \
                      |            v                      v
                      |   +------------------+  +------------------+
                      |   | calculator-app-1 |  | calculator-app-2 |
                      |   |   (Port 8081)    |  |   (Port 8081)    |
                      |   +------------------+  +------------------+
                      +-----------------------------------------------+
```

---

## 2. API Endpoints

The service runs internally on port `8081` and is exposed to clients through Nginx on port `80`.

| Operation | HTTP Method | Endpoint | Query Parameters | Description |
|---|---|---|---|---|
| **Add** | `GET` | `/api/v1/add` | `a`, `b` | Returns `a + b` |
| **Subtract** | `GET` | `/api/v1/subtract` | `a`, `b` | Returns `a - b` |
| **Multiply** | `GET` | `/api/v1/multiply` | `a`, `b` | Returns `a * b` |
| **Divide** | `GET` | `/api/v1/divide` | `a`, `b` | Returns `a / b` (b=0 returns 400 error) |
| **Health Check** | `GET` | `/actuator/health` | None | Spring Boot Actuator health status |

### Sample Requests & Responses

#### 1. Add
```bash
curl "http://localhost/api/v1/add?a=2&b=3"
```
```json
{
  "operation": "add",
  "a": 2.0,
  "b": 3.0,
  "result": 5.0
}
```

#### 2. Subtract
```bash
curl "http://localhost/api/v1/subtract?a=10&b=4"
```
```json
{
  "operation": "subtract",
  "a": 10.0,
  "b": 4.0,
  "result": 6.0
}
```

#### 3. Multiply
```bash
curl "http://localhost/api/v1/multiply?a=2.5&b=4"
```
```json
{
  "operation": "multiply",
  "a": 2.5,
  "b": 4.0,
  "result": 10.0
}
```

#### 4. Divide
```bash
curl "http://localhost/api/v1/divide?a=7&b=2"
```
```json
{
  "operation": "divide",
  "a": 7.0,
  "b": 2.0,
  "result": 3.5
}
```

#### 5. Division by Zero Error (HTTP 400)
```bash
curl -i "http://localhost/api/v1/divide?a=10&b=0"
```
```json
{
  "timestamp": "2026-10-24T07:56:45.617535579Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Division by zero is not allowed",
  "path": "/api/v1/divide"
}
```

#### 6. Missing Parameter Validation (HTTP 400)
```bash
curl -i "http://localhost/api/v1/subtract?a=5"
```
```json
{
  "timestamp": "2026-10-24T07:56:45.734329206Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required parameter 'b'",
  "path": "/api/v1/subtract"
}
```

#### 7. Non-Numeric Parameter Error (HTTP 400)
```bash
curl -i "http://localhost/api/v1/multiply?a=abc&b=2"
```
```json
{
  "timestamp": "2026-10-24T07:56:45.811168001Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Parameter 'a' must be a number, but was 'abc'",
  "path": "/api/v1/multiply"
}
```

#### 8. Special Floating-Point Values (HTTP 400)
```bash
curl -i "http://localhost/api/v1/add?a=NaN&b=1"
```
```json
{
  "timestamp": "2026-10-24T07:56:45.864469893Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Parameter 'a' must be a finite number, but was 'NaN'",
  "path": "/api/v1/add"
}
```

#### 9. Result Overflow Protection (HTTP 400)
```bash
curl -i "http://localhost/api/v1/multiply?a=1e308&b=10"
```
```json
{
  "timestamp": "2026-10-24T07:56:45.912608892Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Result of multiply is too large to represent as a finite number",
  "path": "/api/v1/multiply"
}
```

#### 10. Health Endpoint (Spring Boot Actuator)
```bash
curl "http://localhost/actuator/health"
```
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

## 3. Local Development & Testing

### Build with Maven
```bash
mvn clean package
```

### Run Unit and Integration Tests
```bash
mvn test
```
Test results and XML reports for Jenkins are generated under `target/surefire-reports/`.

### Run Locally
```bash
mvn spring-boot:run
```
The server will start at `http://localhost:8081`.

---

## 4. Docker & Multi-Stage Build

The multi-stage `Dockerfile`:
- **Stage 1 (Builder)**: Uses `maven:3.9-eclipse-temurin-17` to cache dependencies (`pom.xml` layer) and compile the JAR.
- **Stage 2 (Runtime)**: Uses slim `bellsoft/liberica-openjre-alpine:17` (221 MB on disk). Creates a dedicated non-root user (`appuser`) and exposes port `8081`.

> **Why not `eclipse-temurin:17-jre-alpine`?** Temurin publishes its Alpine
> variants for `linux/amd64` only, so that base image cannot be resolved on
> Apple Silicon (`no match for platform in manifest`). Liberica publishes both
> `linux/amd64` and `linux/arm64` while staying Alpine-slim.

### Build Image Manually
```bash
docker build -t calculator:latest .
```

### Run Container Standalone
```bash
docker run -d -p 8081:8081 --name calculator calculator:latest
```

---

## 5. Multi-Container Orchestration (Docker Compose)

The `docker-compose.yml` orchestrates:
1. `calculator-app-1`: First application instance running on internal network `calc-net`.
2. `calculator-app-2`: Second application instance running on internal network `calc-net`.
3. `nginx`: Nginx reverse proxy distributing HTTP traffic across both instances and publishing port `80` to host.

### Launch Stack
```bash
docker compose up -d --build
```

### Check Container Status & Health
```bash
docker compose ps
```

### Verify Round-Robin Load Balancing
```bash
for i in {1..4}; do curl -s "http://localhost/api/v1/add?a=1&b=1"; echo ""; done
docker compose logs nginx --tail 10
```

### Stop Stack
```bash
docker compose down
```

---

## 6. Jenkins Declarative Pipeline

The `Jenkinsfile` defines a 6-stage CI/CD workflow:

1. **Checkout**: Checks out triggering Git commit from GitHub repository.
2. **Build**: Executes `mvn -B clean package -DskipTests`.
3. **Test**: Executes `mvn -B test` and publishes JUnit XML test reports. A failed test immediately terminates the pipeline.
4. **Docker Build**: Builds the multi-stage image tagged with `${BUILD_NUMBER}` and `:latest`.
5. **Push**: Securely authenticates with Docker Hub using Jenkins credentials store (`dockerhub-creds`) and pushes both tags.
6. **Deploy**: Deploys stack with `docker compose up -d --no-build`, polls health status up to 120s, and verifies HTTP 200 from `/actuator/health`.
7. **Post Actions**: Dispatches email notification with build status and console log link.

---

## 7. Repository Structure

```
.
├── Dockerfile                   # Multi-stage optimized Docker build
├── .dockerignore                # Excludes unneeded files from Docker context
├── Jenkinsfile                  # Declarative CI/CD pipeline definition
├── docker-compose.yml           # Two app replicas + Nginx reverse proxy
├── nginx/
│   └── nginx.conf               # Nginx reverse proxy configuration & upstream balancing
├── pom.xml                      # Maven build descriptor with Spring Boot 3 & JUnit 5
├── .gitignore                   # Ignore target/, IDE files, and secrets
├── README.md                    # Project documentation
└── src/
    ├── main/
    │   ├── java/com/calculator/
    │   │   ├── CalculatorApplication.java
    │   │   ├── controller/
    │   │   │   └── CalculatorController.java
    │   │   ├── dto/
    │   │   │   ├── CalculatorResponse.java
    │   │   │   └── ErrorResponse.java
    │   │   ├── exception/
    │   │   │   ├── CalculatorException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   └── service/
    │   │       └── CalculatorService.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/calculator/
            ├── CalculatorApplicationTests.java
            ├── controller/
            │   └── CalculatorControllerTest.java
            └── service/
                └── CalculatorServiceTest.java
```
