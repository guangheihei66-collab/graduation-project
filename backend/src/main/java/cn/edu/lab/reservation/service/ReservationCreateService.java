package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.reservation.mapper.ReservationMapper;
import cn.edu.lab.security.ActorAccess;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationCreateService {
  private final ActorAccess actors;
  private final ReservationLocks locks;
  private final ReservationMapper rows;
  private final ReservationAudit audit;
  private final ReservationQueryService queries;
  private final Clock clock;

  public ReservationCreateService(
      ActorAccess actors,
      ReservationLocks locks,
      ReservationMapper rows,
      ReservationAudit audit,
      ReservationQueryService queries,
      Clock clock) {
    this.actors = actors;
    this.locks = locks;
    this.rows = rows;
    this.audit = audit;
    this.queries = queries;
    this.clock = clock;
  }

  public record Created(boolean replay, ReservationView reservation) {}

  @Transactional
  public Created create(CreateReservationRequest req, String key) {
    var actor = actors.current();
    if (!"STUDENT".equals(actor.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
    var input = ReservationInput.parse(req, key);
    var locked = locks.create(input.equipmentId(), Long.parseLong(actor.id()));
    if (locked.student() == null
        || locked.student().enabled != 1
        || !"STUDENT".equals(locked.student().role))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    var previous =
        rows.selectOne(
            new QueryWrapper<Reservation>()
                .eq("student_id", locked.student().id)
                .eq("idempotency_key", input.key()));
    if (previous != null) {
      if (!previous.requestHash.equals(input.hash()))
        throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
      return new Created(true, queries.view(previous, false));
    }
    if (locked.lab().enabled != 1 || !"AVAILABLE".equals(locked.equipment().status))
      throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE);
    var now = ReservationRules.now(clock);
    input.validateTime(now);
    if (rows.selectCount(ReservationRules.active(now).eq("student_id", locked.student().id)) >= 3)
      throw new BusinessException(ErrorCode.QUOTA_EXCEEDED);
    var conflict =
        ReservationRules.active(now)
            .eq("equipment_id", input.equipmentId())
            .lt("start_time", input.end())
            .gt("end_time", input.start());
    if (rows.selectCount(conflict) > 0) throw new BusinessException(ErrorCode.TIME_CONFLICT);
    var r = new Reservation();
    r.equipmentId = input.equipmentId();
    r.studentId = locked.student().id;
    r.startTime = input.start();
    r.endTime = input.end();
    r.purpose = input.purpose();
    r.status = ReservationStatus.PENDING.name();
    r.idempotencyKey = input.key();
    r.requestHash = input.hash();
    rows.insert(r);
    audit.append(r, r.studentId, ReservationAction.CREATE, null, null);
    return new Created(false, queries.view(r, false));
  }
}
