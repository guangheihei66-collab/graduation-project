package cn.edu.lab.reservation.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.service.ReservationTransitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations/{id}")
public class ReservationActionController {
  private final ReservationTransitionService service;

  public ReservationActionController(ReservationTransitionService service) {
    this.service = service;
  }

  @PostMapping("/review")
  public ApiResponse<ReservationView> review(
      @PathVariable @Positive long id, @Valid @RequestBody ReviewRequest req) {
    return ApiResponse.ok(
        service.change(id, ReservationAction.valueOf(req.decision().name()), req.reason()));
  }

  @PostMapping("/cancel")
  public ApiResponse<ReservationView> cancel(
      @PathVariable @Positive long id, @Valid @RequestBody(required = false) ReasonRequest req) {
    return ApiResponse.ok(
        service.change(id, ReservationAction.CANCEL, req == null ? null : req.reason()));
  }

  @PostMapping("/revoke")
  public ApiResponse<ReservationView> revoke(
      @PathVariable @Positive long id, @Valid @RequestBody RevokeRequest req) {
    return ApiResponse.ok(service.change(id, ReservationAction.REVOKE, req.reason()));
  }

  @PostMapping("/complete")
  public ApiResponse<ReservationView> complete(@PathVariable @Positive long id) {
    return ApiResponse.ok(service.change(id, ReservationAction.COMPLETE, null));
  }
}
