package cn.edu.lab.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    MDC.put("requestId", id);
    res.setHeader("X-Request-ID", id);
    long start = System.nanoTime();
    try {
      chain.doFilter(req, res);
    } finally {
      // 不记录查询串、请求体、Authorization 或其他凭据。
      log.info(
          "requestId={} method={} status={} durationMs={}",
          id,
          req.getMethod(),
          res.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("requestId");
    }
  }
}
