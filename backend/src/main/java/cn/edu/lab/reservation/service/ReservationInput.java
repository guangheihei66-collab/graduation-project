package cn.edu.lab.reservation.service;

import cn.edu.lab.common.*;
import cn.edu.lab.reservation.dto.CreateReservationRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public record ReservationInput(
    long equipmentId,
    LocalDateTime start,
    LocalDateTime end,
    String purpose,
    String key,
    String hash) {
  public static ReservationInput parse(CreateReservationRequest req, String key) {
    try {
      if (key == null
          || !key.matches(
              "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        throw new IllegalArgumentException();
      long eq = Long.parseLong(req.equipmentId());
      if (eq <= 0) throw new IllegalArgumentException();
      var a = OffsetDateTime.parse(req.startTime());
      var z = OffsetDateTime.parse(req.endTime());
      if (!a.getOffset().equals(ReservationRules.OFFSET)
          || !z.getOffset().equals(ReservationRules.OFFSET)) throw new IllegalArgumentException();
      String purpose = req.purpose().strip();
      if (purpose.isEmpty() || purpose.length() > 500) throw new IllegalArgumentException();
      String canonical =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .writeValueAsString(
                  List.of(
                      Long.toString(eq),
                      a.toLocalDateTime().toString(),
                      z.toLocalDateTime().toString(),
                      purpose));
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.getBytes(StandardCharsets.UTF_8)));
      return new ReservationInput(
          eq,
          a.toLocalDateTime(),
          z.toLocalDateTime(),
          purpose,
          key.toLowerCase(Locale.ROOT),
          hash);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
  }

  public void validateTime(LocalDateTime now) {
    long minutes = Duration.between(start, end).toMinutes();
    if (!start.isAfter(now)
        || start.isAfter(now.plusDays(7))
        || !start.toLocalDate().equals(end.toLocalDate())
        || !end.isAfter(start)
        || start.toLocalTime().isBefore(LocalTime.of(8, 0))
        || end.toLocalTime().isAfter(LocalTime.of(22, 0))
        || minutes < 30
        || minutes > 240
        || !aligned(start)
        || !aligned(end)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
  }

  private static boolean aligned(LocalDateTime t) {
    return t.getSecond() == 0 && t.getNano() == 0 && t.getMinute() % 30 == 0;
  }
}
