package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.reservation.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReservationRulesTest {
  CreateReservationRequest input(String start, String end, String purpose) {
    return new CreateReservationRequest("1", start, end, purpose);
  }

  @Test
  void canonicalHashNormalizesTimesPurposeAndKey() {
    String key = UUID.randomUUID().toString();
    var first =
        ReservationInput.parse(
            input("2026-11-03T09:00+08:00", "2026-11-03T10:00+08:00", " 用途 "), key);
    var second =
        ReservationInput.parse(
            input("2026-11-03T09:00:00+08:00", "2026-11-03T10:00:00+08:00", "用途"),
            key.toUpperCase());
    assertEquals(first, second);
    assertEquals(64, first.hash().length());
  }

  @Test
  void exactSevenDaysThirtyMinutesAndFourHoursAreAllowed() {
    var now = LocalDateTime.parse("2026-11-02T08:00");
    ReservationInput.parse(
            input("2026-11-09T08:00+08:00", "2026-11-09T08:30+08:00", "用途"),
            UUID.randomUUID().toString())
        .validateTime(now);
    ReservationInput.parse(
            input("2026-11-03T18:00+08:00", "2026-11-03T22:00+08:00", "用途"),
            UUID.randomUUID().toString())
        .validateTime(now);
    var tooLate =
        ReservationInput.parse(
            input("2026-11-09T08:30+08:00", "2026-11-09T09:00+08:00", "用途"),
            UUID.randomUUID().toString());
    assertThrows(BusinessException.class, () -> tooLate.validateTime(now));
  }

  @Test
  void offsetsAndSubMinuteAlignmentAreRejected() {
    assertThrows(
        BusinessException.class,
        () ->
            ReservationInput.parse(
                input("2026-11-03T01:00Z", "2026-11-03T02:00Z", "用途"),
                UUID.randomUUID().toString()));
    var fractional =
        ReservationInput.parse(
            input("2026-11-03T09:00:00.001+08:00", "2026-11-03T10:00+08:00", "用途"),
            UUID.randomUUID().toString());
    assertThrows(
        BusinessException.class,
        () -> fractional.validateTime(LocalDateTime.parse("2026-11-02T08:00")));
  }

  Reservation row(String status) {
    var r = new Reservation();
    r.status = status;
    r.startTime = LocalDateTime.parse("2026-11-03T09:00");
    r.endTime = r.startTime.plusHours(1);
    return r;
  }

  @Test
  void terminalStatesRejectEveryFurtherTransition() {
    for (String state : List.of("REJECTED", "CANCELLED", "EXPIRED", "COMPLETED"))
      for (var action : ReservationAction.values())
        assertThrows(
            BusinessException.class,
            () ->
                ReservationLifecycle.target(
                    row(state), action, LocalDateTime.parse("2026-11-04T10:00")));
  }

  @Test
  void exactStartAndEndBoundariesRespectStateMachine() {
    var pending = row("PENDING");
    var approved = row("APPROVED");
    assertThrows(
        BusinessException.class,
        () -> ReservationLifecycle.target(pending, ReservationAction.APPROVE, pending.startTime));
    assertEquals(
        ReservationStatus.EXPIRED,
        ReservationLifecycle.target(pending, ReservationAction.EXPIRE, pending.startTime));
    assertThrows(
        BusinessException.class,
        () ->
            ReservationLifecycle.target(
                approved, ReservationAction.COMPLETE, approved.endTime.minusNanos(1)));
    assertEquals(
        ReservationStatus.COMPLETED,
        ReservationLifecycle.target(approved, ReservationAction.COMPLETE, approved.endTime));
  }
}
