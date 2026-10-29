package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.laboratory.service.ResourceAccess;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.entity.*;
import cn.edu.lab.reservation.mapper.*;
import cn.edu.lab.security.ActorAccess;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.*;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTransitionService {
  private final ActorAccess actors;
  private final ResourceAccess access;
  private final ReservationLocks locks;
  private final ReservationMapper rows;
  private final ReservationLogMapper logs;
  private final ReservationAudit audit;
  private final ReservationQueryService queries;
  private final Clock clock;

  public ReservationTransitionService(
      ActorAccess actors,
      ResourceAccess access,
      ReservationLocks locks,
      ReservationMapper rows,
      ReservationLogMapper logs,
      ReservationAudit audit,
      ReservationQueryService queries,
      Clock clock) {
    this.actors = actors;
    this.access = access;
    this.locks = locks;
    this.rows = rows;
    this.logs = logs;
    this.audit = audit;
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional
  public ReservationView change(long id, ReservationAction action, String reason) {
    var actor = actors.current();
    boolean cancel = action == ReservationAction.CANCEL;
    if (action == ReservationAction.CREATE
        || action == ReservationAction.EXPIRE
        || !(cancel ? "STUDENT" : "LAB_ADMIN").equals(actor.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    var locked = locks.existing(id);
    var r = locked.reservation();
    if (cancel) {
      if (!actor.id().equals(r.studentId.toString()))
        throw new BusinessException(ErrorCode.NOT_FOUND);
    } else access.writable(actor, locked.lab());
    String normalized = reason == null || reason.isBlank() ? null : reason.strip();
    if (normalized != null && normalized.length() > 500
        || (action == ReservationAction.REJECT || action == ReservationAction.REVOKE)
            && normalized == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    var latest =
        logs.selectOne(
            new QueryWrapper<ReservationLog>()
                .eq("reservation_id", id)
                .orderByDesc("created_at", "id")
                .last("LIMIT 1"));
    if (latest != null
        && latest.action.equals(action.name())
        && Objects.equals(latest.operatorId, Long.parseLong(actor.id()))
        && Objects.equals(latest.reason, normalized)) return queries.view(r, true);
    var target = ReservationLifecycle.target(r, action, ReservationRules.now(clock));
    if (action == ReservationAction.APPROVE
        && (locked.lab().enabled != 1 || !"AVAILABLE".equals(locked.equipment().status)))
      throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE);
    update(r, target, Long.parseLong(actor.id()), action, normalized);
    return queries.view(r, true);
  }

  @Transactional
  public boolean expire(long id) {
    var locked = locks.existing(id);
    var r = locked.reservation();
    var now = ReservationRules.now(clock);
    if (!"PENDING".equals(r.status) || r.startTime.isAfter(now)) return false;
    update(
        r,
        ReservationLifecycle.target(r, ReservationAction.EXPIRE, now),
        null,
        ReservationAction.EXPIRE,
        null);
    return true;
  }

  private void update(
      Reservation r,
      ReservationStatus target,
      Long operator,
      ReservationAction action,
      String reason) {
    String old = r.status;
    var now = ReservationRules.now(clock);
    int changed =
        rows.update(
            null,
            new UpdateWrapper<Reservation>()
                .eq("id", r.id)
                .eq("status", old)
                .set("status", target.name())
                .set("updated_at", now));
    if (changed != 1) throw new BusinessException(ErrorCode.INVALID_STATE);
    r.status = target.name();
    r.updatedAt = now;
    audit.append(r, operator, action, old, reason);
  }
}
