package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.edu.lab.common.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReservationCoreIT.TimeConfig.class)
class ReservationCoreIT {
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.45").withCommand("--skip-log-bin");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    MYSQL.start();
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
  }

  @AfterAll
  static void stop() {
    MYSQL.stop();
  }

  static class MutableClock extends Clock {
    final AtomicReference<Instant> time =
        new AtomicReference<>(Instant.parse("2026-11-02T00:00:00Z"));

    public ZoneId getZone() {
      return ZoneId.of("Asia/Shanghai");
    }

    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    public Instant instant() {
      return time.get();
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired SysUserMapper users;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtService jwt;
  @Autowired MutableClock clock;
  @Autowired PlatformTransactionManager manager;
  SysUser s1, s2, admin, other, sys;
  long l1, l2, e1, e2, e3, e4;
  static String hash;

  @BeforeEach
  void fixtures() {
    clock.time.set(Instant.parse("2026-11-02T00:00:00Z"));
    jdbc.update("DELETE FROM reservation_log");
    jdbc.update("DELETE FROM reservation");
    jdbc.update("DELETE FROM equipment");
    jdbc.update("DELETE FROM lab_admin");
    jdbc.update("DELETE FROM laboratory");
    jdbc.update("DELETE FROM sys_user");
    if (hash == null) hash = passwords.encode(UUID.randomUUID().toString());
    s1 = user("s1", "STUDENT");
    s2 = user("s2", "STUDENT");
    admin = user("admin", "LAB_ADMIN");
    other = user("other", "LAB_ADMIN");
    sys = user("sys", "SYS_ADMIN");
    l1 = lab("L1");
    l2 = lab("L2");
    jdbc.update(
        "INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES (?,?,NOW(3)),(?,?,NOW(3))",
        l1,
        admin.id,
        l2,
        other.id);
    e1 = equipment(l1, "E1");
    e2 = equipment(l1, "E2");
    e3 = equipment(l2, "E3");
    e4 = equipment(l2, "E4");
  }

  SysUser user(String name, String role) {
    var u = new SysUser();
    u.username = name;
    u.realName = "测试" + name;
    u.role = role;
    u.enabled = 1;
    u.passwordHash = hash;
    users.insert(u);
    return u;
  }

  long lab(String code) {
    jdbc.update(
        "INSERT INTO laboratory(code,name,location,enabled,created_at,updated_at) VALUES"
            + " (?,?,'测试',1,NOW(3),NOW(3))",
        code,
        code);
    return jdbc.queryForObject("SELECT id FROM laboratory WHERE code=?", Long.class, code);
  }

  long equipment(long lab, String asset) {
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " VALUES (?,?,?,'TOOL','测试','AVAILABLE',NOW(3),NOW(3))",
        lab,
        asset,
        asset);
    return jdbc.queryForObject("SELECT id FROM equipment WHERE asset_no=?", Long.class, asset);
  }

  ResultActions call(MockHttpServletRequestBuilder request, SysUser user, Object body)
      throws Exception {
    request.header("Authorization", "Bearer " + jwt.issue(user.id));
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  JsonNode data(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
  }

  long seed(long eq, SysUser student, String start, String end, String status) {
    String key = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " reservation(equipment_id,student_id,start_time,end_time,purpose,status,idempotency_key,request_hash,created_at,updated_at)"
            + " VALUES (?,?,?,?,'测试用途',?,?,?, ?,?)",
        eq,
        student.id,
        LocalDateTime.parse(start),
        LocalDateTime.parse(end),
        status,
        key,
        "a".repeat(64),
        LocalDateTime.of(2026, 11, 2, 8, 0),
        LocalDateTime.of(2026, 11, 2, 8, 0));
    return jdbc.queryForObject(
        "SELECT id FROM reservation WHERE student_id=? AND idempotency_key=?",
        Long.class,
        student.id,
        key);
  }

  @Test
  void aMigrationAndConstraints() {
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
    long id = seed(e1, s1, "2026-11-03T09:00", "2026-11-03T10:00", "PENDING");
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> jdbc.update("UPDATE reservation SET end_time=start_time WHERE id=?", id));
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> jdbc.update("UPDATE reservation SET status='UNKNOWN' WHERE id=?", id));
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> jdbc.update("UPDATE reservation SET student_id=-1 WHERE id=?", id));
    long second = seed(e2, s1, "2026-11-03T09:00", "2026-11-03T10:00", "PENDING");
    String key =
        jdbc.queryForObject("SELECT idempotency_key FROM reservation WHERE id=?", String.class, id);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE reservation SET idempotency_key=? WHERE id=?", key, second));
  }

  @Test
  void aQueryScopePrivacyAndEffectiveExpiry() throws Exception {
    long mine = seed(e1, s1, "2026-11-03T09:00", "2026-11-03T10:00", "PENDING");
    seed(e3, s2, "2026-11-03T09:00", "2026-11-03T10:00", "APPROVED");
    call(get("/api/v1/reservations/mine"), s1, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").isString());
    call(get("/api/v1/reservations/" + mine), s2, null).andExpect(status().isNotFound());
    call(get("/api/v1/reservations"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    call(get("/api/v1/reservations"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));
    var slots =
        data(
            call(get("/api/v1/equipment/" + e1 + "/slots").param("date", "2026-11-03"), s2, null)
                .andExpect(status().isOk()));
    assertEquals(1, slots.get("slots").size());
    assertEquals(
        Set.of("startTime", "endTime", "status"),
        json.convertValue(slots.get("slots").get(0), java.util.Map.class).keySet());
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    call(get("/api/v1/reservations/mine").param("status", "EXPIRED"), s1, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    call(get("/api/v1/equipment/" + e1 + "/slots").param("date", "2026-11-03"), s2, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slots.length()").value(0));
  }

  Map<String, Object> request(long equipment, String start, String end) {
    return Map.of(
        "equipmentId",
        Long.toString(equipment),
        "startTime",
        start + "+08:00",
        "endTime",
        end + "+08:00",
        "purpose",
        "  课程实验  ");
  }

  ResultActions submit(SysUser user, long eq, String start, String end, String key)
      throws Exception {
    return call(
        post("/api/v1/reservations").header("Idempotency-Key", key), user, request(eq, start, end));
  }

  long create(SysUser user, long eq, String start, String end) throws Exception {
    return data(submit(user, eq, start, end, UUID.randomUUID().toString())
            .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  @Test
  void bValidAdjacentAndDifferentEquipment() throws Exception {
    long first = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    create(s1, e1, "2026-11-03T10:00", "2026-11-03T11:00");
    create(s1, e3, "2026-11-03T09:00", "2026-11-03T10:00");
    assertEquals(
        "PENDING",
        jdbc.queryForObject("SELECT status FROM reservation WHERE id=?", String.class, first));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reservation_log WHERE action='CREATE'", Integer.class));
    assertEquals(
        "课程实验",
        jdbc.queryForObject("SELECT purpose FROM reservation WHERE id=?", String.class, first));
  }

  @Test
  void bTimeValidationBoundariesAndInput() throws Exception {
    String[][] invalid = {
      {"2026-11-01T09:00", "2026-11-01T10:00"},
      {"2026-11-02T08:00", "2026-11-02T09:00"},
      {"2026-11-10T09:00", "2026-11-10T10:00"},
      {"2026-11-03T21:30", "2026-11-04T08:00"},
      {"2026-11-03T09:15", "2026-11-03T10:00"},
      {"2026-11-03T09:00", "2026-11-03T09:15"},
      {"2026-11-03T09:00", "2026-11-03T13:30"},
      {"2026-11-03T07:30", "2026-11-03T08:00"},
      {"2026-11-03T21:30", "2026-11-03T22:30"},
      {"2026-11-03T10:00", "2026-11-03T09:00"}
    };
    for (var pair : invalid)
      submit(s1, e1, pair[0], pair[1], UUID.randomUUID().toString())
          .andExpect(status().isBadRequest());
    var body = new HashMap<>(request(e1, "2026-11-03T09:00", "2026-11-03T10:00"));
    for (String bad :
        List.of("2026-11-03T09:00", "2026-11-03T01:00Z", "2026-11-03T09:00+09:00", "not-time")) {
      body.put("startTime", bad);
      call(
              post("/api/v1/reservations").header("Idempotency-Key", UUID.randomUUID().toString()),
              s1,
              body)
          .andExpect(status().isBadRequest());
    }
    body.put("startTime", "2026-11-03T09:00+08:00");
    body.put("purpose", " ");
    call(
            post("/api/v1/reservations").header("Idempotency-Key", UUID.randomUUID().toString()),
            s1,
            body)
        .andExpect(status().isBadRequest());
    call(post("/api/v1/reservations"), s1, request(e1, "2026-11-03T09:00", "2026-11-03T10:00"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bAllOverlapShapesConflict() throws Exception {
    create(s1, e1, "2026-11-03T10:00", "2026-11-03T11:00");
    String[][] pairs = {
      {"10:00", "11:00"}, {"09:30", "10:30"}, {"09:30", "11:30"}, {"10:00", "10:30"}
    };
    for (var pair : pairs)
      submit(s2, e1, "2026-11-03T" + pair[0], "2026-11-03T" + pair[1], UUID.randomUUID().toString())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("TIME_CONFLICT"));
  }

  @Test
  void bInactiveAndOverduePendingDoNotOccupyQuotaOrSlots() throws Exception {
    seed(e1, s1, "2026-11-03T09:00", "2026-11-03T10:00", "REJECTED");
    seed(e1, s1, "2026-11-03T09:00", "2026-11-03T10:00", "CANCELLED");
    seed(e1, s1, "2026-11-02T07:00", "2026-11-02T12:00", "PENDING");
    create(s1, e1, "2026-11-02T09:00", "2026-11-02T10:00");
    create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
  }

  @Test
  void bIdempotencyCanonicalHashAndConflict() throws Exception {
    String key = UUID.randomUUID().toString();
    long id =
        data(submit(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00", key)
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    var canonical = request(e1, "2026-11-03T09:00:00", "2026-11-03T10:00:00");
    call(post("/api/v1/reservations").header("Idempotency-Key", key.toUpperCase()), s1, canonical)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(Long.toString(id)));
    submit(s1, e1, "2026-11-03T10:00", "2026-11-03T11:00", key)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_log", Integer.class));
  }

  @Test
  void bUnavailableResourcesAndRoleDenial() throws Exception {
    jdbc.update("UPDATE equipment SET status='MAINTENANCE' WHERE id=?", e1);
    submit(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00", UUID.randomUUID().toString())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESOURCE_UNAVAILABLE"));
    jdbc.update("UPDATE laboratory SET enabled=0 WHERE id=?", l2);
    submit(s1, e3, "2026-11-03T09:00", "2026-11-03T10:00", UUID.randomUUID().toString())
        .andExpect(status().isConflict());
    submit(sys, e2, "2026-11-03T09:00", "2026-11-03T10:00", UUID.randomUUID().toString())
        .andExpect(status().isForbidden());
  }

  List<Integer> parallel(int n, java.util.function.IntFunction<Integer> action) throws Exception {
    var executor = Executors.newFixedThreadPool(n);
    var gate = new CyclicBarrier(n);
    var futures = new ArrayList<Future<Integer>>();
    try {
      for (int i = 0; i < n; i++) {
        final int ix = i;
        futures.add(
            executor.submit(
                () -> {
                  gate.await(10, TimeUnit.SECONDS);
                  return action.apply(ix);
                }));
      }
      var results = new ArrayList<Integer>();
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  int submitStatus(SysUser student, long eq, String key) {
    try {
      return submit(student, eq, "2026-11-03T09:00", "2026-11-03T10:00", key)
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  void bConcurrentSameSlotOnlyOneWins() throws Exception {
    var result = parallel(2, i -> submitStatus(i == 0 ? s1 : s2, e1, UUID.randomUUID().toString()));
    assertEquals(1, Collections.frequency(result, 201));
    assertEquals(1, Collections.frequency(result, 409));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
  }

  @Test
  void bConcurrentSameKeyAndDifferentPayloads() throws Exception {
    String key = UUID.randomUUID().toString();
    var repeat = parallel(6, i -> submitStatus(s1, e1, key));
    assertEquals(1, Collections.frequency(repeat, 201));
    assertEquals(5, Collections.frequency(repeat, 200));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_log", Integer.class));
    var competing = parallel(2, i -> submitStatus(s2, i == 0 ? e2 : e3, key));
    assertEquals(1, Collections.frequency(competing, 201));
    assertEquals(1, Collections.frequency(competing, 409));
  }

  @Test
  void bCrossLabConcurrentQuotaCannotExceedThree() throws Exception {
    long[] ids = {e1, e2, e3, e4};
    var result = parallel(4, i -> submitStatus(s1, ids[i], UUID.randomUUID().toString()));
    assertEquals(3, Collections.frequency(result, 201));
    assertEquals(1, Collections.frequency(result, 409));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reservation WHERE student_id=?", Integer.class, s1.id));
  }

  @Test
  void bTwentyContendersProduceOneBooking() throws Exception {
    var students = new ArrayList<SysUser>();
    for (int i = 0; i < 20; i++) students.add(user("load" + i, "STUDENT"));
    var result = parallel(20, i -> submitStatus(students.get(i), e1, UUID.randomUUID().toString()));
    assertEquals(1, Collections.frequency(result, 201));
    assertEquals(19, Collections.frequency(result, 409));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_log", Integer.class));
    System.out.println("并发验证：20 请求，成功 1，冲突 19，其他错误 0；预约及审计各 1 条，无重复占用。");
  }

  @Autowired cn.edu.lab.reservation.service.ExpiryJob expiry;

  ResultActions action(long id, String action, SysUser user, Object body) throws Exception {
    return call(post("/api/v1/reservations/" + id + "/" + action), user, body);
  }

  String state(long id) {
    return jdbc.queryForObject("SELECT status FROM reservation WHERE id=?", String.class, id);
  }

  int logCount(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM reservation_log WHERE reservation_id=?", Integer.class, id);
  }

  @Test
  void cApproveReplayAndCancelStateMachine() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(id, "review", admin, Map.of("decision", "APPROVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));
    action(id, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    assertEquals(2, logCount(id));
    action(id, "review", admin, Map.of("decision", "REJECT", "reason", "不同结果"))
        .andExpect(status().isConflict());
    action(id, "cancel", s1, null).andExpect(status().isOk());
    action(id, "cancel", s1, null).andExpect(status().isOk());
    assertEquals("CANCELLED", state(id));
    assertEquals(3, logCount(id));
    action(id, "revoke", admin, Map.of("reason", "不能混淆取消和撤销")).andExpect(status().isConflict());
  }

  @Test
  void cRejectRevokeAndReleasedTimes() throws Exception {
    long rejected = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(rejected, "review", admin, Map.of("decision", "REJECT"))
        .andExpect(status().isBadRequest());
    action(rejected, "review", admin, Map.of("decision", "REJECT", "reason", "用途不符"))
        .andExpect(status().isOk());
    assertEquals("REJECTED", state(rejected));
    long revoked = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(revoked, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    action(revoked, "revoke", admin, Map.of("reason", "设备检查")).andExpect(status().isOk());
    action(revoked, "revoke", admin, Map.of("reason", "设备检查")).andExpect(status().isOk());
    assertEquals(3, logCount(revoked));
    create(s2, e1, "2026-11-03T09:00", "2026-11-03T10:00");
  }

  @Test
  void cCompleteOnlyAfterEndAndKeepHistory() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(id, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    action(id, "complete", admin, null).andExpect(status().isConflict());
    clock.time.set(Instant.parse("2026-11-03T02:00:00Z"));
    action(id, "cancel", s1, null).andExpect(status().isConflict());
    action(id, "complete", admin, null).andExpect(status().isOk());
    action(id, "complete", admin, null).andExpect(status().isOk());
    assertEquals("COMPLETED", state(id));
    assertEquals(3, logCount(id));
  }

  @Test
  void cExpiryRepeatAndAudit() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    action(id, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isConflict());
    assertEquals(1, expiry.runOnce());
    assertEquals(0, expiry.runOnce());
    assertEquals("EXPIRED", state(id));
    assertEquals(2, logCount(id));
    assertNull(
        jdbc.queryForObject(
            "SELECT operator_id FROM reservation_log WHERE action='EXPIRE'", Long.class));
    action(id, "cancel", s1, null).andExpect(status().isConflict());
  }

  @Test
  void cPermissionsAndHistoryAfterDisable() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(id, "cancel", s2, null).andExpect(status().isNotFound());
    action(id, "review", other, Map.of("decision", "APPROVE")).andExpect(status().isForbidden());
    action(id, "review", sys, Map.of("decision", "APPROVE")).andExpect(status().isForbidden());
    action(id, "cancel", s1, null).andExpect(status().isOk());
    call(patch("/api/v1/labs/" + l1), sys, Map.of("enabled", 0)).andExpect(status().isOk());
    call(get("/api/v1/reservations/" + id), s1, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.logs.length()").value(2));
  }

  @Test
  void cReplayAfterCancelTimeAndResourceChanges() throws Exception {
    String key = UUID.randomUUID().toString();
    long id =
        data(submit(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00", key)
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    action(id, "cancel", s1, null).andExpect(status().isOk());
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "DISABLED"))
        .andExpect(status().isOk());
    clock.time.set(Instant.parse("2026-11-05T00:00:00Z"));
    submit(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00", key)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
    assertEquals(2, logCount(id));
  }

  @Test
  void cActualResourceGuardsBlockPendingAndApproved() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "MAINTENANCE"))
        .andExpect(status().isConflict());
    call(patch("/api/v1/labs/" + l1), sys, Map.of("enabled", 0)).andExpect(status().isConflict());
    action(id, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "DISABLED"))
        .andExpect(status().isConflict());
    action(id, "revoke", admin, Map.of("reason", "释放")).andExpect(status().isOk());
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "DISABLED"))
        .andExpect(status().isOk());
    call(patch("/api/v1/labs/" + l1), sys, Map.of("enabled", 0)).andExpect(status().isOk());
  }

  @Test
  void cCreateAuditFailureRollsBackEverything() throws Exception {
    jdbc.execute(
        "CREATE TRIGGER stage3_fail_log BEFORE INSERT ON reservation_log FOR EACH ROW SIGNAL"
            + " SQLSTATE '45000' SET MESSAGE_TEXT='test audit failure'");
    try {
      submit(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00", UUID.randomUUID().toString())
          .andExpect(status().isServiceUnavailable());
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class));
    } finally {
      jdbc.execute("DROP TRIGGER stage3_fail_log");
    }
  }

  @Test
  void cStateAuditFailureRollsBackStateAndExpiry() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    jdbc.execute(
        "CREATE TRIGGER stage3_fail_log BEFORE INSERT ON reservation_log FOR EACH ROW SIGNAL"
            + " SQLSTATE '45000' SET MESSAGE_TEXT='test audit failure'");
    try {
      action(id, "review", admin, Map.of("decision", "APPROVE"))
          .andExpect(status().isServiceUnavailable());
      assertEquals("PENDING", state(id));
      assertEquals(1, logCount(id));
      clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
      assertEquals(0, expiry.runOnce());
      assertEquals("PENDING", state(id));
      assertEquals(1, logCount(id));
    } finally {
      jdbc.execute("DROP TRIGGER stage3_fail_log");
    }
    assertEquals(1, expiry.runOnce());
    assertEquals("EXPIRED", state(id));
  }

  int actionStatus(long id, String name, SysUser actor, Object body) {
    try {
      return action(id, name, actor, body).andReturn().getResponse().getStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void cApproveCancelRaceIsLegal() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    var result =
        parallel(
            2,
            i ->
                i == 0
                    ? actionStatus(id, "review", admin, Map.of("decision", "APPROVE"))
                    : actionStatus(id, "cancel", s1, null));
    assertTrue(result.get(0) == 200 || result.get(0) == 409);
    assertEquals(200, result.get(1));
    assertEquals("CANCELLED", state(id));
    assertEquals(result.get(0) == 200 ? 3 : 2, logCount(id));
  }

  @Test
  void cExpiryApprovalRaceAtBoundary() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    var result =
        parallel(
            2,
            i ->
                i == 0
                    ? actionStatus(id, "review", admin, Map.of("decision", "APPROVE"))
                    : expiry.runOnce());
    assertEquals(409, result.get(0));
    assertEquals(1, result.get(1));
    assertEquals("EXPIRED", state(id));
    assertEquals(2, logCount(id));
  }

  @Test
  void cEquipmentDisableCreationRace() throws Exception {
    resourceRace(false);
  }

  @Test
  void cLabDisableCreationRace() throws Exception {
    resourceRace(true);
  }

  void resourceRace(boolean laboratory) throws Exception {
    var result =
        parallel(
            2,
            i -> {
              if (i == 0) return submitStatus(s1, e1, UUID.randomUUID().toString());
              try {
                return call(
                        patch(laboratory ? "/api/v1/labs/" + l1 : "/api/v1/equipment/" + e1),
                        sys,
                        laboratory ? Map.of("enabled", 0) : Map.of("status", "DISABLED"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    assertTrue(
        result.equals(List.of(201, 409)) || result.equals(List.of(409, 200)), result.toString());
    if (result.get(0) == 201) {
      assertEquals(
          1, jdbc.queryForObject("SELECT enabled FROM laboratory WHERE id=?", Integer.class, l1));
      assertEquals(
          "AVAILABLE",
          jdbc.queryForObject("SELECT status FROM equipment WHERE id=?", String.class, e1));
    }
  }

  @Test
  void cApprovalRechecksAfterConcurrentAuthorizationRevocation() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    var tx = new TransactionTemplate(manager);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var revoke =
          executor.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        jdbc.queryForObject(
                            "SELECT id FROM laboratory WHERE id=? FOR UPDATE", Long.class, l1);
                        jdbc.update("DELETE FROM lab_admin WHERE lab_id=?", l1);
                        locked.countDown();
                        try {
                          if (!release.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("timeout");
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new RuntimeException(e);
                        }
                      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var review =
          executor.submit(
              () -> {
                started.countDown();
                return actionStatus(id, "review", admin, Map.of("decision", "APPROVE"));
              });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> review.get(250, TimeUnit.MILLISECONDS));
      release.countDown();
      revoke.get(5, TimeUnit.SECONDS);
      assertEquals(403, review.get(5, TimeUnit.SECONDS));
      assertEquals("PENDING", state(id));
      assertEquals(1, logCount(id));
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void dStatisticsMatchDatabaseAndScope() throws Exception {
    long a = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(a, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    long b = create(s2, e3, "2026-11-03T09:00", "2026-11-03T10:00");
    action(b, "review", other, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    create(s1, e2, "2026-11-03T09:00", "2026-11-03T10:00");
    long cancelled = create(s2, e4, "2026-11-03T09:00", "2026-11-03T10:00");
    action(cancelled, "cancel", s2, null).andExpect(status().isOk());
    String path = "/api/v1/statistics/overview";
    call(get(path).param("fromDate", "2026-11-01").param("toDate", "2026-11-03"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(4))
        .andExpect(jsonPath("$.data.pendingCount").value(1))
        .andExpect(jsonPath("$.data.approvedCount").value(2))
        .andExpect(jsonPath("$.data.dailyTrend[1].count").value(4))
        .andExpect(jsonPath("$.data.topEquipment.length()").value(2))
        .andExpect(jsonPath("$.data.topEquipment[0].equipmentId").isString());
    call(get(path).param("fromDate", "2026-11-02").param("toDate", "2026-11-02"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(2));
    call(
            get(path)
                .param("labId", Long.toString(l2))
                .param("fromDate", "2026-11-02")
                .param("toDate", "2026-11-02"),
            admin,
            null)
        .andExpect(status().isForbidden());
    call(get(path).param("fromDate", "2026-11-02").param("toDate", "2026-11-02"), s1, null)
        .andExpect(status().isForbidden());
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    call(get(path).param("fromDate", "2026-11-02").param("toDate", "2026-11-02"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pendingCount").value(0));
  }

  @Test
  void dStatisticsDateValidationAndEmptyDays() throws Exception {
    String path = "/api/v1/statistics/overview";
    call(get(path).param("fromDate", "2026-11-02").param("toDate", "2026-11-01"), sys, null)
        .andExpect(status().isBadRequest());
    call(get(path).param("fromDate", "2026-01-01").param("toDate", "2026-04-01"), sys, null)
        .andExpect(status().isBadRequest());
    call(get(path), sys, null).andExpect(status().isBadRequest());
    call(get(path).param("fromDate", "2026-01-01").param("toDate", "2026-03-31"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dailyTrend.length()").value(90))
        .andExpect(jsonPath("$.data.totalCount").value(0));
  }

  @Test
  void dQueryFiltersPaginationAndDisabledAccount() throws Exception {
    create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    create(s1, e2, "2026-11-03T09:00", "2026-11-03T10:00");
    call(
            get("/api/v1/reservations/mine")
                .param("size", "1")
                .param("page", "2")
                .param("equipmentId", Long.toString(e2))
                .param("status", "PENDING")
                .param("fromDate", "2026-11-03")
                .param("toDate", "2026-11-03"),
            s1,
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records.length()").value(0));
    call(get("/api/v1/reservations/mine").param("page", "0"), s1, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/reservations/mine").param("status", "BAD"), s1, null)
        .andExpect(status().isBadRequest());
    call(
            get("/api/v1/reservations/mine")
                .param("fromDate", "2026-11-04")
                .param("toDate", "2026-11-03"),
            s1,
            null)
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE sys_user SET enabled=0 WHERE id=?", s1.id);
    call(get("/api/v1/reservations/mine"), s1, null).andExpect(status().isUnauthorized());
  }

  @Test
  void dDifferentReviewerCannotClaimSameOperation() throws Exception {
    jdbc.update(
        "INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES (?,?,NOW(3))", l1, other.id);
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    action(id, "review", admin, Map.of("decision", "APPROVE")).andExpect(status().isOk());
    action(id, "review", other, Map.of("decision", "APPROVE")).andExpect(status().isConflict());
    assertEquals(2, logCount(id));
  }

  @Test
  void dOverduePendingDoesNotPreventResourceDisable() throws Exception {
    long id = create(s1, e1, "2026-11-03T09:00", "2026-11-03T10:00");
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "MAINTENANCE"))
        .andExpect(status().isOk());
    call(patch("/api/v1/labs/" + l1), sys, Map.of("enabled", 0)).andExpect(status().isOk());
    assertEquals(1, expiry.runOnce());
    assertEquals("EXPIRED", state(id));
  }

  @Test
  void dExpiryProcessesMoreThanOneBatch() throws Exception {
    var args = new ArrayList<Object[]>();
    for (int i = 0; i < 201; i++) args.add(new Object[] {e1, s1.id, UUID.randomUUID().toString()});
    jdbc.batchUpdate(
        "INSERT INTO"
            + " reservation(equipment_id,student_id,start_time,end_time,purpose,status,idempotency_key,request_hash,created_at,updated_at)"
            + " VALUES (?,?,'2026-11-03 09:00:00','2026-11-03"
            + " 10:00:00','过期测试','PENDING',?,REPEAT('b',64),'2026-11-02 08:00:00','2026-11-02"
            + " 08:00:00')",
        args);
    clock.time.set(Instant.parse("2026-11-03T01:00:00Z"));
    assertEquals(201, expiry.runOnce());
    assertEquals(0, expiry.runOnce());
    assertEquals(
        201,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reservation_log WHERE action='EXPIRE'", Integer.class));
  }
}
