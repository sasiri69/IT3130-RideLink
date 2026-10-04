# RideLink – Account Service (IT3130)


## Overview

The Account Service manages all user identity, authentication and authorization for the RideLink ride-sharing platform. It is one of four independently deployable Spring Boot microservices.

**Responsibilities:**
- Passenger and driver account registration
- Login and JWT access token issuance
- Refresh token rotation and logout
- Role-based access control (PASSENGER, DRIVER, ADMIN)
- Profile viewing and updating
- Account status management (ACTIVE / INACTIVE / SUSPENDED)



## Technology Stack

| Layer | Technology |

| Language | Java 17 |
| Framework | Spring Boot 4.0.8 |
| Database | MongoDB Atlas (own isolated database) |
| Authentication | JWT (JJWT 0.11.5) + BCrypt |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Build | Maven |



Service starts on **http://localhost:8081**



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


