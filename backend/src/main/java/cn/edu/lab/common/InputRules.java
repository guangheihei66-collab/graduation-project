package cn.edu.lab.common;

import java.nio.charset.StandardCharsets;

public final class InputRules {
  private InputRules() {}

  public static boolean password(String value) {
    return value != null
        && !value.isBlank()
        && value.length() >= 8
        && value.length() <= 64
        && value.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  public static boolean requiredPatch(boolean present, String value, int max) {
    return !present || value != null && !value.isBlank() && value.length() <= max;
  }

  public static boolean optionalPatch(String value, int max) {
    return value == null || value.length() <= max;
  }

  public static String search(String value, int max) {
    if (value == null) return null;
    if (value.isBlank() || value.length() > max)
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
