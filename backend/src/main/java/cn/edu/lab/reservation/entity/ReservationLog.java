package cn.edu.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("reservation_log")
public class ReservationLog {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long reservationId;
  public Long operatorId;
  public String action;
  public String fromStatus;
  public String toStatus;
  public String reason;

  @TableField(fill = FieldFill.INSERT)
  public java.time.LocalDateTime createdAt;
}
