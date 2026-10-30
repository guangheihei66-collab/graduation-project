package cn.edu.lab.auth.dto;

import jakarta.validation.constraints.*;

public record WechatLoginRequest(@NotBlank @Size(max = 256) String code) {
  @Override
  public String toString() {
    return "WechatLoginRequest[凭据已隐藏]";
  }
}
