# CSE 816 — Software Production Engineering
## Mini-Project Report: End-to-End CI/CD Pipeline for a Calculator REST API

**Name:** Garv Rajput  
**Roll No:** [Your Roll No Here]  
**Faculty:** Prof. B. Thangaraju  
**Repository:** https://github.com/GarvRajput17/SPE_MiniProj  
**Docker Hub:** https://hub.docker.com/r/garvrajput17/calculator (public)  

---

### 1. Overview

Modern software engineering relies on continuous integration and continuous delivery (CI/CD) to ensure rapid, dependable, and automated releases. Manually testing, building, packaging, and deploying code introduces human error and slows deployment cycles. CI/CD automates these stages so that every code commit is validated against automated tests and released with zero manual intervention.

This project delivers an end-to-end DevOps pipeline for a production-style Calculator REST API built using Java (Spring Boot) and Maven. A single `git push` to GitHub triggers an automated workflow in Jenkins that checks out the source code, runs automated JUnit tests, packages the executable JAR, builds an optimized multi-stage Docker image, pushes it to Docker Hub, and deploys the multi-container stack with Docker Compose behind an Nginx reverse proxy.

The toolchain integrates four core components:
- **Git & GitHub:** Version control, feature branching, and pull request workflows.
- **Jenkins:** Automation server executing a declarative 6-stage pipeline.
- **Docker:** Multi-stage image packaging ensuring reproducible environments.
- **Docker Compose:** Multi-container orchestration managing two API replicas behind an Nginx reverse proxy with automated health checks.

The Calculator API provides arithmetic endpoints (`/add`, `/subtract`, `/multiply`, `/divide`) and an Actuator health check (`/actuator/health`). All inputs are strictly validated, rejecting missing values, non-numeric strings, `NaN`, `Infinity`, overflow, and division by zero with structured HTTP 400 responses.

---

### 2. Pipeline Architecture

Every push to the repository triggers a Jenkins declarative pipeline via a GitHub webhook.

```
+------------------+     git push      +--------------------+     webhook     +------------------+
| Developer Laptop | ----------------> | GitHub Repository  | --------------> | Jenkins CI/CD    |
+------------------+                   +--------------------+                 +------------------+
                                                                                       |
                                                                                       v
                                                                             +-------------------+
                                                                             | 1. Checkout       |
                                                                             | 2. Build (Maven)  |
                                                                             | 3. Test (JUnit 5) |
                                                                             | 4. Docker Build   |
                                                                             | 5. Docker Push    |
                                                                             | 6. Compose Deploy |
                                                                             +-------------------+
                                                                                       |
                                                     +---------------------------------+
                                                     v                                 v
                                          +---------------------+           +---------------------+
                                          | Docker Hub Registry |           | Email Notification  |
                                          +---------------------+           +---------------------+
                                                     |
                                                     v
                              +-----------------------------------------------+
                              | Compose Stack (calc-net)                      |
                              |                                               |
                              |          +--------------------------+         |
                              |          | Nginx Reverse Proxy (80) |         |
                              |          +--------------------------+         |
                              |             /                    \            |
                              |            v                      v           |
                              |   +------------------+  +------------------+  |
                              |   | calculator-app-1 |  | calculator-app-2 |  |
                              |   |   (Port 8081)    |  |   (Port 8081)    |  |
                              |   +------------------+  +------------------+  |
                              +-----------------------------------------------+
```

Clients only access Nginx on port `80`. The application replicas are isolated on the internal bridge network (`calc-net`).

---

### 3. Pipeline Stages

The declarative pipeline is versioned inside `Jenkinsfile`.

| Stage | Command / Action | Description |
|---|---|---|
| **Checkout** | `checkout scm` | Fetches the commit that triggered the build from GitHub. |
| **Build** | `mvn -B clean package -DskipTests` | Compiles source files and produces the executable Spring Boot JAR. |
| **Test** | `mvn -B test` | Executes 27 JUnit 5 unit and MockMvc integration tests; publishes JUnit XML reports. A failed test aborts the pipeline. |
| **Docker Build** | `docker build -t $DOCKER_IMAGE:$BUILD_NUMBER -t $DOCKER_IMAGE:latest .` | Builds the multi-stage Docker image using cached dependencies and tags it with build number and `:latest`. |
| **Push** | `withCredentials(...) { docker push ... }` | Authenticates securely to Docker Hub using Jenkins credentials and pushes both tags. Credentials are never logged. |
| **Deploy** | `IMAGE_TAG=$BUILD_NUMBER docker compose up -d --no-build` | Reconciles the stack in place, polls container health for up to 150s, then smoke-tests the API through Nginx — including asserting that divide-by-zero still returns HTTP 400. |
| **Post Actions** | `mail to: 'garvgrez@gmail.com', ...` | Sends an email notification on build success or failure with console link. |

---

### 4. Docker & Docker Compose Design

#### Multi-Stage Dockerfile
- **Stage 1 (Maven Builder):** Uses `maven:3.9-eclipse-temurin-17`. `pom.xml` is copied and `mvn dependency:go-offline` is executed first to leverage Docker layer caching.
- **Stage 2 (Runtime):** Uses lightweight `bellsoft/liberica-openjre-alpine:17` (221 MB on disk, 66 MB compressed on Docker Hub). An unprivileged user `appuser` is created and assigned ownership. Port `8081` is exposed and the JVM is exec'd as PID 1 so container stop signals propagate cleanly.

#### Multi-Container Deployment
- **Replication:** Runs two identical application instances (`calculator-app-1` and `calculator-app-2`) listening on internal port 8081.
- **Reverse Proxy:** Nginx distributes incoming requests using round-robin load balancing.
- **Health Checks:** Both application instances and Nginx include automated health checks hitting `/actuator/health`. Nginx starts only after application containers report healthy (`condition: service_healthy`).
- **Idempotence:** Re-running the pipeline updates containers in-place without dropping ongoing connections.

---

### 5. Challenges Faced & Solutions

| Challenge | Root Cause | Solution |
|---|---|---|
| **Port Conflict on Port 8080** | Jenkins default port is 8080, causing `BindException` when running the application on the host. | Configured `server.port=8081` in `application.properties` and updated container internal ports to 8081. |
| **Handling `NaN` and `Infinity`** | Standard `Double.parseDouble()` treats strings like `"NaN"` and `"Infinity"` as valid numbers. | Added input sanitization to explicitly check `Double.isNaN()` and `Double.isInfinite()` and return HTTP 400. |
| **Arithmetic Overflow** | Multiplying large doubles (e.g., `1e308 * 10`) results in `Infinity` rather than an exception. | Added result bounds checking in `CalculatorService` to return HTTP 400 when results exceed finite representation. |
| **Nginx IPv6 Healthcheck Failure** | Inside Alpine Linux containers, `localhost` may resolve to `::1` (IPv6), which Nginx is not bound to by default. | Explicitly bound health check to IPv4 address `http://127.0.0.1/actuator/health`. |
| **Idempotent Deployment** | Rebuilding images on every `docker compose up` in the deploy stage slows down deployments and can fail if local context differs. | Tagged images during Docker Build stage and used `IMAGE_TAG=${BUILD_NUMBER} docker compose up -d --no-build` in Deploy stage. |

---

### 6. Sample API Verification

```bash
# Valid Operations
curl "http://localhost/api/v1/add?a=2&b=3"
# {"operation":"add","a":2.0,"b":3.0,"result":5.0}

curl "http://localhost/api/v1/subtract?a=10&b=4"
# {"operation":"subtract","a":10.0,"b":4.0,"result":6.0}

curl "http://localhost/api/v1/multiply?a=2.5&b=4"
# {"operation":"multiply","a":2.5,"b":4.0,"result":10.0}

curl "http://localhost/api/v1/divide?a=7&b=2"
# {"operation":"divide","a":7.0,"b":2.0,"result":3.5}

# Validation & Error Handling (HTTP 400)
curl -i "http://localhost/api/v1/divide?a=10&b=0"
# {"timestamp":"...","status":400,"error":"Bad Request","message":"Division by zero is not allowed","path":"/api/v1/divide"}

curl -i "http://localhost/api/v1/subtract?a=5"
# {"timestamp":"...","status":400,"error":"Bad Request","message":"Missing required parameter 'b'","path":"/api/v1/subtract"}

curl -i "http://localhost/api/v1/multiply?a=abc&b=2"
# {"timestamp":"...","status":400,"error":"Bad Request","message":"Parameter 'a' must be a number, but was 'abc'","path":"/api/v1/multiply"}

curl -i "http://localhost/api/v1/add?a=NaN&b=1"
# {"timestamp":"...","status":400,"error":"Bad Request","message":"Parameter 'a' must be a finite number, but was 'NaN'","path":"/api/v1/add"}

curl -i "http://localhost/api/v1/multiply?a=1e308&b=10"
# {"timestamp":"...","status":400,"error":"Bad Request","message":"Result of multiply is too large to represent as a finite number","path":"/api/v1/multiply"}

# Health Endpoint
curl "http://localhost/actuator/health"
# {"status":"UP","components":{"diskSpace":{"status":"UP"},"ping":{"status":"UP"}}}
```
