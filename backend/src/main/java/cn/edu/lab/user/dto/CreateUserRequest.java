package cn.edu.lab.user.dto;

import cn.edu.lab.common.InputRules;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "\\S(?:.*\\S)?") String username,
    @NotBlank @Size(max = 50) String realName,
    @NotNull UserRole role,
    @NotNull String initialPassword) {
  @AssertTrue
  @JsonIgnore
  public boolean isPasswordValid() {
    return InputRules.password(initialPassword);
  }

  @Override
  public String toString() {
    return "CreateUserRequest[凭据已隐藏]";
  }
}
