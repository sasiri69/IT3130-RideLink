package com.ridelink.payment.service;

import com.ridelink.payment.client.AccountServiceClient;
import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.client.dto.RideClientResponse;
import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.PaymentResponse;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.dto.RefundRequest;
import com.ridelink.payment.exception.*;
import com.ridelink.payment.model.Payment;
import com.ridelink.payment.model.PaymentMethod;
import com.ridelink.payment.model.PaymentStatus;
import com.ridelink.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service managing payment recording, verification, simulated processing, and receipt generation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountServiceClient accountServiceClient;
    private final RideServiceClient rideServiceClient;

    /**
     * Processes and records a simulated payment for a ride booking.
     *
     * @param request Payment payload
     * @param authToken Optional user authentication token
     * @return Created payment response in COMPLETED state
     */
    public PaymentResponse processPayment(PaymentRequest request, String authToken) {
        String rideId = request.getRideId().trim();
        String passengerId = request.getPassengerId().trim();
        log.info("Processing payment for ride: {}, passenger: {}, amount: {}", rideId, passengerId, request.getAmount());

        // 1. Fast-fail: Check for duplicate payment BEFORE making expensive inter-service calls
        if (paymentRepository.existsByRideIdAndStatus(rideId, PaymentStatus.COMPLETED)) {
            throw new DuplicatePaymentException("Payment has already been completed for ride ID: " + rideId);
        }

        // 2. Verify passenger account exists with Account Service
        accountServiceClient.getPassengerById(passengerId, authToken);

        // 3. Verify ride exists with Ride Management Service
        RideClientResponse ride = rideServiceClient.getRideById(rideId, authToken);

        // 4. Check for simulated card failure (e.g. card ending in '0000')
        if (request.getPaymentMethod() == PaymentMethod.CARD
                && request.getSimulatedCardNumber() != null
                && request.getSimulatedCardNumber().trim().endsWith("0000")) {
            
            // Record failed payment attempt for auditing
            String failedPayId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Payment failedPayment = Payment.builder()
                    .paymentId(failedPayId)
                    .rideId(rideId)
                    .passengerId(passengerId)
                    .driverId(ride != null ? ride.getDriverId() : null)
                    .amount(request.getAmount())
                    .currency("LKR")
                    .paymentMethod(request.getPaymentMethod())
                    .status(PaymentStatus.FAILED)
                    .failureReason("Simulated card decline: Card ending in 0000 was rejected by the issuing bank.")
                    .createdAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(failedPayment);

            throw new PaymentProcessingException("Payment transaction declined by bank for card ending in 0000.");
        }

        // 5. Generate identifiers
        String publicPaymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        double totalAmount = request.getAmount();
        double baseFare = Math.round((totalAmount * 0.20) * 100.0) / 100.0;
        double distanceFare = Math.round((totalAmount * 0.75) * 100.0) / 100.0;
        double serviceFee = Math.round((totalAmount * 0.05) * 100.0) / 100.0;

        Payment payment = Payment.builder()
                .paymentId(publicPaymentId)
                .rideId(rideId)
                .passengerId(passengerId)
                .driverId(ride != null ? ride.getDriverId() : null)
                .amount(totalAmount)
                .baseFare(baseFare)
                .distanceFare(distanceFare)
                .serviceFee(serviceFee)
                .discount(0.0)
                .currency("LKR")
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.COMPLETED)
                .transactionReference(txnRef)
                .paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment successfully processed and saved with ID: {}", saved.getPaymentId());
        return PaymentResponse.fromEntity(saved);
    }

    /**
     * Retrieves payment by public payment ID.
     */
    public PaymentResponse getPaymentById(String paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Retrieves payment for a specific ride booking.
     */
    public PaymentResponse getPaymentByRideId(String rideId) {
        Payment payment = paymentRepository.findByRideId(rideId.trim())
                .orElseThrow(() -> new PaymentNotFoundException("Payment record not found for ride ID: " + rideId));
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Retrieves payment history for a passenger.
     */
    public List<PaymentResponse> getPaymentsByPassenger(String passengerId) {
        return paymentRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId.trim())
                .stream()
                .map(PaymentResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves all payments with optional status filtering.
     */
    public List<PaymentResponse> getAllPayments(PaymentStatus status) {
        List<Payment> list = (status != null)
                ? paymentRepository.findByStatus(status)
                : paymentRepository.findAll();
        return list.stream().map(PaymentResponse::fromEntity).toList();
    }

    /**
     * Generates a formal digital receipt for a completed payment.
     */
    public ReceiptResponse getReceiptByPaymentId(String paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ReceiptNotFoundException("Receipt unavailable: Payment is not completed (current status: " + payment.getStatus() + ").");
        }

        String receiptNumber = "REC-" + payment.getPaymentId().replace("PAY-", "");
        String digitalSignature = generateDigitalSignature(payment);

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/payments/" + payment.getPaymentId() + "/receipt");
        links.put("payment", "/api/payments/" + payment.getPaymentId());
        links.put("ride", "/api/rides/" + payment.getRideId());

        return ReceiptResponse.builder()
                .receiptNumber(receiptNumber)
                .paymentId(payment.getPaymentId())
                .rideId(payment.getRideId())
                .passengerId(payment.getPassengerId())
                .driverId(payment.getDriverId())
                .baseFare(payment.getBaseFare())
                .distanceFare(payment.getDistanceFare())
                .serviceFee(payment.getServiceFee())
                .discount(payment.getDiscount())
                .totalPaid(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .transactionReference(payment.getTransactionReference())
                .issuedAt(payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now())
                .digitalSignature(digitalSignature)
                .links(links)
                .build();
    }

    /**
     * Processes a simulated payment refund.
     */
    public PaymentResponse refundPayment(String paymentId, RefundRequest request) {
        Payment payment = findPaymentOrThrow(paymentId);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentRequestException("Cannot refund payment in status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundReason(request.getReason());
        payment.setRefundedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} successfully refunded", saved.getPaymentId());
        return PaymentResponse.fromEntity(saved);
    }

    private Payment findPaymentOrThrow(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId.trim())
                .orElseThrow(() -> new PaymentNotFoundException("Payment with ID '" + paymentId + "' does not exist."));
    }

    private String generateDigitalSignature(Payment payment) {
        try {
            String raw = payment.getPaymentId() + ":" + payment.getRideId() + ":" + payment.getAmount() + ":" + payment.getTransactionReference();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "SIG-" + UUID.randomUUID().toString();
        }
    }
}
