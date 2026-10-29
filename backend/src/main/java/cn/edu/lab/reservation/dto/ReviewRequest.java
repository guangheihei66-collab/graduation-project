package cn.edu.lab.reservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record ReviewRequest(@NotNull Decision decision, @Size(max = 500) String reason) {
  public enum Decision {
    APPROVE,
    REJECT
  }

  @AssertTrue
  @JsonIgnore
  public boolean isReasonValid() {
    return decision != Decision.REJECT || reason != null && !reason.isBlank();
  }
}
