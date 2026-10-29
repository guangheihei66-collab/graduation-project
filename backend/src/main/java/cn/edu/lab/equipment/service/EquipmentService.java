package cn.edu.lab.equipment.service;

import cn.edu.lab.common.*;
import cn.edu.lab.equipment.dto.*;
import cn.edu.lab.equipment.entity.Equipment;
import cn.edu.lab.equipment.mapper.EquipmentMapper;
import cn.edu.lab.laboratory.mapper.LaboratoryMapper;
import cn.edu.lab.laboratory.service.ResourceAccess;
import cn.edu.lab.security.ActorAccess;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {
  private final EquipmentMapper equipment;
  private final LaboratoryMapper labs;
  private final ActorAccess actors;
  private final ResourceAccess access;
  private final ResourceStateGuard guard;
  private final Clock clock;

  public EquipmentService(
      EquipmentMapper equipment,
      LaboratoryMapper labs,
      ActorAccess actors,
      ResourceAccess access,
      ResourceStateGuard guard,
      Clock clock) {
    this.equipment = equipment;
    this.labs = labs;
    this.actors = actors;
    this.access = access;
    this.guard = guard;
    this.clock = clock;
  }

  public PageResult<EquipmentView> list(
      PageQuery page,
      Long labId,
      String keyword,
      EquipmentCategory category,
      EquipmentStatus status) {
    var actor = actors.current();
    if (labId != null) access.readable(actor, access.lab(labId, false));
    var q = new QueryWrapper<Equipment>();
    if (labId != null) q.eq("lab_id", labId);
    String search = InputRules.search(keyword, 100);
    if (search != null) q.and(w -> w.like("name", search).or().like("asset_no", search));
    if (category != null) q.eq("category", category.name());
    if (status != null) q.eq("status", status.name());
    if ("STUDENT".equals(actor.role()))
      q.eq("status", "AVAILABLE")
          .exists("SELECT 1 FROM laboratory l WHERE l.id=equipment.lab_id AND l.enabled=1");
    else if ("LAB_ADMIN".equals(actor.role()))
      q.exists(
          "SELECT 1 FROM lab_admin a WHERE a.lab_id=equipment.lab_id AND a.user_id={0}",
          Long.parseLong(actor.id()));
    q.orderByDesc("created_at", "id");
    var p = equipment.selectPage(page.<Equipment>toPage(), q);
    Map<Long, String> names =
        p.getRecords().isEmpty()
            ? Map.of()
            : labs
                .selectByIds(p.getRecords().stream().map(e -> e.labId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(l -> l.id, l -> l.name));
    return new PageResult<>(
        p.getRecords().stream().map(e -> EquipmentView.from(e, names.get(e.labId))).toList(),
        p.getTotal(),
        p.getCurrent(),
        p.getSize());
  }

  public EquipmentView detail(long id) {
    var actor = actors.current();
    var e = get(id);
    var lab = access.lab(e.labId, false);
    access.readable(actor, lab);
    if ("STUDENT".equals(actor.role()) && !"AVAILABLE".equals(e.status))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    return EquipmentView.from(e, lab.name);
  }

  @Transactional
  public EquipmentView create(CreateEquipmentRequest req) {
    long labId;
    try {
      labId = Long.parseLong(req.labId());
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    var lab = access.lab(labId, true);
    access.writable(actors.current(), lab);
    var e = new Equipment();
    e.labId = labId;
    e.assetNo = req.assetNo();
    e.name = req.name();
    e.category = req.category().name();
    e.model = req.model();
    e.location = req.location();
    e.description = req.description();
    e.status = "AVAILABLE";
    equipment.insert(e);
    return EquipmentView.from(e, lab.name);
  }

  @Transactional
  public EquipmentView update(long id, UpdateEquipmentRequest req) {
    // 先只读定位不可变关联，再按统一顺序加锁并重新读取。
    var initial = get(id);
    var lab = access.lab(initial.labId, true);
    var e = equipment.lockById(id);
    access.writable(actors.current(), lab);
    if (req.hasStatus()
        && !req.getStatus().name().equals(e.status)
        && req.getStatus() != EquipmentStatus.AVAILABLE) guard.beforeEquipmentUnavailable(id);
    var q = new UpdateWrapper<Equipment>().eq("id", id).set("updated_at", LocalDateTime.now(clock));
    if (req.hasName()) q.set("name", req.getName());
    if (req.hasCategory()) q.set("category", req.getCategory().name());
    if (req.hasModel()) q.set("model", req.getModel());
    if (req.hasLocation()) q.set("location", req.getLocation());
    if (req.hasDescription()) q.set("description", req.getDescription());
    if (req.hasStatus()) q.set("status", req.getStatus().name());
    equipment.update(null, q);
    return EquipmentView.from(equipment.selectById(id), lab.name);
  }

  private Equipment get(long id) {
    var e = equipment.selectById(id);
    if (e == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    return e;
  }
}
