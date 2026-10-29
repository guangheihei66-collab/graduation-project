package cn.edu.lab.reservation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReservationView(
    String id,
    String equipmentId,
    String equipmentName,
    String labId,
    String studentId,
    String studentName,
    String username,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    String purpose,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<LogView> logs) {}
