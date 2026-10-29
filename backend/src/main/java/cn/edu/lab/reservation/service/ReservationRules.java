package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.ReservationStatus;
import cn.edu.lab.reservation.entity.Reservation;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;

public final class ReservationRules {
  public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

  private ReservationRules() {}

  public static LocalDateTime now(Clock clock) {
    return LocalDateTime.ofInstant(clock.instant(), OFFSET)
        .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  public static String effective(Reservation r, LocalDateTime now) {
    return "PENDING".equals(r.status) && !r.startTime.isAfter(now) ? "EXPIRED" : r.status;
  }

  public static QueryWrapper<Reservation> active(LocalDateTime now) {
    return new QueryWrapper<Reservation>()
        .and(
            w ->
                w.eq("status", "APPROVED")
                    .gt("end_time", now)
                    .or()
                    .eq("status", "PENDING")
                    .gt("start_time", now));
  }

  public static void status(
      QueryWrapper<Reservation> q, ReservationStatus status, LocalDateTime now) {
    if (status == null) return;
    if (status == ReservationStatus.PENDING) q.eq("status", "PENDING").gt("start_time", now);
    else if (status == ReservationStatus.EXPIRED)
      q.and(w -> w.eq("status", "EXPIRED").or().eq("status", "PENDING").le("start_time", now));
    else q.eq("status", status.name());
  }

  public static void dates(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to))
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
  }
}
