package cn.edu.lab.auth.wechat;

import cn.edu.lab.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OfficialWechatClient implements WechatClient {
  private final String appId;
  private final String secret;
  private final ObjectMapper json;
  private final HttpClient http;
  private String endpoint;
  private static final String ENDPOINT = "https://api.weixin.qq.com/sns/jscode2session";

  @org.springframework.beans.factory.annotation.Autowired
  public OfficialWechatClient(
      @Value("${app.wechat.app-id:}") String appId,
      @Value("${app.wechat.app-secret:}") String secret,
      ObjectMapper json) {
    this(
        appId,
        secret,
        json,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  OfficialWechatClient(String appId, String secret, ObjectMapper json, HttpClient http) {
    this.appId = appId;
    this.secret = secret;
    this.json = json;
    this.http = http;
    this.endpoint = ENDPOINT;
  }

  // 仅包内测试可替换地址；生产构造器始终使用官方 HTTPS 常量。
  OfficialWechatClient(
      String appId, String secret, ObjectMapper json, HttpClient http, String endpoint) {
    this(appId, secret, json, http);
    this.endpoint = endpoint;
  }

  @Override
  public String exchange(String code) {
    if (appId.isBlank() || secret.isBlank()) throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
    URI uri =
        UriComponentsBuilder.fromUriString(endpoint)
            .queryParam("appid", appId)
            .queryParam("secret", secret)
            .queryParam("js_code", code)
            .queryParam("grant_type", "authorization_code")
            .build()
            .encode()
            .toUri();
    var future =
        http.sendAsync(
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    try {
      // 对“完整响应体”设置总期限，避免只收到响应头后正文一直不结束。
      var response = future.get(5, TimeUnit.SECONDS);
      return parse(response.statusCode(), response.body());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
    } catch (TimeoutException | ExecutionException e) {
      future.cancel(true);
      // 异常消息可能含完整URI，不能传播或记录。
      throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
    }
  }

  String parse(int status, String body) {
    if (status != 200) throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
    try {
      var data = json.readTree(body);
      if (data == null || !data.isObject()) throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
      int err = data.path("errcode").asInt(0);
      if (err == 40029 || err == 40163 || err == 40226)
        throw new BusinessException(ErrorCode.WECHAT_CODE_INVALID);
      if (err != 0) throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
      var openid = data.path("openid");
      if (!openid.isTextual() || openid.asText().isBlank() || openid.asText().length() > 128)
        throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
      return openid.asText();
    } catch (java.io.IOException e) {
      throw new BusinessException(ErrorCode.RETRYABLE_ERROR);
    }
  }
}
