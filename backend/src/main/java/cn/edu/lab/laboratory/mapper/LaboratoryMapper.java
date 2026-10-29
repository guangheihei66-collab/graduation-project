package cn.edu.lab.laboratory.mapper;

import cn.edu.lab.laboratory.entity.Laboratory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface LaboratoryMapper extends BaseMapper<Laboratory> {
  @org.apache.ibatis.annotations.Select("SELECT * FROM laboratory WHERE id=#{id} FOR UPDATE")
  Laboratory lockById(@org.apache.ibatis.annotations.Param("id") long id);
}
