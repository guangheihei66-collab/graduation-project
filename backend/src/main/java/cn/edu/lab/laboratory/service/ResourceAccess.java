package cn.edu.lab.laboratory.service;

import cn.edu.lab.auth.dto.UserView;
import cn.edu.lab.common.*;
import cn.edu.lab.laboratory.entity.Laboratory;
import cn.edu.lab.laboratory.mapper.*;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccess {
  private final LaboratoryMapper labs;
  private final LabAdminMapper admins;

  public ResourceAccess(LaboratoryMapper labs, LabAdminMapper admins) {
    this.labs = labs;
    this.admins = admins;
  }

  public Laboratory lab(long id, boolean lock) {
    var lab = lock ? labs.lockById(id) : labs.selectById(id);
    if (lab == null) throw new BusinessException(ErrorCode.NOT_FOUND);
    return lab;
  }

  public void readable(UserView actor, Laboratory lab) {
    if ("SYS_ADMIN".equals(actor.role())) return;
    if ("STUDENT".equals(actor.role()) && lab.enabled == 1) return;
    if ("LAB_ADMIN".equals(actor.role())
        && admins.countAssignment(lab.id, Long.parseLong(actor.id())) > 0) return;
    throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  public void writable(UserView actor, Laboratory lab) {
    if (!"SYS_ADMIN".equals(actor.role()) && !"LAB_ADMIN".equals(actor.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    readable(actor, lab);
  }
}
