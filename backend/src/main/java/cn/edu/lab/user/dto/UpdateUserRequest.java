package cn.edu.lab.user.dto;

import cn.edu.lab.common.InputRules;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.AssertTrue;

public class UpdateUserRequest {
  private String realName;
  private boolean hasRealName;

  public String getRealName() {
    return realName;
  }

  @JsonSetter
  public void setRealName(String value) {
    realName = value;
    hasRealName = true;
  }

  public boolean hasRealName() {
    return hasRealName;
  }

  private Integer enabled;
  private boolean hasEnabled;

  public Integer getEnabled() {
    return enabled;
  }

  @JsonSetter
  public void setEnabled(Integer value) {
    enabled = value;
    hasEnabled = true;
  }

  public boolean hasEnabled() {
    return hasEnabled;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidPatch() {
    return (hasRealName || hasEnabled)
        && (InputRules.requiredPatch(hasRealName, realName, 50)
            && (!hasEnabled || enabled != null && (enabled == 0 || enabled == 1)));
  }
}
