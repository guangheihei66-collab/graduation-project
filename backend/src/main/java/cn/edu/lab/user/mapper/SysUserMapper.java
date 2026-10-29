package cn.edu.lab.user.mapper;

import cn.edu.lab.user.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface SysUserMapper extends BaseMapper<SysUser> {
  @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
  SysUser lockById(@org.apache.ibatis.annotations.Param("id") long id);
}
