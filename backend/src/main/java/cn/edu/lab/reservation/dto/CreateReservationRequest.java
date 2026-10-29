package cn.edu.lab.reservation.dto;

import jakarta.validation.constraints.*;

public record CreateReservationRequest(
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String equipmentId,
    @NotBlank String startTime,
    @NotBlank String endTime,
    @NotBlank @Size(max = 500) String purpose) {}
