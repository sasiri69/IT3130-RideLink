# RideLink – Driver & Vehicle Service (IT3130)

> **IT3130 Application Development | Group Assignment**  
> **Microservice #2 — Driver & Vehicle Management Service**  
> **Primary Service Owner: Member 2 (IT24100687)**

---

## 1. Overview & Service Responsibilities

The **Driver & Vehicle Service** is one of the four core independently deployable Spring Boot microservices powering the **RideLink** ride-sharing platform. It manages driver operational profiles, vehicle registration, availability states, operational service areas, and simulated GPS location tracking.

### Core Responsibilities:
- **Driver Operational Profile**: Maintains operational lifecycle records mapped to Account Service credentials via stable identifiers (`driverId`).
- **Vehicle Registration & Replacement**: Manages vehicle attributes (make, model, year, license plate, vehicle type, seating capacity).
- **Availability State Machine**: Manages transitions between `OFFLINE`, `AVAILABLE`, `BUSY`, and `SUSPENDED`.
- **Service Area & Location Tracking**: Records operational service areas and updates simulated GPS coordinates (`latitude`, `longitude`, `addressName`).
- **Dispatching Retrieval (For Member 3 - Ride Management)**: Provides query endpoints filtering available drivers by service area, vehicle type, and real-time Haversine distance proximity.
- **Interservice Integration**: Synchronously connects to **Member 1 (Account Service)** via REST to verify driver existence, `DRIVER` role, and `ACTIVE` account status before profile activation.

---

## 2. Architectural & REST Compliance (Lectures 07 & 08)

This microservice is strictly designed according to **IT3130 Lecture 07 (API & REST Fundamentals)** and **Lecture 08 (Communication Interfaces II)**:

| Lecture Constraint / Principle | Implementation in Driver & Vehicle Service |
|---|---|
| **Resource-Oriented URLs (Nouns, Not Verbs)** | `/api/drivers`, `/api/drivers/{id}`, `/api/drivers/{id}/availability`, `/api/drivers/{id}/location`. Never contains operation names like `/getDrivers` or `/updateLocation`. |
| **HTTP Semantics & Idempotency** | • `POST`: Creates resources (`201 Created`).<br/>• `GET`: Safe & idempotent retrieval (`200 OK` or `404 Not Found`).<br/>• `PUT`: Idempotent replacement (`200 OK`).<br/>• `PATCH`: Partial state modification (`200 OK`). |
| **Path vs Query vs Body** | • **Path Variables**: Unique resource identity (`/api/drivers/{driverId}`).<br/>• **Query Parameters**: Subsets and filtering (`/api/drivers/available?serviceArea=Colombo&vehicleType=CAR`).<br/>• **Request Body**: JSON representations for creation and updates. |
| **Predictable Status Codes** | Never returns `200 OK` for errors. Uses `200`, `201`, `400` (validation), `401` (unauthorized), `403` (forbidden), `404` (not found), `409` (duplicate license/plate), `503` (service unavailable). |
| **Statelessness (Constraint #2)** | Every protected request carries authentication via `Authorization: Bearer <jwt>`. No session state is held on the server. |
| **Data Boundary Autonomy** | Member 2 maintains its own isolated database (`ridelink_driver_db`). Direct database queries into Member 1's tables are strictly avoided. |
| **Richardson Maturity Model (Level 3)** | Level 2 (HTTP verbs + status codes) plus Level 3 hypermedia links (`links`) included in responses for discoverable next actions. |

---

## 3. Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Java 17 |
| **Framework** | Spring Boot 4.0.8 |
| **Database** | MongoDB (isolated database `ridelink_driver_db`) |
| **Security** | Spring Security 6 + JJWT 0.11.5 (Stateless Bearer JWT) |
| **Validation** | Jakarta Bean Validation (Hibernate Validator) |
| **Documentation** | SpringDoc OpenAPI 2.8.5 / Swagger UI |
| **Testing** | JUnit 5 + Mockito + Spring MockMvc |
| **Build Tool** | Apache Maven 3.9+ |

---

## 4. Configuration – Environment Variables

> ⚠️ **Never commit secrets to the repository.** Configuration is loaded from `.env` or system environment variables:

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port for Driver Service | `8082` |
| `MONGODB_URI` | MongoDB connection string (isolated DB) | `mongodb://localhost:27017/ridelink_driver_db` |
| `JWT_SECRET` | Shared JWT signing secret (same as Member 1) | *required* |
| `ACCOUNT_SERVICE_URL` | Base URL of Member 1 (Account Service) | `http://localhost:8081` |
| `ACCOUNT_SERVICE_TIMEOUT_MS` | Timeout for synchronous calls to Member 1 | `5000` |

---

## 5. Running the Service

### Prerequisites:
- Java 17+ installed (`java -version`)
- MongoDB running locally or MongoDB Atlas connection string
- Member 1 (Account Service) running on `http://localhost:8081` (optional for standalone testing)

### Commands:
```bash
# 1. Navigate to the service folder
cd IT3130-RideLink-driver-service

# 2. Build and run all automated unit & slice tests
.\mvnw clean test

# 3. Start the Spring Boot microservice
.\mvnw spring-boot:run
```

The service will start on **`http://localhost:8082`**.

---

## 6. API Endpoints Catalog

| Method | Endpoint | Auth | Description | Status Code |
|---|---|---|---|---|
| **POST** | `/api/drivers` | Bearer JWT | Register driver profile & vehicle (Calls Member 1 to verify) | `201 Created` / `400` / `409` |
| **GET** | `/api/drivers/me` | Bearer JWT | Retrieve currently authenticated driver's profile | `200 OK` / `401` / `404` |
| **GET** | `/api/drivers/{driverId}` | Public | Retrieve driver profile by Account User ID or MongoDB ID | `200 OK` / `404 Not Found` |
| **PUT** | `/api/drivers/{driverId}/vehicle` | Bearer JWT | Replace/update vehicle details (Idempotent PUT) | `200 OK` / `400` / `403` / `409` |
| **POST** | `/api/drivers/{driverId}/vehicle` | Bearer JWT | Add/register vehicle for driver (Sub-resource creation) | `201 Created` / `400` / `403` / `409` |
| **GET** | `/api/drivers/{driverId}/vehicle` | Public | Retrieve vehicle details for a driver | `200 OK` / `404 Not Found` |
| **DELETE** | `/api/drivers/{driverId}/vehicle` | Bearer JWT | Remove/deregister vehicle details (Lecture 07 DELETE) | `204 No Content` / `400` / `403` / `404` |
| **PATCH** | `/api/drivers/{driverId}/availability` | Bearer JWT | Update availability (`AVAILABLE` or `OFFLINE`) | `200 OK` / `400` / `403` |
| **PATCH** | `/api/drivers/{driverId}/location` | Bearer JWT | Update simulated GPS location coordinates | `200 OK` / `400` / `403` |
| **PATCH** | `/api/drivers/{driverId}/service-area` | Bearer JWT | Update operational service area (e.g. Colombo, Malabe) | `200 OK` / `400` / `403` |
| **GET** | `/api/drivers/available` | Public | Query available drivers filtered by `serviceArea` & `vehicleType` | `200 OK` |
| **GET** | `/api/drivers/nearby` | Public | Haversine proximity query (`latitude`, `longitude`, `radiusKm`) | `200 OK` / `400` |
| **GET** | `/api/drivers` | ADMIN only | List all drivers with query filters | `200 OK` / `403` |
| **PATCH** | `/api/drivers/{driverId}/admin-status` | ADMIN only | Administrative status override (e.g. `SUSPENDED`) | `200 OK` / `403` / `404` |

---

## 7. Swagger UI & OpenAPI Specification

Once the service is running, explore and interactively test all endpoints via Swagger UI:

- **Swagger UI**: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **OpenAPI JSON Docs**: [http://localhost:8082/v3/api-docs](http://localhost:8082/v3/api-docs)

To test authenticated endpoints in Swagger UI:
1. Click the **Authorize** button (top right).
2. Paste your Bearer JWT token issued by Member 1 (Account Service).
3. Execute requests directly from your browser.

---

## 8. Sample JSON Payloads & Workflows

### 8.1 Register Driver Profile (`POST /api/drivers`)
```json
{
  "driverId": "66e2c34a9b1c2d3e4f5a6b7c",
  "licenseNumber": "B7894561",
  "serviceArea": "Colombo",
  "vehicle": {
    "make": "Toyota",
    "model": "Prius",
    "year": 2022,
    "licensePlate": "CAB-5566",
    "color": "Silver Metallic",
    "vehicleType": "CAR",
    "seatingCapacity": 4
  },
  "initialLocation": {
    "latitude": 6.9271,
    "longitude": 79.8612,
    "addressName": "Colombo Fort Station"
  }
}
```

### 8.2 Declare Availability (`PATCH /api/drivers/{driverId}/availability`)
```json
{
  "status": "AVAILABLE"
}
```

### 8.3 Update Simulated GPS Coordinates (`PATCH /api/drivers/{driverId}/location`)
```json
{
  "latitude": 6.9147,
  "longitude": 79.9733,
  "addressName": "Malabe SLIIT Campus"
}
```

### 8.4 Dispatching Query (`GET /api/drivers/available?serviceArea=Colombo&vehicleType=CAR`)
Returns lightweight, dispatch-ready representations for Member 3 (Ride Management Service).

---

## 9. Automated Testing & Quality Assurance

The service contains 19 comprehensive unit and slice tests verifying:
- Successful driver operational registration with Member 1 mock verification.
- **Negative Scenarios (Workflow 7)**:
  - Account does not exist in Member 1 (returns `404 Not Found`).
  - Account exists in Member 1 with role `PASSENGER` (returns `400 Bad Request`).
  - Account exists in Member 1 with status `SUSPENDED` (returns `400 Bad Request`).
  - Duplicate driver registration attempt (returns `409 Conflict`).
  - Duplicate vehicle license plate (returns `409 Conflict`).
  - Suspended driver attempting to switch to `AVAILABLE` (returns `400 Bad Request`).
  - Validation failures on missing required fields (returns `400 Bad Request`).
- Simulated GPS Haversine distance calculations and sorting.

To run the automated test suite:
```bash
.\mvnw test
```
All test reports are generated under `target/surefire-reports/`.

---

## 10. Continuous Integration (CI/CD)

The service includes an automated **GitHub Actions** CI pipeline:
- **Location**: `.github/workflows/ci.yml`
- Runs on every push and pull request to `main`, `develop`, and `feature/**`.
- Provisions an ephemeral MongoDB 6.0 container on Ubuntu.
- Builds with Java 17 Temurin, compiles code, runs all 19 tests, and publishes test artifacts.

---

## 11. Service Ownership Statement

- **Service**: Driver & Vehicle Management Service (Microservice #2)
- **Primary Owner**: Member 2 — IT24100687
- **Module**: IT3130 – Application Development (Year 3, Semester 1)
