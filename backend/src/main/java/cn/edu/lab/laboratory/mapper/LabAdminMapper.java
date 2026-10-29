package cn.edu.lab.laboratory.mapper;

import org.apache.ibatis.annotations.*;

public interface LabAdminMapper {
  @Select("SELECT COUNT(*) FROM lab_admin WHERE lab_id=#{labId} AND user_id=#{userId}")
  int countAssignment(@Param("labId") long labId, @Param("userId") long userId);
}
