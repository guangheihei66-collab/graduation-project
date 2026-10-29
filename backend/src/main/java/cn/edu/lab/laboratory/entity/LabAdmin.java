package cn.edu.lab.laboratory.entity;

import java.time.LocalDateTime;

public record LabAdmin(Long labId, Long userId, LocalDateTime createdAt) {}
