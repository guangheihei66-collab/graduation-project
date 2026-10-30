package cn.edu.lab.auth.controller;

import cn.edu.lab.auth.dto.*;
import cn.edu.lab.auth.wechat.WechatAuthService;
import cn.edu.lab.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/wechat")
public class WechatAuthController {
  private final WechatAuthService service;

  public WechatAuthController(WechatAuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public ApiResponse<WechatLoginResponse> login(@Valid @RequestBody WechatLoginRequest req) {
    return ApiResponse.ok(service.login(req));
  }

  @PostMapping("/bind")
  public ApiResponse<LoginResponse> bind(@Valid @RequestBody WechatBindRequest req) {
    return ApiResponse.ok(service.bind(req));
  }
}
