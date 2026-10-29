package cn.edu.lab.security;

import cn.edu.lab.auth.dto.UserView;
import cn.edu.lab.common.*;
import cn.edu.lab.user.mapper.SysUserMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ActorAccess {
  private final SysUserMapper users;

  public ActorAccess(SysUserMapper users) {
    this.users = users;
  }

  public UserView current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserView principal))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    var user = users.selectById(Long.parseLong(principal.id()));
    if (user == null || !Integer.valueOf(1).equals(user.enabled))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    return UserView.from(user);
  }

  public UserView systemAdmin() {
    var actor = current();
    if (!"SYS_ADMIN".equals(actor.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
    return actor;
  }
}
