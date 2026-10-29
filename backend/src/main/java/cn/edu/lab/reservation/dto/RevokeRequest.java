package cn.edu.lab.reservation.dto;

import jakarta.validation.constraints.*;

public record RevokeRequest(@NotBlank @Size(max = 500) String reason) {}
