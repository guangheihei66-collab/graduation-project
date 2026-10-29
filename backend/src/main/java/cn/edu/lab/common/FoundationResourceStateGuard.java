package cn.edu.lab.common;

import java.util.List;
import org.springframework.stereotype.Component;

/** 本阶段不存在预约表，此实现明确不查询预约，也不代表预约占用检测已完成。 */
@Component
public class FoundationResourceStateGuard implements ResourceStateGuard {
  public void beforeLabDisabled(long labId, List<Long> equipmentIds) {}

  public void beforeEquipmentUnavailable(long equipmentId) {}
}
