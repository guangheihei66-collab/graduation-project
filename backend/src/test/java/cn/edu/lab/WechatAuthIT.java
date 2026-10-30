package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.edu.lab.auth.wechat.*;
import cn.edu.lab.common.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WechatAuthIT {
  private static MySQLContainer<?> mysql;
  private static final String PASSWORD = UUID.randomUUID().toString();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    mysql = new MySQLContainer<>("mysql:8.0.45").withCommand("--skip-log-bin");
    mysql.start();
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("app.jwt-secret", () -> "isolated-wechat-integration-only-key-1234567890");
  }

  @AfterAll
  static void close() {
    if (mysql != null) mysql.stop();
  }

  @MockitoBean WechatClient client;
  @org.springframework.boot.test.web.server.LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired SysUserMapper users;
  @Autowired PasswordEncoder encoder;
  @Autowired Flyway flyway;
  @Autowired JwtService jwt;
  SysUser student, other, admin, lab, disabled;

  @BeforeEach
  void fixtures() {
    reset(client);
    when(client.exchange(anyString())).thenReturn("wx-test-a");
    jdbc.execute("DROP TRIGGER IF EXISTS fail_ticket_delete");
    for (String table :
        List.of(
            "reservation_log",
            "reservation",
            "wx_bind_ticket",
            "equipment",
            "lab_admin",
            "laboratory",
            "sys_user")) jdbc.update("DELETE FROM " + table);
    student = user("student", "STUDENT", 1);
    other = user("other", "STUDENT", 1);
    admin = user("admin", "SYS_ADMIN", 1);
    lab = user("lab", "LAB_ADMIN", 1);
    disabled = user("disabled", "STUDENT", 0);
  }

  SysUser user(String name, String role, int enabled) {
    var u = new SysUser();
    u.username = name;
    u.realName = "脱敏测试";
    u.role = role;
    u.enabled = enabled;
    u.passwordHash = encoder.encode(PASSWORD);
    users.insert(u);
    return u;
  }

  ResultActions postJson(String path, Object body) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/" + path)
            .contentType("application/json")
            .content(json.writeValueAsString(body)));
  }

  JsonNode data(ResultActions a) throws Exception {
    return json.readTree(
            a.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .path("data");
  }

  String ticket() throws Exception {
    return data(postJson("wechat/login", Map.of("code", UUID.randomUUID().toString())))
        .path("bindTicket")
        .asText();
  }

  ResultActions bind(String t, SysUser u, String pass) throws Exception {
    return postJson(
        "wechat/bind", Map.of("bindTicket", t, "username", u.username, "password", pass));
  }

  void bound(SysUser u, String openid) {
    u.wxOpenid = openid;
    users.updateById(u);
  }

  @Test
  void boundStudentGetsExistingJwt() throws Exception {
    bound(student, "wx-test-a");
    var d = data(postJson("wechat/login", Map.of("code", "valid")));
    assertFalse(d.path("needBind").asBoolean());
    assertEquals(student.id, jwt.verify(d.path("accessToken").asText()));
    assertEquals(7200, d.path("expiresIn").asInt());
    assertFalse(d.has("bindTicket"));
  }

  @Test
  void unboundGetsHashedShortTicket() throws Exception {
    String t = ticket();
    assertEquals(43, t.length());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM wx_bind_ticket WHERE ticket_hash=?",
            Integer.class,
            WechatAuthService.hash(t)));
    assertEquals(
        300,
        jdbc.queryForObject(
            "SELECT TIMESTAMPDIFF(SECOND,created_at,expires_at) FROM wx_bind_ticket",
            Integer.class));
    assertNotEquals(t, jdbc.queryForObject("SELECT ticket_hash FROM wx_bind_ticket", String.class));
  }

  @Test
  void bindAndLoginAgain() throws Exception {
    var d = data(bind(ticket(), student, PASSWORD));
    assertEquals(student.id, jwt.verify(d.path("accessToken").asText()));
    assertEquals("wx-test-a", users.selectById(student.id).wxOpenid);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_bind_ticket", Integer.class));
    data(postJson("wechat/login", Map.of("code", "new-code"))).path("needBind");
    postJson("wechat/login", Map.of("code", "new-code-2"))
        .andExpect(jsonPath("$.data.needBind").value(false));
  }

  @Test
  void expiredTicket() throws Exception {
    String t = ticket();
    jdbc.update(
        "UPDATE wx_bind_ticket SET created_at=NOW()-INTERVAL 10 MINUTE,expires_at=NOW()-INTERVAL 5"
            + " MINUTE");
    bind(t, student, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BIND_TICKET_INVALID"));
    assertNull(users.selectById(student.id).wxOpenid);
  }

  @Test
  void consumedTicketCannotBindAnotherStudent() throws Exception {
    String t = ticket();
    data(bind(t, student, PASSWORD));
    bind(t, other, PASSWORD).andExpect(status().isUnauthorized());
    assertNull(users.selectById(other.id).wxOpenid);
  }

  @Test
  void wrongPasswordDoesNotConsume() throws Exception {
    String t = ticket();
    bind(t, student, "wrong-password").andExpect(status().isUnauthorized());
    data(bind(t, student, PASSWORD));
  }

  @Test
  void unknownAccount() throws Exception {
    postJson(
            "wechat/bind",
            Map.of("bindTicket", ticket(), "username", "missing", "password", PASSWORD))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void disabledCannotBind() throws Exception {
    bind(ticket(), disabled, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void disabledCannotWechatLogin() throws Exception {
    bound(disabled, "wx-test-a");
    postJson("wechat/login", Map.of("code", "valid")).andExpect(status().isUnauthorized());
  }

  @Test
  void systemAdminCannotBind() throws Exception {
    bind(ticket(), admin, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void labAdminCannotBind() throws Exception {
    bind(ticket(), lab, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void existingStudentBindingNotOverwritten() throws Exception {
    bound(student, "wx-original");
    bind(ticket(), student, PASSWORD).andExpect(status().isConflict());
    assertEquals("wx-original", users.selectById(student.id).wxOpenid);
  }

  @Test
  void openidBoundSinceTicketCreated() throws Exception {
    String t = ticket();
    bound(other, "wx-test-a");
    bind(t, student, PASSWORD).andExpect(status().isConflict());
    assertNull(users.selectById(student.id).wxOpenid);
    assertEquals("wx-test-a", users.selectById(other.id).wxOpenid);
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_bind_ticket", Integer.class));
  }

  @Test
  void invalidWechatCode() throws Exception {
    when(client.exchange(anyString()))
        .thenThrow(new BusinessException(ErrorCode.WECHAT_CODE_INVALID));
    postJson("wechat/login", Map.of("code", "invalid")).andExpect(status().isUnauthorized());
  }

  @Test
  void remoteFailure503() throws Exception {
    when(client.exchange(anyString())).thenThrow(new BusinessException(ErrorCode.RETRYABLE_ERROR));
    postJson("wechat/login", Map.of("code", "valid")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void clientOpenidRejected() throws Exception {
    postJson("wechat/login", Map.of("code", "valid", "openid", "forged"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingCodeRejected() throws Exception {
    postJson("wechat/login", Map.of()).andExpect(status().isBadRequest());
  }

  @Test
  void invalidTicketFormatRejected() throws Exception {
    bind("forged", student, PASSWORD).andExpect(status().isBadRequest());
  }

  @Test
  void secretsNotInApiResponse() throws Exception {
    String t = ticket();
    var response =
        bind(t, student, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (String secret : List.of("wx-test-a", "wxOpenid", "session_key", "passwordHash", PASSWORD))
      assertFalse(response.contains(secret));
  }

  @Test
  void adminPasswordLoginStillWorks() throws Exception {
    data(postJson("login", Map.of("username", admin.username, "password", PASSWORD)));
  }

  @Test
  void studentJwtCannotManageOrReadOthers() throws Exception {
    String token = data(bind(ticket(), student, PASSWORD)).path("accessToken").asText();
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations/mine").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/reservations/1/review")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void freshJwtUsesRealReservationFlow() throws Exception {
    String token = data(bind(ticket(), student, PASSWORD)).path("accessToken").asText();
    jdbc.update(
        "INSERT INTO laboratory(code,name,location,enabled,created_at,updated_at)"
            + " VALUES('test-lab','实验室','测试',1,NOW(),NOW())");
    Long labId = jdbc.queryForObject("SELECT id FROM laboratory", Long.class);
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " VALUES(?,'test-asset','测试设备','OTHER','测试','AVAILABLE',NOW(),NOW())",
        labId);
    String eq = jdbc.queryForObject("SELECT CAST(id AS CHAR) FROM equipment", String.class);
    var start =
        LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .plusDays(1)
            .atTime(9, 0)
            .atOffset(ZoneOffset.ofHours(8));
    String body =
        json.writeValueAsString(
            Map.of(
                "equipmentId",
                eq,
                "startTime",
                start.toString(),
                "endTime",
                start.plusHours(1).toString(),
                "purpose",
                "联调验证"));
    String key = UUID.randomUUID().toString();
    var result =
        mvc.perform(
                post("/api/v1/reservations")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String id = json.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    mvc.perform(
            post("/api/v1/reservations")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id));
    mvc.perform(
            get("/api/v1/equipment/" + eq + "/slots")
                .param("date", start.toLocalDate().toString())
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.slots.length()").value(1));
    mvc.perform(
            get("/api/v1/reservations/" + id)
                .header("Authorization", "Bearer " + jwt.issue(other.id)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/reservations/" + id + "/cancel")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_log", Integer.class));
  }

  @Test
  void v3RepeatMigrationPreservesUsers() {
    assertEquals(3, flyway.info().applied().length);
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertNotNull(users.selectById(student.id));
  }

  @Test
  void consumeFailureRollsBackBinding() throws Exception {
    String t = ticket();
    jdbc.execute(
        "CREATE TRIGGER fail_ticket_delete BEFORE DELETE ON wx_bind_ticket FOR EACH ROW SIGNAL"
            + " SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
    try {
      bind(t, student, PASSWORD).andExpect(status().isServiceUnavailable());
      assertNull(users.selectById(student.id).wxOpenid);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_bind_ticket", Integer.class));
    } finally {
      jdbc.execute("DROP TRIGGER fail_ticket_delete");
    }
  }

  @Test
  void concurrentSameTicketSingleWinner() throws Exception {
    concurrent(false, false);
  }

  @Test
  void concurrentDifferentTicketsSameOpenid() throws Exception {
    concurrent(true, false);
  }

  @Test
  void concurrentSameStudentDifferentOpenids() throws Exception {
    concurrent(true, true);
  }

  void concurrent(boolean twoTickets, boolean sameStudent) throws Exception {
    String a = ticket();
    if (sameStudent) when(client.exchange(anyString())).thenReturn("wx-test-b");
    String b = twoTickets ? ticket() : a;
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var x =
          pool.submit(
              () -> {
                gate.await();
                return bind(a, student, PASSWORD).andReturn().getResponse().getStatus();
              });
      var y =
          pool.submit(
              () -> {
                gate.await();
                return bind(b, sameStudent ? student : other, PASSWORD)
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      gate.countDown();
      var codes = List.of(x.get(20, TimeUnit.SECONDS), y.get(20, TimeUnit.SECONDS));
      assertEquals(1, codes.stream().filter(c -> c == 200).count());
      assertTrue(codes.stream().allMatch(c -> c == 200 || c == 401 || c == 409));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM sys_user WHERE wx_openid IS NOT NULL", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void wechatHttpRunsOutsideTransaction() throws Exception {
    when(client.exchange(anyString()))
        .thenAnswer(
            invocation -> {
              assertFalse(
                  org.springframework.transaction.support.TransactionSynchronizationManager
                      .isActualTransactionActive());
              return "wx-test-a";
            });
    ticket();
  }

  @Test
  void miniappProductionServicesOverRealHttp() throws Exception {
    jdbc.update(
        "INSERT INTO laboratory(code,name,location,enabled,created_at,updated_at)"
            + " VALUES('http-lab','HTTP实验室','测试',1,NOW(),NOW())");
    Long labId = jdbc.queryForObject("SELECT id FROM laboratory", Long.class);
    jdbc.update(
        "INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES(?,?,NOW())", labId, lab.id);
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " VALUES(?,'http-equipment','HTTP设备','OTHER','测试','AVAILABLE',NOW(),NOW())",
        labId);
    String equipmentId =
        jdbc.queryForObject("SELECT CAST(id AS CHAR) FROM equipment", String.class);
    var script = java.nio.file.Path.of("../miniapp/tests/real-backend.js").toAbsolutePath();
    var process = new ProcessBuilder("node", script.toString());
    process.environment().put("MINIAPP_TEST_API_URL", "http://127.0.0.1:" + port + "/api/v1");
    process.environment().put("MINIAPP_TEST_PASSWORD", PASSWORD);
    process.environment().put("MINIAPP_TEST_EQUIPMENT_ID", equipmentId);
    var log = java.nio.file.Files.createTempFile("miniapp-http-", ".log");
    process.redirectErrorStream(true).redirectOutput(log.toFile());
    var running = process.start();
    try {
      boolean ended = running.waitFor(60, TimeUnit.SECONDS);
      if (!ended) running.destroyForcibly();
      assertTrue(ended, "小程序HTTP逻辑联调超时");
      // 不把子进程可能包含凭据的原始日志写入公开测试报告。
      assertEquals(0, running.exitValue(), "小程序HTTP联调失败，需在隔离环境检查临时日志");
      assertEquals(
          "CANCELLED", jdbc.queryForObject("SELECT status FROM reservation", String.class));
      assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_log", Integer.class));
    } finally {
      java.nio.file.Files.deleteIfExists(log);
    }
  }
}
