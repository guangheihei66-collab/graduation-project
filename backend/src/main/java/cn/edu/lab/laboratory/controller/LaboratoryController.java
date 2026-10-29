package cn.edu.lab.laboratory.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.laboratory.dto.*;
import cn.edu.lab.laboratory.service.LaboratoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/labs")
public class LaboratoryController {
  private final LaboratoryService service;

  public LaboratoryController(LaboratoryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<LabView>> list(
      @Valid @ModelAttribute PageQuery page,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) @Min(0) @Max(1) Integer enabled) {
    return ApiResponse.ok(service.list(page, name, enabled));
  }

  @GetMapping("/{id}")
  public ApiResponse<LabView> detail(@PathVariable @Positive long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LabView>> create(@Valid @RequestBody CreateLabRequest req) {
    return ResponseEntity.status(201).body(ApiResponse.ok(service.create(req)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<LabView> update(
      @PathVariable @Positive long id, @Valid @RequestBody UpdateLabRequest req) {
    return ApiResponse.ok(service.update(id, req));
  }

  @GetMapping("/{id}/admins")
  public ApiResponse<List<AdminView>> admins(@PathVariable @Positive long id) {
    return ApiResponse.ok(service.admins(id));
  }

  @PutMapping("/{id}/admins")
  public ApiResponse<List<AdminView>> assign(
      @PathVariable @Positive long id, @Valid @RequestBody AssignAdminsRequest req) {
    return ApiResponse.ok(service.assign(id, req));
  }
}
