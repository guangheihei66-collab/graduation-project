package cn.edu.lab.laboratory.dto;

import cn.edu.lab.laboratory.entity.Laboratory;
import java.time.*;

public record LabView(
    String id,
    String code,
    String name,
    String location,
    String description,
    int enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static LabView from(Laboratory l) {
    return new LabView(
        l.id.toString(),
        l.code,
        l.name,
        l.location,
        l.description,
        l.enabled,
        l.createdAt.atOffset(ZoneOffset.ofHours(8)),
        l.updatedAt.atOffset(ZoneOffset.ofHours(8)));
  }
}
