package cn.edu.lab.equipment.dto;

import cn.edu.lab.equipment.entity.Equipment;
import java.time.*;

public record EquipmentView(
    String id,
    String labId,
    String labName,
    String assetNo,
    String name,
    String category,
    String model,
    String location,
    String description,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static EquipmentView from(Equipment e, String labName) {
    return new EquipmentView(
        e.id.toString(),
        e.labId.toString(),
        labName,
        e.assetNo,
        e.name,
        e.category,
        e.model,
        e.location,
        e.description,
        e.status,
        e.createdAt.atOffset(ZoneOffset.ofHours(8)),
        e.updatedAt.atOffset(ZoneOffset.ofHours(8)));
  }
}
