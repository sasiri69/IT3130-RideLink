# RideLink - Backend Microservices for a Ride-Sharing Platform

Backend microservices architecture developed for IT3130 - Application Development.

---

## Group Members & Microservice Ownership

| # | Microservice | Primary Owner | Student ID | Service Status | Port |
|---|---|---|---|---|---|
| 1 | Account & Auth Service | Member 1 | IT24100778 | Completed & Tested | 8081 |
| 2 | Driver & Vehicle Service | Sasiru Deshan | IT24100687 | Completed & Tested | 8082 |
| 3 | Ride Management Service | Member 3 | IT24101290 | Completed & Tested | 8083 |
| 4 | Fare & Payment Service | Member 4 | IT24100891 | Completed & Tested | 8084 |

---

## Architecture & Technology Stack

- Framework: Java 17, Spring Boot 3
- Security: Stateless JWT Authentication & Role-Based Access Control (PASSENGER, DRIVER, ADMIN)
- Persistence Boundary: Independent database per microservice (Database-per-Service pattern)
- Inter-Service Communication: Synchronous REST via Spring RestTemplate / WebClient
- API Documentation: OpenAPI 3 / Swagger UI
- Continuous Integration: GitHub Actions CI (.github/workflows/ci.yml)
- Deployment Option: Docker Compose (Optional enhancement per IT3130 Section 6.5)

---

## Repository Structure

```
IT3130-RideLink/
├── account-service/          # Account & Auth Service (Port 8081)
├── driver-service/           # Driver & Vehicle Service (Port 8082)
├── ride-service/             # Ride Management Service (Port 8083)
├── payment-service/          # Fare & Payment Service (Port 8084)
├── .github/workflows/        # Automated CI workflow building & testing services
├── e2e_positive_flow.ps1     # Automated end-to-end positive flow test script
├── docker-compose.yml        # Optional Docker Compose deployment configuration
└── README.md
```

---

## System Execution Instructions

### Primary Execution Method: Local Execution (Recommended for Viva & Testing)

Each microservice runs as an independent Spring Boot application using individual database boundaries:

1. Prerequisites:
   - JDK 17 or higher
   - Apache Maven 3.8+
   - MongoDB running locally on localhost:27017 (or configured environment URI)

2. Start Microservices:

   - Account Service (Port 8081):
     ```bash
     cd account-service
     mvn spring-boot:run
     ```

   - Driver Service (Port 8082):
     ```bash
     cd driver-service
     mvn spring-boot:run
     ```

   - Ride Management Service (Port 8083):
     ```bash
     cd ride-service
     mvn spring-boot:run
     ```

   - Fare & Payment Service (Port 8084):
     ```bash
     cd payment-service
     mvn spring-boot:run
     ```

   Note: Alternatively, run `powershell -File start-all-services.ps1` to launch all four services simultaneously.

### Optional Execution Method: Docker Compose

Per IT3130 Assignment Specification Section 6.5, Docker containerization is an optional enhancement. If Docker Desktop is available:

```bash
docker-compose up --build
```

---

## API Documentation & Swagger UI Endpoints

Once services are running, interactive Swagger UI documentation and OpenAPI 3 contracts are accessible at:

- Account Service: http://localhost:8081/swagger-ui.html
- Driver & Vehicle Service: http://localhost:8082/swagger-ui.html
- Ride Management Service: http://localhost:8083/swagger-ui.html
- Fare & Payment Service: http://localhost:8084/swagger-ui.html

---

## Testing & Verification

### Unit Testing
Run unit tests for any service using Maven:
```bash
mvn test
```

### End-to-End Positive Workflow Test
Execute the automated PowerShell end-to-end integration test covering all 12 positive flow steps across all 4 microservices:
```powershell
powershell -ExecutionPolicy Bypass -File e2e_positive_flow.ps1
```

### Postman Integration Testing
Import `RideLink_Postman_Collection.json` into Postman to execute the 94 automated positive and negative test cases.
