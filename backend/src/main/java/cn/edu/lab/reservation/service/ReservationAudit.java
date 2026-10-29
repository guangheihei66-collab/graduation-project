package cn.edu.lab.reservation.service;

import cn.edu.lab.reservation.dto.ReservationAction;
import cn.edu.lab.reservation.entity.*;
import cn.edu.lab.reservation.mapper.ReservationLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class ReservationAudit {
  private final ReservationLogMapper logs;

  public ReservationAudit(ReservationLogMapper logs) {
    this.logs = logs;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(
      Reservation r, Long operator, ReservationAction action, String from, String reason) {
    var l = new ReservationLog();
    l.reservationId = r.id;
    l.operatorId = operator;
    l.action = action.name();
    l.fromStatus = from;
    l.toStatus = r.status;
    l.reason = reason;
    logs.insert(l);
  }
}
