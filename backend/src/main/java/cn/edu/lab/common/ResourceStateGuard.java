package cn.edu.lab.common;

import java.util.List;

/** 阶段 3 接入预约占用检查；调用时已经持有实验室及相关设备行锁。 */
public interface ResourceStateGuard {
  void beforeLabDisabled(long labId, List<Long> equipmentIds);

  void beforeEquipmentUnavailable(long equipmentId);
}
