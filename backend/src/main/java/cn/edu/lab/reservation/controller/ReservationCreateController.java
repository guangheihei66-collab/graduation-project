package cn.edu.lab.reservation.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.*;
import cn.edu.lab.reservation.service.ReservationCreateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationCreateController {
  private final ReservationCreateService service;

  public ReservationCreateController(ReservationCreateService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/reservations")
  public ResponseEntity<ApiResponse<ReservationView>> create(
      @Valid @RequestBody CreateReservationRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = service.create(req, key);
    return ResponseEntity.status(result.replay() ? 200 : 201)
        .body(ApiResponse.ok(result.reservation()));
  }
}
