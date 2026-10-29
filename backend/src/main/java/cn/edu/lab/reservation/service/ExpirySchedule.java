package cn.edu.lab.reservation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ExpirySchedule {
  private final ExpiryJob job;

  public ExpirySchedule(ExpiryJob job) {
    this.job = job;
  }

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
  public void tick() {
    job.runOnce();
  }
}
