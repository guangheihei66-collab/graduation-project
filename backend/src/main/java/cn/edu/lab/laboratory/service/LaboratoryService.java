package cn.edu.lab.laboratory.service;

import cn.edu.lab.common.*;
import cn.edu.lab.equipment.entity.Equipment;
import cn.edu.lab.equipment.mapper.EquipmentMapper;
import cn.edu.lab.laboratory.dto.*;
import cn.edu.lab.laboratory.entity.Laboratory;
import cn.edu.lab.laboratory.mapper.*;
import cn.edu.lab.security.ActorAccess;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryService {
  private final LaboratoryMapper labs;
  private final LabAdminMapper admins;
  private final EquipmentMapper equipment;
  private final SysUserMapper users;
  private final ActorAccess actorAccess;
  private final ResourceAccess access;
  private final ResourceStateGuard guard;
  private final Clock clock;

  public LaboratoryService(
      LaboratoryMapper labs,
      LabAdminMapper admins,
      EquipmentMapper equipment,
      SysUserMapper users,
      ActorAccess actorAccess,
      ResourceAccess access,
      ResourceStateGuard guard,
      Clock clock) {
    this.labs = labs;
    this.admins = admins;
    this.equipment = equipment;
    this.users = users;
    this.actorAccess = actorAccess;
    this.access = access;
    this.guard = guard;
    this.clock = clock;
  }

  public PageResult<LabView> list(PageQuery page, String name, Integer enabled) {
    var actor = actorAccess.current();
    var q = new QueryWrapper<Laboratory>();
    var search = InputRules.search(name, 100);
    if (search != null) q.like("name", search);
    if (enabled != null) q.eq("enabled", enabled);
    if ("STUDENT".equals(actor.role())) q.eq("enabled", 1);
    else if ("LAB_ADMIN".equals(actor.role()))
      q.exists(
          "SELECT 1 FROM lab_admin a WHERE a.lab_id=laboratory.id AND a.user_id={0}",
          Long.parseLong(actor.id()));
    q.orderByDesc("created_at", "id");
    var p = labs.selectPage(page.<Laboratory>toPage(), q);
    return new PageResult<>(
        p.getRecords().stream().map(LabView::from).toList(),
        p.getTotal(),
        p.getCurrent(),
        p.getSize());
  }

  public LabView detail(long id) {
    var actor = actorAccess.current();
    var lab = access.lab(id, false);
    access.readable(actor, lab);
    return LabView.from(lab);
  }

  @Transactional
  public LabView create(CreateLabRequest req) {
    actorAccess.systemAdmin();
    var lab = new Laboratory();
    lab.code = req.code();
    lab.name = req.name();
    lab.location = req.location();
    lab.description = req.description();
    lab.enabled = 1;
    labs.insert(lab);
    return LabView.from(lab);
  }

  @Transactional
  public LabView update(long id, UpdateLabRequest req) {
    var lab = access.lab(id, true);
    var actor = actorAccess.current();
    access.writable(actor, lab);
    if (req.hasEnabled() && req.getEnabled() == 0 && lab.enabled == 1) {
      // 先锁实验室，再按设备 ID 升序锁定，授权替换及设备新增也持有实验室锁。
      var locked =
          equipment.selectList(
              new QueryWrapper<Equipment>().eq("lab_id", id).orderByAsc("id").last("FOR UPDATE"));
      guard.beforeLabDisabled(id, locked.stream().map(e -> e.id).toList());
    }
    var q =
        new UpdateWrapper<Laboratory>().eq("id", id).set("updated_at", LocalDateTime.now(clock));
    if (req.hasName()) q.set("name", req.getName());
    if (req.hasLocation()) q.set("location", req.getLocation());
    if (req.hasDescription()) q.set("description", req.getDescription());
    if (req.hasEnabled()) q.set("enabled", req.getEnabled());
    labs.update(null, q);
    return LabView.from(labs.selectById(id));
  }

  public List<AdminView> admins(long id) {
    actorAccess.systemAdmin();
    access.lab(id, false);
    return admins.listAdmins(id);
  }

  @Transactional
  public List<AdminView> assign(long id, AssignAdminsRequest req) {
    actorAccess.systemAdmin();
    access.lab(id, true);
    List<Long> ids = new ArrayList<>();
    try {
      for (String value : req.userIds()) ids.add(Long.parseLong(value));
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    if (new HashSet<>(ids).size() != ids.size())
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    ids.sort(Long::compareTo);
    for (long userId : ids) {
      var user = users.lockById(userId);
      if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND);
      if (!"LAB_ADMIN".equals(user.role) || user.enabled != 1)
        throw new BusinessException(ErrorCode.CONFLICT);
    }
    admins.deleteForLab(id);
    for (long userId : ids) admins.assign(id, userId, LocalDateTime.now(clock));
    return admins.listAdmins(id);
  }
}
