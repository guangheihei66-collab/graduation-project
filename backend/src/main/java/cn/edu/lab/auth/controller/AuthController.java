package cn.edu.lab.auth.controller;

import cn.edu.lab.auth.dto.*;
import cn.edu.lab.auth.service.AuthService;
import cn.edu.lab.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
    return ApiResponse.ok(service.login(req));
  }

  @GetMapping("/me")
  public ApiResponse<UserView> me(@AuthenticationPrincipal UserView user) {
    return ApiResponse.ok(user);
  }
}
