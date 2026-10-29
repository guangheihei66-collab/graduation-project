package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.equipment.entity.Equipment;
import cn.edu.lab.equipment.mapper.EquipmentMapper;
import cn.edu.lab.laboratory.entity.Laboratory;
import cn.edu.lab.laboratory.service.ResourceAccess;
import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.reservation.mapper.ReservationMapper;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

@Component
public class ReservationLocks {
  private final EquipmentMapper equipment;
  private final ResourceAccess access;
  private final SysUserMapper users;
  private final ReservationMapper rows;

  public ReservationLocks(
      EquipmentMapper equipment,
      ResourceAccess access,
      SysUserMapper users,
      ReservationMapper rows) {
    this.equipment = equipment;
    this.access = access;
    this.users = users;
    this.rows = rows;
  }

  public record Locked(
      Laboratory lab, Equipment equipment, SysUser student, Reservation reservation) {}

  public Locked create(long equipmentId, long studentId) {
    var initial = equipment.selectById(equipmentId);
    if (initial == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    var lab = access.lab(initial.labId, true);
    var eq = equipment.lockById(equipmentId);
    var student = users.lockById(studentId);
    return new Locked(lab, eq, student, null);
  }

  public Locked existing(long id) {
    var initial = rows.selectById(id);
    if (initial == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    var locked = create(initial.equipmentId, initial.studentId);
    return new Locked(locked.lab(), locked.equipment(), locked.student(), rows.lockById(id));
  }
}
