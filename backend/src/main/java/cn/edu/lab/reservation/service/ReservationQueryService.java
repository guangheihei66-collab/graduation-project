package cn.edu.lab.reservation.service;

import cn.edu.lab.auth.dto.UserView;
import cn.edu.lab.common.*;
import cn.edu.lab.equipment.mapper.EquipmentMapper;
import cn.edu.lab.laboratory.service.ResourceAccess;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.entity.*;
import cn.edu.lab.reservation.mapper.*;
import cn.edu.lab.security.ActorAccess;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReservationQueryService {
  private final ReservationMapper rows;
  private final ReservationLogMapper logs;
  private final EquipmentMapper equipment;
  private final SysUserMapper users;
  private final ActorAccess actors;
  private final ResourceAccess access;
  private final Clock clock;

  public ReservationQueryService(
      ReservationMapper rows,
      ReservationLogMapper logs,
      EquipmentMapper equipment,
      SysUserMapper users,
      ActorAccess actors,
      ResourceAccess access,
      Clock clock) {
    this.rows = rows;
    this.logs = logs;
    this.equipment = equipment;
    this.users = users;
    this.actors = actors;
    this.access = access;
    this.clock = clock;
  }

  public PageResult<ReservationView> list(
      boolean mine,
      PageQuery page,
      Long labId,
      Long equipmentId,
      ReservationStatus status,
      LocalDate from,
      LocalDate to) {
    var actor = actors.current();
    if (mine && !"STUDENT".equals(actor.role()) || !mine && "STUDENT".equals(actor.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    ReservationRules.dates(from, to);
    var q = new QueryWrapper<Reservation>();
    if (mine) q.eq("student_id", Long.parseLong(actor.id()));
    else if ("LAB_ADMIN".equals(actor.role()))
      q.exists(
          "SELECT 1 FROM equipment e JOIN lab_admin a ON a.lab_id=e.lab_id WHERE"
              + " e.id=reservation.equipment_id AND a.user_id={0}",
          Long.parseLong(actor.id()));
    if (labId != null) {
      if (!mine) access.readable(actor, access.lab(labId, false));
      q.exists(
          "SELECT 1 FROM equipment e WHERE e.id=reservation.equipment_id AND e.lab_id={0}", labId);
    }
    if (equipmentId != null) q.eq("equipment_id", equipmentId);
    ReservationRules.status(q, status, ReservationRules.now(clock));
    if (from != null) q.ge("start_time", from.atStartOfDay());
    if (to != null) q.lt("start_time", to.plusDays(1).atStartOfDay());
    q.orderByDesc("created_at", "id");
    var p = rows.selectPage(page.<Reservation>toPage(), q);
    return new PageResult<>(
        p.getRecords().stream().map(r -> view(r, false)).toList(),
        p.getTotal(),
        p.getCurrent(),
        p.getSize());
  }

  public ReservationView detail(long id) {
    var actor = actors.current();
    var r = rows.selectById(id);
    if (r == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    authorize(actor, r);
    return view(r, true);
  }

  public void authorize(UserView actor, Reservation r) {
    if ("STUDENT".equals(actor.role())) {
      if (!actor.id().equals(r.studentId.toString()))
        throw new BusinessException(ErrorCode.NOT_FOUND);
    } else if ("LAB_ADMIN".equals(actor.role()))
      access.readable(actor, access.lab(equipment.selectById(r.equipmentId).labId, false));
    else if (!"SYS_ADMIN".equals(actor.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  public ReservationView view(Reservation r, boolean detail) {
    var e = equipment.selectById(r.equipmentId);
    var u = users.selectById(r.studentId);
    var history =
        detail
            ? logs
                .selectList(
                    new QueryWrapper<ReservationLog>()
                        .eq("reservation_id", r.id)
                        .orderByAsc("created_at", "id"))
                .stream()
                .map(LogView::from)
                .toList()
            : List.<LogView>of();
    return new ReservationView(
        r.id.toString(),
        r.equipmentId.toString(),
        e.name,
        e.labId.toString(),
        r.studentId.toString(),
        u.realName,
        u.username,
        r.startTime.atOffset(ReservationRules.OFFSET),
        r.endTime.atOffset(ReservationRules.OFFSET),
        r.purpose,
        ReservationRules.effective(r, ReservationRules.now(clock)),
        r.createdAt.atOffset(ReservationRules.OFFSET),
        r.updatedAt.atOffset(ReservationRules.OFFSET),
        history);
  }

  public SlotsView slots(long id, LocalDate date) {
    var actor = actors.current();
    var e = equipment.selectById(id);
    if (e == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    access.readable(actor, access.lab(e.labId, false));
    if ("STUDENT".equals(actor.role()) && !"AVAILABLE".equals(e.status))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    var q =
        ReservationRules.active(ReservationRules.now(clock))
            .eq("equipment_id", id)
            .lt("start_time", date.plusDays(1).atStartOfDay())
            .gt("end_time", date.atStartOfDay())
            .orderByAsc("start_time", "id");
    return new SlotsView(
        date,
        "08:00",
        "22:00",
        rows.selectList(q).stream()
            .map(
                r ->
                    new SlotsView.Slot(
                        r.startTime.atOffset(ReservationRules.OFFSET),
                        r.endTime.atOffset(ReservationRules.OFFSET),
                        r.status))
            .toList());
  }
}
