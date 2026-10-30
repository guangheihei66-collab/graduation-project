package cn.edu.lab.auth.wechat;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.lab.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class OfficialWechatClientTest {
  private final OfficialWechatClient client =
      new OfficialWechatClient("test-app", "test-secret", new ObjectMapper());

  private void fails(ErrorCode code, Runnable call) {
    assertEquals(code, assertThrows(BusinessException.class, call::run).code);
  }

  @Test
  void successIgnoresSessionKey() {
    assertEquals(
        "test-openid",
        client.parse(200, "{\"openid\":\"test-openid\",\"session_key\":\"do-not-return\"}"));
  }

  @Test
  void invalidCode() {
    fails(ErrorCode.WECHAT_CODE_INVALID, () -> client.parse(200, "{\"errcode\":40029}"));
  }

  @Test
  void reusedCode() {
    fails(ErrorCode.WECHAT_CODE_INVALID, () -> client.parse(200, "{\"errcode\":40163}"));
  }

  @Test
  void remoteError() {
    fails(ErrorCode.RETRYABLE_ERROR, () -> client.parse(200, "{\"errcode\":-1}"));
  }

  @Test
  void missingOpenid() {
    fails(ErrorCode.RETRYABLE_ERROR, () -> client.parse(200, "{\"session_key\":\"hidden\"}"));
  }

  @Test
  void malformedJson() {
    fails(ErrorCode.RETRYABLE_ERROR, () -> client.parse(200, "not-json"));
  }

  @Test
  void httpFailure() {
    fails(ErrorCode.RETRYABLE_ERROR, () -> client.parse(500, "{}"));
  }

  @Test
  void missingConfig() {
    fails(
        ErrorCode.RETRYABLE_ERROR,
        () -> new OfficialWechatClient("", "", new ObjectMapper()).exchange("code"));
  }

  @Test
  void officialProtocolOverMockHttpServer() throws Exception {
    var query = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/sns/jscode2session",
        e -> {
          query.set(e.getRequestURI().getRawQuery());
          byte[] bytes = "{\"openid\":\"server-only\",\"session_key\":\"hidden\"}".getBytes();
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    try {
      var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
      var c =
          new OfficialWechatClient(
              "test-app",
              "test-secret",
              new ObjectMapper(),
              http,
              "http://127.0.0.1:" + server.getAddress().getPort() + "/sns/jscode2session");
      assertEquals("server-only", c.exchange("one-time-code"));
      assertTrue(query.get().contains("grant_type=authorization_code"));
      assertTrue(query.get().contains("js_code=one-time-code"));
      assertTrue(query.get().contains("appid=test-app"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void timeoutIsSanitized() throws Exception {
    HttpClient http = org.mockito.Mockito.mock(HttpClient.class);
    org.mockito.Mockito.when(
            http.sendAsync(
                org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            java.util.concurrent.CompletableFuture.failedFuture(
                new HttpTimeoutException("sensitive URI")));
    var c = new OfficialWechatClient("test-app", "test-secret", new ObjectMapper(), http);
    var e = assertThrows(BusinessException.class, () -> c.exchange("private-code"));
    assertEquals(ErrorCode.RETRYABLE_ERROR, e.code);
    assertNull(e.getCause());
    assertFalse(e.getMessage().contains("sensitive"));
  }

  @Test
  void stalledResponseBodyHasDeadline() throws Exception {
    var gate = new java.util.concurrent.CountDownLatch(1);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        e -> {
          e.sendResponseHeaders(200, 100);
          e.getResponseBody().write('{');
          e.getResponseBody().flush();
          try {
            gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          e.close();
        });
    server.start();
    try {
      var c =
          new OfficialWechatClient(
              "test-app",
              "test-secret",
              new ObjectMapper(),
              HttpClient.newHttpClient(),
              "http://127.0.0.1:" + server.getAddress().getPort() + "/");
      long start = System.nanoTime();
      fails(ErrorCode.RETRYABLE_ERROR, () -> c.exchange("unit-code"));
      assertTrue(java.time.Duration.ofNanos(System.nanoTime() - start).toSeconds() < 8);
    } finally {
      gate.countDown();
      server.stop(0);
    }
  }
}
