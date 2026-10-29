package cn.edu.lab.equipment.dto;

import cn.edu.lab.common.InputRules;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.AssertTrue;

public class UpdateEquipmentRequest {
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

  private EquipmentCategory category;
  private boolean hasCategory;

  public EquipmentCategory getCategory() {
    return category;
  }

  @JsonSetter
  public void setCategory(EquipmentCategory value) {
    category = value;
    hasCategory = true;
  }

  public boolean hasCategory() {
    return hasCategory;
  }

  private String model;
  private boolean hasModel;

  public String getModel() {
    return model;
  }

  @JsonSetter
  public void setModel(String value) {
    model = value;
    hasModel = true;
  }

  public boolean hasModel() {
    return hasModel;
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

  private EquipmentStatus status;
  private boolean hasStatus;

  public EquipmentStatus getStatus() {
    return status;
  }

  @JsonSetter
  public void setStatus(EquipmentStatus value) {
    status = value;
    hasStatus = true;
  }

  public boolean hasStatus() {
    return hasStatus;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidPatch() {
    return (hasName || hasCategory || hasModel || hasLocation || hasDescription || hasStatus)
        && (InputRules.requiredPatch(hasName, name, 100)
            && InputRules.requiredPatch(hasLocation, location, 200)
            && InputRules.optionalPatch(model, 100)
            && InputRules.optionalPatch(description, 1000)
            && (!hasCategory || category != null)
            && (!hasStatus || status != null));
  }
}
