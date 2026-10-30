package cn.edu.lab.auth.wechat;

import java.time.LocalDateTime;

public class BindTicket {
  public String ticketHash;
  public String wxOpenid;
  public LocalDateTime expiresAt;
  public LocalDateTime createdAt;
}
