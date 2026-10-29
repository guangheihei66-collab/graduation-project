package cn.edu.lab.user.dto;

import cn.edu.lab.common.InputRules;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotNull String newPassword) {
  @AssertTrue
  @JsonIgnore
  public boolean isPasswordValid() {
    return InputRules.password(newPassword);
  }

  @Override
  public String toString() {
    return "ResetPasswordRequest[凭据已隐藏]";
  }
}
