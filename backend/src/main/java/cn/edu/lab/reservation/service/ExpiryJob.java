package cn.edu.lab.reservation.service;

import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.reservation.mapper.ReservationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.stereotype.Component;

@Component
public class ExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);
  private final ReservationMapper rows;
  private final ReservationTransitionService service;
  private final Clock clock;

  public ExpiryJob(ReservationMapper rows, ReservationTransitionService service, Clock clock) {
    this.rows = rows;
    this.service = service;
    this.clock = clock;
  }

  public int runOnce() {
    int changed = 0;
    long after = 0;
    // 每轮最多 2000 条，逐条独立事务；失败留待下一分钟，不在锁事务内无限重试。
    for (int batch = 0; batch < 10; batch++) {
      var candidates =
          rows.selectList(
              new QueryWrapper<Reservation>()
                  .eq("status", "PENDING")
                  .le("start_time", ReservationRules.now(clock))
                  .gt("id", after)
                  .orderByAsc("id")
                  .last("LIMIT 200"));
      if (candidates.isEmpty()) break;
      for (var r : candidates) {
        after = r.id;
        try {
          if (service.expire(r.id)) changed++;
        } catch (RuntimeException e) {
          log.error("过期任务单条失败，类型={}", e.getClass().getSimpleName());
        }
      }
    }
    return changed;
  }
}
