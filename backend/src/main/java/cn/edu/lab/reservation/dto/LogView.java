package cn.edu.lab.reservation.dto;

import cn.edu.lab.reservation.entity.ReservationLog;
import cn.edu.lab.reservation.service.ReservationRules;
import java.time.OffsetDateTime;

public record LogView(
    String id,
    String operatorId,
    String action,
    String fromStatus,
    String toStatus,
    String reason,
    OffsetDateTime createdAt) {
  public static LogView from(ReservationLog l) {
    return new LogView(
        l.id.toString(),
        l.operatorId == null ? null : l.operatorId.toString(),
        l.action,
        l.fromStatus,
        l.toStatus,
        l.reason,
        l.createdAt.atOffset(ReservationRules.OFFSET));
  }
}
