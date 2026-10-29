package cn.edu.lab.equipment.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.equipment.dto.*;
import cn.edu.lab.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
  private final EquipmentService service;

  public EquipmentController(EquipmentService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<EquipmentView>> list(
      @Valid @ModelAttribute PageQuery page,
      @RequestParam(required = false) @Positive Long labId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) EquipmentCategory category,
      @RequestParam(required = false) EquipmentStatus status) {
    return ApiResponse.ok(service.list(page, labId, keyword, category, status));
  }

  @GetMapping("/{id}")
  public ApiResponse<EquipmentView> detail(@PathVariable @Positive long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<EquipmentView>> create(
      @Valid @RequestBody CreateEquipmentRequest req) {
    return ResponseEntity.status(201).body(ApiResponse.ok(service.create(req)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<EquipmentView> update(
      @PathVariable @Positive long id, @Valid @RequestBody UpdateEquipmentRequest req) {
    return ApiResponse.ok(service.update(id, req));
  }
}
