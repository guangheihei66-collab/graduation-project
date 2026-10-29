package cn.edu.lab.laboratory.dto;

import cn.edu.lab.common.InputRules;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.AssertTrue;

public class UpdateLabRequest {
  private String name;
  private boolean hasName;

  public String getName() {
    return name;
  }

  @JsonSetter
  public void setName(String value) {
    name = value;
    hasName = true;
  }

  public boolean hasName() {
    return hasName;
  }

  private String location;
  private boolean hasLocation;

  public String getLocation() {
    return location;
  }

  @JsonSetter
  public void setLocation(String value) {
    location = value;
    hasLocation = true;
  }

  public boolean hasLocation() {
    return hasLocation;
  }

  private String description;
  private boolean hasDescription;

  public String getDescription() {
    return description;
  }

  @JsonSetter
  public void setDescription(String value) {
    description = value;
    hasDescription = true;
  }

  public boolean hasDescription() {
    return hasDescription;
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
    return (hasName || hasLocation || hasDescription || hasEnabled)
        && (InputRules.requiredPatch(hasName, name, 100)
            && InputRules.requiredPatch(hasLocation, location, 200)
            && InputRules.optionalPatch(description, 1000)
            && (!hasEnabled || enabled != null && (enabled == 0 || enabled == 1)));
  }
}
