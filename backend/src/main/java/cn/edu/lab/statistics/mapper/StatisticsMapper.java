package cn.edu.lab.statistics.mapper;

import cn.edu.lab.reservation.entity.Reservation;
import cn.edu.lab.statistics.dto.StatisticsView.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 动态片段仅来自服务端白名单 QueryWrapper，不接收客户端 SQL 或排序字段。 */
public interface StatisticsMapper {
  @Select(
      "SELECT COUNT(*) total_count,COALESCE(SUM(CASE WHEN status='PENDING' AND start_time>#{now}"
          + " THEN 1 ELSE 0 END),0) pending_count,COALESCE(SUM(CASE WHEN status='APPROVED' THEN 1"
          + " ELSE 0 END),0) approved_count FROM reservation ${ew.customSqlSegment}")
  Totals totals(
      @Param(Constants.WRAPPER) Wrapper<Reservation> query, @Param("now") LocalDateTime now);

  @Select(
      "SELECT DATE(reservation.created_at) date,COUNT(*) count FROM reservation"
          + " ${ew.customSqlSegment} GROUP BY DATE(reservation.created_at) ORDER BY date")
  List<DailyPoint> daily(@Param(Constants.WRAPPER) Wrapper<Reservation> query);

  @Select(
      "SELECT e.id equipment_id,e.name equipment_name,COUNT(*) count FROM reservation JOIN"
          + " equipment e ON e.id=reservation.equipment_id ${ew.customSqlSegment} GROUP BY"
          + " e.id,e.name ORDER BY count DESC,e.id ASC LIMIT 10")
  List<TopEquipment> top(@Param(Constants.WRAPPER) Wrapper<Reservation> query);
}
