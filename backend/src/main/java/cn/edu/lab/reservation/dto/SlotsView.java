package cn.edu.lab.reservation.dto;

import java.time.*;
import java.util.List;

public record SlotsView(LocalDate date, String openTime, String closeTime, List<Slot> slots) {
  public record Slot(OffsetDateTime startTime, OffsetDateTime endTime, String status) {}
}
