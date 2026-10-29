package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.mapper.ReservationMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class ReservationResourceStateGuard implements ResourceStateGuard {
  private final ReservationMapper rows;
  private final Clock clock;

  public ReservationResourceStateGuard(ReservationMapper rows, Clock clock) {
    this.rows = rows;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void beforeLabDisabled(long labId, List<Long> equipmentIds) {
    if (equipmentIds.isEmpty()) return;
    if (rows.selectCount(
            ReservationRules.active(ReservationRules.now(clock)).in("equipment_id", equipmentIds))
        > 0) throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void beforeEquipmentUnavailable(long equipmentId) {
    if (rows.selectCount(
            ReservationRules.active(ReservationRules.now(clock)).eq("equipment_id", equipmentId))
        > 0) throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE);
  }
}
