package cn.edu.lab.auth.wechat;

import cn.edu.lab.auth.mapper.BindTicketMapper;
import java.time.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BindTicketCleanup {
  private final BindTicketMapper tickets;
  private final Clock clock;

  public BindTicketCleanup(BindTicketMapper tickets, Clock clock) {
    this.tickets = tickets;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 60000)
  public void clean() {
    tickets.deleteExpired(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")));
  }
}
