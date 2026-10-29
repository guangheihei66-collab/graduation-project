package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.entity.Reservation;
import java.time.LocalDateTime;

public final class ReservationLifecycle {
  private ReservationLifecycle() {}

  public static ReservationStatus target(
      Reservation r, ReservationAction action, LocalDateTime now) {
    var old = ReservationStatus.valueOf(r.status);
    boolean before = now.isBefore(r.startTime);
    return switch (action) {
      case APPROVE -> {
        require(old == ReservationStatus.PENDING && before);
        yield ReservationStatus.APPROVED;
      }
      case REJECT -> {
        require(old == ReservationStatus.PENDING && before);
        yield ReservationStatus.REJECTED;
      }
      case CANCEL -> {
        require((old == ReservationStatus.PENDING || old == ReservationStatus.APPROVED) && before);
        yield ReservationStatus.CANCELLED;
      }
      case REVOKE -> {
        require(old == ReservationStatus.APPROVED && before);
        yield ReservationStatus.CANCELLED;
      }
      case COMPLETE -> {
        require(old == ReservationStatus.APPROVED && !now.isBefore(r.endTime));
        yield ReservationStatus.COMPLETED;
      }
      case EXPIRE -> {
        require(old == ReservationStatus.PENDING && !before);
        yield ReservationStatus.EXPIRED;
      }
      default -> throw new BusinessException(ErrorCode.INVALID_STATE);
    };
  }

  private static void require(boolean allowed) {
    if (!allowed) throw new BusinessException(ErrorCode.INVALID_STATE);
  }
}
