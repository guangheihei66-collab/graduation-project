package cn.edu.lab.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public record WechatBindRequest(
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String bindTicket,
    @NotBlank @Size(max = 64) @Pattern(regexp = "\\S(?:.*\\S)?") String username,
    @NotBlank @Size(min = 8, max = 64) String password) {
  @AssertTrue
  @JsonIgnore
  public boolean isPasswordWithinBcryptLimit() {
    return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "WechatBindRequest[凭据已隐藏]";
  }
}
