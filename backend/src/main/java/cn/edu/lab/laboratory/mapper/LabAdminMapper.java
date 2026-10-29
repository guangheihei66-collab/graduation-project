package cn.edu.lab.laboratory.mapper;

import org.apache.ibatis.annotations.*;

public interface LabAdminMapper {
  @Select("SELECT COUNT(*) FROM lab_admin WHERE lab_id=#{labId} AND user_id=#{userId}")
  int countAssignment(@Param("labId") long labId, @Param("userId") long userId);

  @Select(
      "SELECT u.id,u.real_name FROM sys_user u JOIN lab_admin a ON a.user_id=u.id WHERE"
          + " a.lab_id=#{labId} ORDER BY u.id")
  java.util.List<cn.edu.lab.laboratory.dto.AdminView> listAdmins(@Param("labId") long labId);

  @Delete("DELETE FROM lab_admin WHERE lab_id=#{labId}")
  int deleteForLab(@Param("labId") long labId);

  @Insert("INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES (#{labId},#{userId},#{now})")
  int assign(
      @Param("labId") long labId,
      @Param("userId") long userId,
      @Param("now") java.time.LocalDateTime now);
}
