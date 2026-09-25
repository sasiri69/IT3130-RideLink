package com.ridelink.payment.controller;

import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.PaymentResponse;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.dto.RefundRequest;
import com.ridelink.payment.model.PaymentStatus;
import com.ridelink.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller exposing endpoints for payment processing, receipt retrieval, and transaction history.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management Service", description = "Endpoints for ride payment processing, receipts, and refund operations")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Process a simulated ride payment", description = "Records a simulated payment, verifies passenger and ride, and issues transaction reference.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failure or invalid payment state"),
            @ApiResponse(responseCode = "402", description = "Payment declined (simulated card failure)"),
            @ApiResponse(responseCode = "409", description = "Duplicate payment - ride already paid"),
            @ApiResponse(responseCode = "503", description = "Downstream service unavailable")
    })
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        log.info("REST POST /api/payments for ride: {}", request.getRideId());
        PaymentResponse response = paymentService.processPayment(request, token);
        return ResponseEntity.created(URI.create("/api/payments/" + response.getPaymentId())).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment record found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentResponse> getPaymentById(
            @Parameter(description = "Public payment ID (e.g. PAY-XXXX)") @PathVariable String paymentId
    ) {
        log.info("REST GET /api/payments/{}", paymentId);
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ride/{rideId}")
    @Operation(summary = "Get payment record for a ride")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment record found"),
            @ApiResponse(responseCode = "404", description = "Payment not found for ride")
    })
    public ResponseEntity<PaymentResponse> getPaymentByRideId(@PathVariable String rideId) {
        log.info("REST GET /api/payments/ride/{}", rideId);
        PaymentResponse response = paymentService.getPaymentByRideId(rideId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/passenger/{passengerId}")
    @Operation(summary = "Get payment history for a passenger")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment history returned")
    })
    public ResponseEntity<List<PaymentResponse>> getPaymentsByPassenger(@PathVariable String passengerId) {
        log.info("REST GET /api/payments/passenger/{}", passengerId);
        List<PaymentResponse> list = paymentService.getPaymentsByPassenger(passengerId);
        return ResponseEntity.ok(list);
    }

    @GetMapping
    @Operation(summary = "List all payments with optional status filter (Admin)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of payments returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<List<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status
    ) {
        log.info("REST GET /api/payments?status={}", status);
        List<PaymentResponse> list = paymentService.getAllPayments(status);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{paymentId}/receipt")
    @Operation(summary = "Retrieve digital payment receipt", description = "Generates and returns itemized receipt with digital security signature.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt generated"),
            @ApiResponse(responseCode = "404", description = "Payment not found or payment not completed")
    })
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable String paymentId) {
        log.info("REST GET /api/payments/{}/receipt", paymentId);
        ReceiptResponse response = paymentService.getReceiptByPaymentId(paymentId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a payment transaction (Admin or Operator)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment successfully refunded"),
            @ApiResponse(responseCode = "400", description = "Cannot refund uncompleted payment"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody RefundRequest request
    ) {
        log.info("REST PATCH /api/payments/{}/refund", paymentId);
        PaymentResponse response = paymentService.refundPayment(paymentId, request);
        return ResponseEntity.ok(response);
    }
}
