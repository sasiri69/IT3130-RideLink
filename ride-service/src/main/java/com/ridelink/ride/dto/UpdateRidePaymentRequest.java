package com.ridelink.ride.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for updating payment status on a completed ride booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRidePaymentRequest {

    @NotBlank(message = "Payment ID is required")
    private String paymentId;

    @NotBlank(message = "Payment status is required (e.g. PAID)")
    private String paymentStatus;
}
