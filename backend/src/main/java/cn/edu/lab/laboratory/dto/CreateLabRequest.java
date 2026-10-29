package cn.edu.lab.laboratory.dto;

import jakarta.validation.constraints.*;

public record CreateLabRequest(
    @NotBlank @Size(max = 32) @Pattern(regexp = "\\S(?:.*\\S)?") String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 200) String location,
    @Size(max = 1000) String description) {}
