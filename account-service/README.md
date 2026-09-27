# RideLink – Account Service (IT3130)

> **IT3130 Application Development | Group Assignment**
> Microservice #1 — Account & Authentication Service

---

## Overview

The Account Service manages all user identity, authentication and authorization for the RideLink ride-sharing platform. It is one of four independently deployable Spring Boot microservices.

**Responsibilities:**
- Passenger and driver account registration
- Login and JWT access token issuance
- Refresh token rotation and logout
- Role-based access control (PASSENGER, DRIVER, ADMIN)
- Profile viewing and updating
- Account status management (ACTIVE / INACTIVE / SUSPENDED)

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.8 |
| Database | MongoDB Atlas (own isolated database) |
| Authentication | JWT (JJWT 0.11.5) + BCrypt |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Build | Maven |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- MongoDB Atlas account (or local MongoDB)

---

## Configuration – Environment Variables

> ⚠️ **Never hardcode credentials.** Copy `.env.example` to `.env` and fill in real values.

| Variable | Description | Default |
|---|---|---|
| `MONGODB_URI` | MongoDB connection string | *required* |
| `JWT_SECRET` | JWT signing secret (≥32 hex chars) | *required* |
| `JWT_EXPIRATION` | Access token expiry in ms | `86400000` (24h) |
| `JWT_REFRESH_EXPIRATION` | Refresh token expiry in ms | `604800000` (7d) |
| `SERVER_PORT` | HTTP port | `8081` |

Set variables before running:
```bash
# PowerShell
$env:MONGODB_URI = "mongodb+srv://..."
$env:JWT_SECRET  = "your_secret_here"
```

---

## Running the Service

```bash
# 1. Clone and enter the directory
git clone <repo-url>
cd account-service

# 2. Set environment variables (see above)

# 3. Build and run
mvn spring-boot:run
```

Service starts on **http://localhost:8081**

---

## API Endpoints

| Method | Endpoint | Auth | Description | Status Code |
|---|---|---|---|---|
| POST | `/api/auth/register` | None | Register new passenger/driver | `201 Created` |
| POST | `/api/auth/login` | None | Login — returns JWT + refresh token | `200 OK` (or `401 Unauthorized`) |
| POST | `/api/auth/refresh` | None | Get new access token using refresh token | `200 OK` (or `401 Unauthorized`) |
| POST | `/api/auth/logout` | Bearer JWT | Revoke refresh tokens | `204 No Content` |
| GET | `/api/users/me` | Bearer JWT | Get own profile (UserResponse DTO) | `200 OK` |
| PATCH | `/api/users/me/profile` | Bearer JWT | Update profile details (PUT also supported) | `200 OK` |
| GET | `/api/users?role={role}&status={status}` | ADMIN only | Get all users (supports query filtering by role/status) | `200 OK` |
| GET | `/api/users/{id}` | ADMIN only | Get user by ID (path variable) | `200 OK` (or `404 Not Found`) |
| PATCH | `/api/users/{id}/status` | ADMIN only | Update account status | `200 OK` |
| DELETE | `/api/users/me` | Bearer JWT | Delete own account & revoke all sessions | `204 No Content` |
| DELETE | `/api/users/{id}` | ADMIN only | Delete user by ID & revoke their sessions | `204 No Content` (or `404 Not Found`) |

> 🔒 **Security & Clean Architecture Note**: In compliance with REST API standards, raw database entities (`User`) are never returned directly; all user endpoints return safe `UserResponse` DTOs that strictly exclude internal fields such as `passwordHash`.
>
> 💡 **REST Design Principles (Lecture 07/08 Compliance)**:
> - **Path Variables** vs **Query Parameters**: Path variables (`/api/users/{id}`) identify specific resource entities; query parameters (`/api/users?role=DRIVER&status=ACTIVE`) filter collection subsets.
> - **Idempotency**: `GET`, `PUT`, and `DELETE` operations are idempotent.
> - **Stateless Authentication**: Every protected request carries authentication via `Authorization: Bearer <jwt>`. No session state is held on the server.

---

## Swagger UI

Once running, open: **http://localhost:8081/swagger-ui.html**

Click **Authorize** and paste your Bearer token to test protected endpoints.

---

## Running Tests

```bash
mvn test
```

Test results are saved to `target/surefire-reports/`.

---

## Sample Test Data

**Register a Passenger:**
```json
POST /api/auth/register
{
  "firstName": "Kamal",
  "lastName": "Perera",
  "email": "kamal@test.com",
  "password": "password123",
  "phone": "0712345678",
  "role": "PASSENGER"
}
```

**Register a Driver:**
```json
POST /api/auth/register
{
  "firstName": "Nimal",
  "lastName": "Silva",
  "email": "nimal@test.com",
  "password": "password123",
  "phone": "0771234567",
  "role": "DRIVER"
}
```

---

## CI/CD

GitHub Actions workflow runs on every push/PR to `main` and `develop`:
- `.github/workflows/ci.yml`
- Builds with Java 17, runs all unit tests, uploads test report artifacts

---

## Service Owner

**Member 1** — Primary owner of the Account Service.
