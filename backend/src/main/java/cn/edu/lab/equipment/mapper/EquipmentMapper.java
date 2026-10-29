package cn.edu.lab.equipment.mapper;

import cn.edu.lab.equipment.entity.Equipment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface EquipmentMapper extends BaseMapper<Equipment> {
  @org.apache.ibatis.annotations.Select("SELECT * FROM equipment WHERE id=#{id} FOR UPDATE")
  Equipment lockById(@org.apache.ibatis.annotations.Param("id") long id);
}
