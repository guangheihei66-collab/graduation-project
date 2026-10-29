package cn.edu.lab.statistics.service;

import cn.edu.lab.common.*;
import cn.edu.lab.laboratory.service.ResourceAccess;
import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.reservation.service.ReservationRules;
import cn.edu.lab.security.ActorAccess;
import cn.edu.lab.statistics.dto.StatisticsView;
import cn.edu.lab.statistics.mapper.StatisticsMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
  private final StatisticsMapper mapper;
  private final ActorAccess actors;
  private final ResourceAccess access;
  private final Clock clock;

  public StatisticsService(
      StatisticsMapper mapper, ActorAccess actors, ResourceAccess access, Clock clock) {
    this.mapper = mapper;
    this.actors = actors;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public StatisticsView overview(Long labId, LocalDate from, LocalDate to) {
    var actor = actors.current();
    if (!List.of("LAB_ADMIN", "SYS_ADMIN").contains(actor.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 90)
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    if (labId != null) access.readable(actor, access.lab(labId, false));
    var q =
        new QueryWrapper<Reservation>()
            .ge("reservation.created_at", from.atStartOfDay())
            .lt("reservation.created_at", to.plusDays(1).atStartOfDay());
    if (labId != null)
      q.exists(
          "SELECT 1 FROM equipment eq WHERE eq.id=reservation.equipment_id AND eq.lab_id={0}",
          labId);
    if ("LAB_ADMIN".equals(actor.role()))
      q.exists(
          "SELECT 1 FROM equipment eq JOIN lab_admin a ON a.lab_id=eq.lab_id WHERE"
              + " eq.id=reservation.equipment_id AND a.user_id={0}",
          Long.parseLong(actor.id()));
    var totals = mapper.totals(q, ReservationRules.now(clock));
    var counts =
        mapper.daily(q).stream()
            .collect(
                Collectors.toMap(
                    StatisticsView.DailyPoint::date, StatisticsView.DailyPoint::count));
    var trend = new ArrayList<StatisticsView.DailyPoint>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1))
      trend.add(new StatisticsView.DailyPoint(day, counts.getOrDefault(day, 0L)));
    q.in("reservation.status", List.of("APPROVED", "COMPLETED"));
    return new StatisticsView(
        totals.totalCount(), totals.pendingCount(), totals.approvedCount(), trend, mapper.top(q));
  }
}
