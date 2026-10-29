package cn.edu.lab.reservation.dto;

import jakarta.validation.constraints.Size;

public record ReasonRequest(@Size(max = 500) String reason) {}
