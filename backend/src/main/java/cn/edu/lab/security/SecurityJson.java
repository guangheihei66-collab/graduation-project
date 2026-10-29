package cn.edu.lab.security;

import cn.edu.lab.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class SecurityJson {
  private final ObjectMapper json;

  public SecurityJson(ObjectMapper json) {
    this.json = json;
  }

  public void write(HttpServletResponse response, ErrorCode code) throws IOException {
    response.setStatus(code.status.value());
    response.setContentType("application/json;charset=UTF-8");
    json.writeValue(response.getOutputStream(), ApiResponse.error(code));
  }
}
