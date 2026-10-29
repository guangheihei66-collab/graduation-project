package cn.edu.lab.user.controller;

import cn.edu.lab.common.*;
import cn.edu.lab.user.dto.*;
import cn.edu.lab.user.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserManagementService service;

  public UserController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<ManagedUserView>> list(
      @Valid @ModelAttribute PageQuery page,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) UserRole role,
      @RequestParam(required = false) @Min(0) @Max(1) Integer enabled) {
    return ApiResponse.ok(service.list(page, username, role, enabled));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ManagedUserView>> create(
      @Valid @RequestBody CreateUserRequest req) {
    return ResponseEntity.status(201).body(ApiResponse.ok(service.create(req)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<ManagedUserView> update(
      @PathVariable @Positive long id, @Valid @RequestBody UpdateUserRequest req) {
    return ApiResponse.ok(service.update(id, req));
  }

  @PutMapping("/{id}/password")
  public ApiResponse<java.util.Map<String, String>> reset(
      @PathVariable @Positive long id, @Valid @RequestBody ResetPasswordRequest req) {
    return ApiResponse.ok(service.reset(id, req));
  }
}
