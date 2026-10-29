package cn.edu.lab.laboratory.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("laboratory")
public class Laboratory {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String code;
  public String name;
  public String location;
  public String description;
  public Integer enabled;

  @TableField(fill = FieldFill.INSERT)
  public LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  public LocalDateTime updatedAt;
}
