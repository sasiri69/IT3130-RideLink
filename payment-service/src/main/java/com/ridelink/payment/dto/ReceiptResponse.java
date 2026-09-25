package com.ridelink.payment.dto;

import com.ridelink.payment.model.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detailed receipt representation returned upon payment completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

    private String receiptNumber;
    private String paymentId;
    private String rideId;
    private String passengerId;
    private String driverId;
    private Double baseFare;
    private Double distanceFare;
    private Double serviceFee;
    private Double discount;
    private Double totalPaid;
    private String currency;
    private PaymentMethod paymentMethod;
    private String transactionReference;
    private LocalDateTime issuedAt;
    private String digitalSignature;

    @Builder.Default
    private Map<String, String> links = new LinkedHashMap<>();
}
