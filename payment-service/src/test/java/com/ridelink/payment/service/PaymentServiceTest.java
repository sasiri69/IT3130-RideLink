package com.ridelink.payment.service;

import com.ridelink.payment.client.AccountServiceClient;
import com.ridelink.payment.client.RideServiceClient;
import com.ridelink.payment.client.dto.AccountUserResponse;
import com.ridelink.payment.client.dto.RideClientResponse;
import com.ridelink.payment.dto.PaymentRequest;
import com.ridelink.payment.dto.PaymentResponse;
import com.ridelink.payment.dto.ReceiptResponse;
import com.ridelink.payment.dto.RefundRequest;
import com.ridelink.payment.exception.DuplicatePaymentException;
import com.ridelink.payment.exception.PaymentNotFoundException;
import com.ridelink.payment.exception.PaymentProcessingException;
import com.ridelink.payment.exception.ReceiptNotFoundException;
import com.ridelink.payment.model.Payment;
import com.ridelink.payment.model.PaymentMethod;
import com.ridelink.payment.model.PaymentStatus;
import com.ridelink.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private RideServiceClient rideServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = Payment.builder()
                .id("doc-123")
                .paymentId("PAY-12345678")
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .driverId("DRV-202")
                .amount(1500.0)
                .baseFare(300.0)
                .distanceFare(1125.0)
                .serviceFee(75.0)
                .discount(0.0)
                .currency("LKR")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.COMPLETED)
                .transactionReference("TXN-9988776655")
                .paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully process and record simulated payment")
    void testProcessPayment_Success() {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .simulatedCardNumber("4111222233334444")
                .build();

        when(accountServiceClient.getPassengerById(anyString(), any()))
                .thenReturn(AccountUserResponse.builder().id("PASS-101").status("ACTIVE").build());

        when(rideServiceClient.getRideById(anyString(), any()))
                .thenReturn(RideClientResponse.builder().rideId("RIDE-A1B2C3D4").driverId("DRV-202").status("COMPLETED").build());

        when(paymentRepository.existsByRideIdAndStatus(eq("RIDE-A1B2C3D4"), eq(PaymentStatus.COMPLETED)))
                .thenReturn(false);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request, "Bearer test-token");

        assertNotNull(response);
        assertEquals("RIDE-A1B2C3D4", response.getRideId());
        assertEquals("PASS-101", response.getPassengerId());
        assertEquals(1500.0, response.getAmount());
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertNotNull(response.getPaymentId());
        assertNotNull(response.getTransactionReference());
        assertNotNull(response.getLinks().get("receipt"));

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should throw DuplicatePaymentException when ride is already paid")
    void testProcessPayment_DuplicateRidePayment() {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CASH)
                .build();

        when(paymentRepository.existsByRideIdAndStatus(eq("RIDE-A1B2C3D4"), eq(PaymentStatus.COMPLETED)))
                .thenReturn(true);

        assertThrows(DuplicatePaymentException.class, () ->
                paymentService.processPayment(request, null)
        );

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should trigger PaymentProcessingException and save FAILED record on card decline")
    void testProcessPayment_DeclinedCard() {
        PaymentRequest request = PaymentRequest.builder()
                .rideId("RIDE-A1B2C3D4")
                .passengerId("PASS-101")
                .amount(1500.0)
                .paymentMethod(PaymentMethod.CARD)
                .simulatedCardNumber("4111222233330000") // ends in 0000 -> triggers decline
                .build();

        when(paymentRepository.existsByRideIdAndStatus(anyString(), any()))
                .thenReturn(false);

        assertThrows(PaymentProcessingException.class, () ->
                paymentService.processPayment(request, null)
        );

        // Verify that failed payment was audited
        verify(paymentRepository, times(1)).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("Should retrieve payment by public payment ID")
    void testGetPaymentById_Success() {
        when(paymentRepository.findByPaymentId("PAY-12345678"))
                .thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.getPaymentById("PAY-12345678");

        assertNotNull(response);
        assertEquals("PAY-12345678", response.getPaymentId());
        assertEquals(1500.0, response.getAmount());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment does not exist")
    void testGetPaymentById_NotFound() {
        when(paymentRepository.findByPaymentId("PAY-UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () ->
                paymentService.getPaymentById("PAY-UNKNOWN")
        );
    }

    @Test
    @DisplayName("Should generate itemized receipt with digital signature for completed payment")
    void testGetReceiptByPaymentId_Success() {
        when(paymentRepository.findByPaymentId("PAY-12345678"))
                .thenReturn(Optional.of(samplePayment));

        ReceiptResponse receipt = paymentService.getReceiptByPaymentId("PAY-12345678");

        assertNotNull(receipt);
        assertEquals("REC-12345678", receipt.getReceiptNumber());
        assertEquals("PAY-12345678", receipt.getPaymentId());
        assertEquals(1500.0, receipt.getTotalPaid());
        assertNotNull(receipt.getDigitalSignature());
        assertNotNull(receipt.getLinks().get("self"));
    }

    @Test
    @DisplayName("Should throw ReceiptNotFoundException when payment is not in COMPLETED status")
    void testGetReceiptByPaymentId_NotCompleted() {
        samplePayment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByPaymentId("PAY-12345678"))
                .thenReturn(Optional.of(samplePayment));

        assertThrows(ReceiptNotFoundException.class, () ->
                paymentService.getReceiptByPaymentId("PAY-12345678")
        );
    }

    @Test
    @DisplayName("Should successfully refund a completed payment")
    void testRefundPayment_Success() {
        when(paymentRepository.findByPaymentId("PAY-12345678"))
                .thenReturn(Optional.of(samplePayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundRequest refundRequest = new RefundRequest("Passenger duplicate deduction complaint");
        PaymentResponse response = paymentService.refundPayment("PAY-12345678", refundRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals("Passenger duplicate deduction complaint", response.getRefundReason());
        assertNotNull(response.getRefundedAt());
    }

    @Test
    @DisplayName("Should retrieve payment by ride ID")
    void testGetPaymentByRideId_Success() {
        when(paymentRepository.findByRideId("RIDE-A1B2C3D4"))
                .thenReturn(Optional.of(samplePayment));

        PaymentResponse response = paymentService.getPaymentByRideId("RIDE-A1B2C3D4");

        assertNotNull(response);
        assertEquals("RIDE-A1B2C3D4", response.getRideId());
        assertEquals("PAY-12345678", response.getPaymentId());
    }

    @Test
    @DisplayName("Should return empty list when passenger has no payment history")
    void testGetPaymentsByPassenger_EmptyList() {
        when(paymentRepository.findByPassengerIdOrderByCreatedAtDesc("PASS-999"))
                .thenReturn(java.util.List.of());

        java.util.List<PaymentResponse> result = paymentService.getPaymentsByPassenger("PASS-999");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw InvalidPaymentRequestException when refunding a non-completed payment")
    void testRefundPayment_NotCompleted_ThrowsException() {
        samplePayment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByPaymentId("PAY-12345678"))
                .thenReturn(Optional.of(samplePayment));

        assertThrows(com.ridelink.payment.exception.InvalidPaymentRequestException.class, () ->
                paymentService.refundPayment("PAY-12345678", new RefundRequest("Error"))
        );

        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
