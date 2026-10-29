package cn.edu.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("reservation")
public class Reservation {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long equipmentId;
  public Long studentId;
  public java.time.LocalDateTime startTime;
  public java.time.LocalDateTime endTime;
  public String purpose;
  public String status;
  public String idempotencyKey;
  public String requestHash;

  @TableField(fill = FieldFill.INSERT)
  public java.time.LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  public java.time.LocalDateTime updatedAt;
}
