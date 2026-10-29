package cn.edu.lab.reservation.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.service.ReservationQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationQueryController {
  private final ReservationQueryService service;

  public ReservationQueryController(ReservationQueryService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/reservations/mine")
  public ApiResponse<PageResult<ReservationView>> mine(
      @Valid @ModelAttribute PageQuery page,
      @RequestParam(required = false) @Positive Long labId,
      @RequestParam(required = false) @Positive Long equipmentId,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate) {
    return ApiResponse.ok(service.list(true, page, labId, equipmentId, status, fromDate, toDate));
  }

  @GetMapping("/api/v1/reservations")
  public ApiResponse<PageResult<ReservationView>> list(
      @Valid @ModelAttribute PageQuery page,
      @RequestParam(required = false) @Positive Long labId,
      @RequestParam(required = false) @Positive Long equipmentId,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate) {
    return ApiResponse.ok(service.list(false, page, labId, equipmentId, status, fromDate, toDate));
  }

  @GetMapping("/api/v1/reservations/{id}")
  public ApiResponse<ReservationView> detail(@PathVariable @Positive long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @GetMapping("/api/v1/equipment/{id}/slots")
  public ApiResponse<SlotsView> slots(
      @PathVariable @Positive long id, @RequestParam LocalDate date) {
    return ApiResponse.ok(service.slots(id, date));
  }
}
