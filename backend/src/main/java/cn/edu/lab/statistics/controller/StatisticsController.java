package cn.edu.lab.statistics.controller;

import cn.edu.lab.common.ApiResponse;
import cn.edu.lab.statistics.dto.StatisticsView;
import cn.edu.lab.statistics.service.StatisticsService;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatisticsController {
  private final StatisticsService service;

  public StatisticsController(StatisticsService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/statistics/overview")
  public ApiResponse<StatisticsView> overview(
      @RequestParam(required = false) @Positive Long labId,
      @RequestParam LocalDate fromDate,
      @RequestParam LocalDate toDate) {
    return ApiResponse.ok(service.overview(labId, fromDate, toDate));
  }
}
