package cn.edu.lab.auth.dto;

import cn.edu.lab.user.entity.SysUser;

public record UserView(String id, String username, String realName, String role) {
  public static UserView from(SysUser u) {
    return new UserView(u.id.toString(), u.username, u.realName, u.role);
  }
}
