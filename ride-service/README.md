# RideLink – Ride Management Service (Member 3)

Backend microservice responsible for ride booking creation, driver assignment, and the full ride lifecycle state machine.

**Owner**: Member 3 – IT24101290 (Idusara S K U)  
**Port**: `8083`  
**Database**: MongoDB (`ride_db`)  

---

## 🚀 Key Features

1. **Ride Lifecycle State Machine**:
   - `REQUESTED`: Initial booking created by passenger.
   - `ASSIGNED`: Eligible available driver assigned (auto or manual).
   - `ACCEPTED`: Driver confirms ride, updating availability to `BUSY`.
   - `IN_PROGRESS`: Driver arrives and begins journey.
   - `COMPLETED`: Driver drops passenger off, computes final fare, and resets availability to `AVAILABLE`.
   - `CANCELLED`: Permitted before trip commences, automatically releasing driver if assigned.

2. **Interservice Integration**:
   - **Driver Service (`8082`)**: Queries available drivers and coordinates driver status transitions (`BUSY` / `AVAILABLE`).
   - **Account Service (`8081`)**: Validates passenger account existence and active role.

3. **Security**:
   - Stateless JWT Bearer token authentication compatible with Account Service.

---

## 📡 REST API Summary

| Method | Endpoint | Description | Status Code |
|:---|:---|:---|:---|
| `POST` | `/api/rides` | Create a new ride request | `201 Created` |
| `GET` | `/api/rides/{rideId}` | Get ride details by ID | `200 OK` |
| `GET` | `/api/rides/passenger/{passengerId}` | List passenger ride history | `200 OK` |
| `GET` | `/api/rides/driver/{driverId}` | List driver assigned rides | `200 OK` |
| `PATCH` | `/api/rides/{rideId}/assign-driver` | Assign driver (auto or manual) | `200 OK` |
| `PATCH` | `/api/rides/{rideId}/accept` | Driver accepts ride | `200 OK` |
| `PATCH` | `/api/rides/{rideId}/start` | Driver starts trip | `200 OK` |
| `PATCH` | `/api/rides/{rideId}/complete` | Driver completes trip | `200 OK` |
| `PATCH` | `/api/rides/{rideId}/cancel` | Cancel ride booking | `200 OK` |
| `GET` | `/api/rides` | Admin list with filters | `200 OK` |

---

## 🛠️ Build and Run

### Run Locally:
```bash
mvn spring-boot:run
```

### Run Unit Tests:
```bash
mvn clean test
```

### Swagger UI Documentation:
Navigate to `http://localhost:8083/swagger-ui.html`
