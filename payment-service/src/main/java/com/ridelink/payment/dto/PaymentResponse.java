package com.ridelink.payment.dto;

import com.ridelink.payment.model.Payment;
import com.ridelink.payment.model.PaymentMethod;
import com.ridelink.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public response DTO for payment details with navigational hypermedia links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String id;
    private String paymentId;
    private String rideId;
    private String passengerId;
    private String driverId;
    private Double amount;
    private Double baseFare;
    private Double distanceFare;
    private Double serviceFee;
    private Double discount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String transactionReference;
    private String failureReason;
    private String refundReason;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;

    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();

    public static PaymentResponse fromEntity(Payment payment) {
        if (payment == null) {
            return null;
        }

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/payments/" + payment.getPaymentId());

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            links.put("receipt", "/api/payments/" + payment.getPaymentId() + "/receipt");
            links.put("refund", "/api/payments/" + payment.getPaymentId() + "/refund");
        }
        if (payment.getRideId() != null) {
            links.put("ride", "/api/rides/" + payment.getRideId());
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentId(payment.getPaymentId())
                .rideId(payment.getRideId())
                .passengerId(payment.getPassengerId())
                .driverId(payment.getDriverId())
                .amount(payment.getAmount())
                .baseFare(payment.getBaseFare())
                .distanceFare(payment.getDistanceFare())
                .serviceFee(payment.getServiceFee())
                .discount(payment.getDiscount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .transactionReference(payment.getTransactionReference())
                .failureReason(payment.getFailureReason())
                .refundReason(payment.getRefundReason())
                .paidAt(payment.getPaidAt())
                .refundedAt(payment.getRefundedAt())
                .createdAt(payment.getCreatedAt())
                .links(links)
                .build();
    }
}
