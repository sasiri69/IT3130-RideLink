# RideLink – Backend Microservices for a Ride-Sharing Platform

Backend microservices solution for a ride-sharing platform developed for **IT3130 – Application Development**.

---

## 👥 Group Members & Service Ownership

| # | Microservice | Primary Owner | Student ID | Status | Port |
|---|---|---|---|---|---|
| **1** | **Account Service** | Jayakody J A K S S | IT24100778 | ✅ Completed & Tested | `8081` |
| **2** | **Driver & Vehicle Service** | Member 2 | IT24100687 | ✅ Completed & Tested | `8082` |
| **3** | **Ride Management Service** | Idusara S K U | IT24101290 | In Progress | `8083` |
| **4** | **Fare & Payment Service** | Meththasinghe M.D.D.T | IT24100891 | In Progress | `8084` |

---

## 🏗️ Architecture & Technology Stack

- **Framework**: Java 17, Spring Boot 3
- **Security**: Stateless JWT Authentication & Role-Based Access Control (`ROLE_PASSENGER`, `ROLE_DRIVER`, `ROLE_ADMIN`)
- **Database Boundary**: Independent MongoDB databases (`account_db` and `driver_db`)
- **API Documentation**: OpenAPI 3 / Swagger UI
- **Containerization**: Docker & Docker Compose
- **Continuous Integration**: GitHub Actions CI (`.github/workflows/ci.yml`)

---

## 📁 Repository Structure

```
IT3130-RideLink/
├── account-service/          # Member 1: Account & Auth Service (Port 8081)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── postman/
├── driver-service/           # Member 2: Driver & Vehicle Service (Port 8082)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── postman/
├── .github/workflows/        # Automated CI building and testing all services
│   └── ci.yml
├── docker-compose.yml        # Orchestrates all microservices & MongoDB databases
└── README.md
```

---

## 🚀 Running the System

### Option 1: Running with Docker Compose (Recommended)
```bash
docker-compose up --build
```

### Option 2: Running Services Locally
1. Start MongoDB on ports `27017` and `27018`.
2. Run Account Service:
   ```bash
   cd account-service
   mvn spring-boot:run
   ```
3. Run Driver & Vehicle Service:
   ```bash
   cd driver-service
   mvn spring-boot:run
   ```

---

## 📖 API Documentation & Swagger UI
- **Account Service**: `http://localhost:8081/swagger-ui.html`
- **Driver & Vehicle Service**: `http://localhost:8082/swagger-ui.html`
