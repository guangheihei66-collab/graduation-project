package cn.edu.lab.statistics.dto;

import java.time.LocalDate;
import java.util.List;

public record StatisticsView(
    long totalCount,
    long pendingCount,
    long approvedCount,
    List<DailyPoint> dailyTrend,
    List<TopEquipment> topEquipment) {
  public record Totals(long totalCount, long pendingCount, long approvedCount) {}

  public record DailyPoint(LocalDate date, long count) {}

  public record TopEquipment(String equipmentId, String equipmentName, long count) {}
}
