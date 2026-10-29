package cn.edu.lab.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("equipment")
public class Equipment {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long labId;
  public String assetNo;
  public String name;
  public String category;
  public String model;
  public String location;
  public String description;
  public String status;

  @TableField(fill = FieldFill.INSERT)
  public LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  public LocalDateTime updatedAt;
}
