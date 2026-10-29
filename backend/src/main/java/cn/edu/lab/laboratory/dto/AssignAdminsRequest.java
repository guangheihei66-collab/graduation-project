package cn.edu.lab.laboratory.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record AssignAdminsRequest(
    @NotNull @Size(max = 100)
        List<@NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String> userIds) {}
