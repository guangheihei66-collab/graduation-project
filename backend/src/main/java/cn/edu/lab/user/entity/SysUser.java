package cn.edu.lab.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String username;
  public String passwordHash;
  public String realName;
  public String role;
  public Integer enabled;
  public String wxOpenid;

  @TableField(fill = FieldFill.INSERT)
  public LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  public LocalDateTime updatedAt;
}
