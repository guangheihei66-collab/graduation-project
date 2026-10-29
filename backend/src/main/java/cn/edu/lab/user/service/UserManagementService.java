package cn.edu.lab.user.service;

import cn.edu.lab.common.*;
import cn.edu.lab.security.ActorAccess;
import cn.edu.lab.user.dto.*;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
  private final SysUserMapper users;
  private final ActorAccess access;
  private final PasswordEncoder passwords;
  private final Clock clock;

  public UserManagementService(
      SysUserMapper users, ActorAccess access, PasswordEncoder passwords, Clock clock) {
    this.users = users;
    this.access = access;
    this.passwords = passwords;
    this.clock = clock;
  }

  public PageResult<ManagedUserView> list(
      PageQuery page, String username, UserRole role, Integer enabled) {
    access.systemAdmin();
    var q = new QueryWrapper<SysUser>();
    String search = InputRules.search(username, 64);
    if (search != null) q.like("username", search);
    if (role != null) q.eq("role", role.name());
    if (enabled != null) q.eq("enabled", enabled);
    q.orderByDesc("created_at", "id");
    var p = users.selectPage(page.<SysUser>toPage(), q);
    return new PageResult<>(
        p.getRecords().stream().map(ManagedUserView::from).toList(),
        p.getTotal(),
        p.getCurrent(),
        p.getSize());
  }

  @Transactional
  public ManagedUserView create(CreateUserRequest req) {
    access.systemAdmin();
    var u = new SysUser();
    u.username = req.username();
    u.realName = req.realName();
    u.role = req.role().name();
    u.enabled = 1;
    u.passwordHash = passwords.encode(req.initialPassword());
    users.insert(u);
    return ManagedUserView.from(u);
  }

  @Transactional
  public ManagedUserView update(long id, UpdateUserRequest req) {
    var actor = access.systemAdmin();
    var user = locked(id);
    if (req.hasEnabled() && req.getEnabled() == 0 && actor.id().equals(Long.toString(id)))
      throw new BusinessException(ErrorCode.CONFLICT);
    var q = new UpdateWrapper<SysUser>().eq("id", id).set("updated_at", LocalDateTime.now(clock));
    if (req.hasRealName()) q.set("real_name", req.getRealName());
    if (req.hasEnabled()) q.set("enabled", req.getEnabled());
    users.update(null, q);
    return ManagedUserView.from(users.selectById(id));
  }

  @Transactional
  public java.util.Map<String, String> reset(long id, ResetPasswordRequest req) {
    access.systemAdmin();
    locked(id);
    users.update(
        null,
        new UpdateWrapper<SysUser>()
            .eq("id", id)
            .set("password_hash", passwords.encode(req.newPassword()))
            .set("updated_at", LocalDateTime.now(clock)));
    return java.util.Map.of("id", Long.toString(id));
  }

  private SysUser locked(long id) {
    var user = users.lockById(id);
    if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    return user;
  }
}
