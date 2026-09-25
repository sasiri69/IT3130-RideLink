package com.ridelink.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.PaymentResponse;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.exception.DuplicatePaymentException;
import com.ridelink.payment.exception.PaymentNotFoundException;
import com.ridelink.payment.exception.PaymentProcessingException;
import com.ridelink.payment.model.PaymentMethod;
import com.ridelink.payment.model.PaymentStatus;
import com.ridelink.payment.security.JwtService;
import com.ridelink.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/payments should return 201 Created on successful payment")
    void testProcessPayment_Success() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .paymentId("PAY-12345678")
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .status(PaymentStatus.COMPLETED)
                .currency("LKR")
                .build();

        when(paymentService.processPayment(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/payments/PAY-12345678"))
                .andExpect(jsonPath("$.paymentId").value("PAY-12345678"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/payments should return 409 Conflict on duplicate ride payment")
    void testProcessPayment_DuplicatePayment() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CASH)
                .build();

        when(paymentService.processPayment(any(), any()))
                .thenThrow(new DuplicatePaymentException("Payment has already been completed for ride ID: RIDE-A1B2C3D4"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate Payment"));
    }

    @Test
    @DisplayName("POST /api/payments should return 402 Payment Required on card decline")
    void testProcessPayment_DeclinedCard() throws Exception {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .simulatedCardNumber("4111222233330000")
                .build();

        when(paymentService.processPayment(any(), any()))
                .thenThrow(new PaymentProcessingException("Payment transaction declined by bank."));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Payment Declined"));
    }

    @Test
    @DisplayName("GET /api/payments/{id} should return 200 OK when payment exists")
    void testGetPaymentById_Success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId("PAY-12345678")
                .amount(1500.0)
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentById("PAY-12345678")).thenReturn(response);

        mockMvc.perform(get("/api/payments/PAY-12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("PAY-12345678"));
    }

    @Test
    @DisplayName("GET /api/payments/{id} should return 404 Not Found when payment missing")
    void testGetPaymentById_NotFound() throws Exception {
        when(paymentService.getPaymentById("PAY-UNKNOWN"))
                .thenThrow(new PaymentNotFoundException("Payment with ID 'PAY-UNKNOWN' does not exist."));

        mockMvc.perform(get("/api/payments/PAY-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Payment Not Found"));
    }

    @Test
    @DisplayName("GET /api/payments/{id}/receipt should return 200 OK with digital receipt")
    void testGetReceipt_Success() throws Exception {
        ReceiptResponse receipt = ReceiptResponse.builder()
                .receiptNumber("REC-12345678")
                .paymentId("PAY-12345678")
                .totalPaid(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .digitalSignature("SIG-DEMO-SIGNATURE")
                .issuedAt(LocalDateTime.now())
                .build();

        when(paymentService.getReceiptByPaymentId("PAY-12345678")).thenReturn(receipt);

        mockMvc.perform(get("/api/payments/PAY-12345678/receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("REC-12345678"))
                .andExpect(jsonPath("$.digitalSignature").value("SIG-DEMO-SIGNATURE"));
    }

    // ─── Validation Tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments - Negative Scenario: Returns 400 when required fields are missing")
    void testProcessPayment_ValidationFailure_MissingFields() throws Exception {
        PaymentRequest invalid = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                // passengerId is missing - required field
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/payments - Negative Scenario: Returns 400 when amount is negative")
    void testProcessPayment_ValidationFailure_NegativeAmount() throws Exception {
        PaymentRequest invalid = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(-100.0) // invalid: must be positive
                .paymentMethod(PaymentMethod.CASH)
                .build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // ─── Refund Tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/payments/{id}/refund - Returns 200 OK on successful refund (Admin)")
    void testRefundPayment_Success() throws Exception {
        com.ridelink.payment.dto.RefundRequest refundReq = new com.ridelink.payment.dto.RefundRequest("Duplicate charge");

        PaymentResponse refunded = PaymentResponse.builder()
                .paymentId("PAY-12345678")
                .status(PaymentStatus.REFUNDED)
                .refundReason("Duplicate charge")
                .build();

        when(paymentService.refundPayment(eq("PAY-12345678"), any())).thenReturn(refunded);

        mockMvc.perform(patch("/api/payments/PAY-12345678/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundReason").value("Duplicate charge"));
    }

    // ─── Retrieval Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/ride/{rideId} - Returns 200 OK with payment for a ride")
    void testGetPaymentByRideId_Success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId("PAY-12345678")
                .rideId("RIDE-A1B2C3D4")
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentService.getPaymentByRideId("RIDE-A1B2C3D4")).thenReturn(response);

        mockMvc.perform(get("/api/payments/ride/RIDE-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("PAY-12345678"))
                .andExpect(jsonPath("$.rideId").value("RIDE-A1B2C3D4"));
    }

    @Test
    @DisplayName("GET /api/payments/passenger/{passengerId} - Returns 200 OK with payment history")
    void testGetPaymentsByPassenger_Success() throws Exception {
        java.util.List<PaymentResponse> list = java.util.List.of(
                PaymentResponse.builder().paymentId("PAY-111").passengerId("PASS-101").amount(1200.0).build(),
                PaymentResponse.builder().paymentId("PAY-222").passengerId("PASS-101").amount(800.0).build()
        );

        when(paymentService.getPaymentsByPassenger("PASS-101")).thenReturn(list);

        mockMvc.perform(get("/api/payments/passenger/PASS-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].paymentId").value("PAY-111"));
    }
}
