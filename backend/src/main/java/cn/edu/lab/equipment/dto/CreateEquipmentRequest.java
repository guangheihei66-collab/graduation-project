package cn.edu.lab.equipment.dto;

import jakarta.validation.constraints.*;

public record CreateEquipmentRequest(
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String labId,
    @NotBlank @Size(max = 64) @Pattern(regexp = "\\S(?:.*\\S)?") String assetNo,
    @NotBlank @Size(max = 100) String name,
    @NotNull EquipmentCategory category,
    @Size(max = 100) String model,
    @NotBlank @Size(max = 200) String location,
    @Size(max = 1000) String description) {}
