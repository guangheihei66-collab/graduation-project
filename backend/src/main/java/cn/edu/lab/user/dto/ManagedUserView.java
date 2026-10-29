package cn.edu.lab.user.dto;

import cn.edu.lab.user.entity.SysUser;
import java.time.*;

public record ManagedUserView(
    String id,
    String username,
    String realName,
    String role,
    int enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static ManagedUserView from(SysUser u) {
    return new ManagedUserView(
        u.id.toString(),
        u.username,
        u.realName,
        u.role,
        u.enabled,
        u.createdAt.atOffset(ZoneOffset.ofHours(8)),
        u.updatedAt.atOffset(ZoneOffset.ofHours(8)));
  }
}
