package cn.edu.lab.auth.mapper;

import cn.edu.lab.auth.wechat.BindTicket;
import org.apache.ibatis.annotations.*;

public interface BindTicketMapper {
  @Insert(
      "INSERT INTO wx_bind_ticket(ticket_hash,wx_openid,expires_at,created_at)"
          + " VALUES(#{ticketHash},#{wxOpenid},#{expiresAt},#{createdAt})")
  int insert(BindTicket ticket);

  @Select("SELECT * FROM wx_bind_ticket WHERE ticket_hash=#{hash} FOR UPDATE")
  BindTicket lock(String hash);

  @Delete("DELETE FROM wx_bind_ticket WHERE ticket_hash=#{hash}")
  int consume(String hash);

  @Delete("DELETE FROM wx_bind_ticket WHERE expires_at <= #{now} LIMIT 500")
  int deleteExpired(java.time.LocalDateTime now);
}
