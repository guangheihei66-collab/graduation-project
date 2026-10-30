package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.edu.lab.common.DemoDataInitializer;
import cn.edu.lab.laboratory.mapper.LabAdminMapper;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationIT {
  private static final String KEY = "integration-test-only-32-byte-key-not-for-production-123456";
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static MySQLContainer<?> mysql;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    mysql = new MySQLContainer<>("mysql:8.0.45");
    mysql.start();
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("app.jwt-secret", () -> KEY);
  }

  @AfterAll
  static void closeContainer() {
    if (mysql != null) mysql.stop();
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired SysUserMapper users;
  @Autowired LabAdminMapper assignments;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtService jwt;
  @Autowired Flyway flyway;
  @Autowired ApplicationContext context;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;
  private SysUser admin, lab, student, disabled;

  @BeforeEach
  void fixtures() {
    jdbc.update("DELETE FROM equipment");
    jdbc.update("DELETE FROM lab_admin");
    jdbc.update("DELETE FROM laboratory");
    jdbc.update("DELETE FROM sys_user");
    admin = create("admin", "SYS_ADMIN", 1);
    lab = create("lab", "LAB_ADMIN", 1);
    student = create("student", "STUDENT", 1);
    disabled = create("disabled", "LAB_ADMIN", 0);
  }

  private SysUser create(String username, String role, int enabled) {
    var u = new SysUser();
    u.username = username;
    u.passwordHash = encoder.encode(PASSWORD);
    u.realName = "测试账号";
    u.role = role;
    u.enabled = enabled;
    users.insert(u);
    return u;
  }

  private ResultActions login(String username, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", username, "password", password))));
  }

  private String loginToken(SysUser u) throws Exception {
    return json.readTree(
            login(u.username, PASSWORD)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .at("/data/accessToken")
        .asText();
  }

  @Test
  void contextAndMysqlInitialized() throws Exception {
    assertNotNull(context.getBean(LabApplication.class));
    try (var connection = dataSource.getConnection()) {
      assertTrue(connection.getMetaData().getDatabaseProductVersion().startsWith("8."));
    }
    assertTrue(context.getBeansOfType(DemoDataInitializer.class).isEmpty());
    mvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UP"));
  }

  @Test
  void validAdminLoginAndSafeUserView() throws Exception {
    login("admin", PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.id").isString())
        .andExpect(jsonPath("$.data.expiresIn").value(7200))
        .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.user.wxOpenid").doesNotExist());
  }

  @Test
  void wrongPassword() throws Exception {
    login("admin", "incorrect-password")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void nonexistentAccount() throws Exception {
    login("missing", PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void disabledAccountLogin() throws Exception {
    login("disabled", PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void studentPasswordLoginDenied() throws Exception {
    login("student", PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void missingJwt() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.requestId").isString());
  }

  @Test
  void invalidJwt() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredJwt() throws Exception {
    String expired =
        new JwtService(KEY, Clock.offset(Clock.systemUTC(), Duration.ofHours(-3))).issue(admin.id);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void currentUser() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + loginToken(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("admin"))
        .andExpect(jsonPath("$.data.role").value("SYS_ADMIN"));
  }

  @Test
  void disableAfterIssuance() throws Exception {
    String token = loginToken(admin);
    jdbc.update("UPDATE sys_user SET enabled=0 WHERE id=?", admin.id);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleReloadedFromDatabase() throws Exception {
    String token = loginToken(admin);
    jdbc.update("UPDATE sys_user SET role='STUDENT' WHERE id=?", admin.id);
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void studentDeniedAdminRoutes() throws Exception {
    // 测试签发学生令牌仅验证服务端权限，不提供学生登录或免密接口。
    String token = jwt.issue(student.id);
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    for (String path : List.of("/api/v1/labs", "/api/v1/equipment"))
      mvc.perform(post(path).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/equipment").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void labAdminRoleBoundaries() throws Exception {
    String token = loginToken(lab);
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/labs").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/labs/1/admins").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    // 阶段 2 已有资源控制器；无分配时仍能查询，但只返回空列表。
    mvc.perform(get("/api/v1/labs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void validationAndUnknownFields() throws Exception {
    login("admin", "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    "{\"username\":\"admin\",\"password\":\"abcdefgh\",\"role\":\"SYS_ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void corsAllowsOnlyConfiguredOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/auth/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    mvc.perform(
            options("/api/v1/auth/me")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.requestId").isString());
  }

  @Test
  void usernameUniqueAndBcryptPersisted() {
    assertTrue(encoder.matches(PASSWORD, users.selectById(admin.id).passwordHash));
    assertThrows(DataIntegrityViolationException.class, () -> create("admin", "SYS_ADMIN", 1));
  }

  private void assertCheckViolation(org.junit.jupiter.api.function.Executable operation) {
    var error = assertThrows(org.springframework.dao.DataAccessException.class, operation);
    assertInstanceOf(java.sql.SQLException.class, error.getMostSpecificCause());
    assertEquals(3819, ((java.sql.SQLException) error.getMostSpecificCause()).getErrorCode());
  }

  @Test
  void constraintsAndAssignment() {
    assertCheckViolation(() -> jdbc.update("UPDATE sys_user SET role='ROOT' WHERE id=?", admin.id));
    assertCheckViolation(() -> jdbc.update("UPDATE sys_user SET enabled=2 WHERE id=?", admin.id));
    assertCheckViolation(
        () -> jdbc.update("UPDATE sys_user SET wx_openid='bad' WHERE id=?", admin.id));
    jdbc.update(
        "INSERT INTO laboratory(code,name,location,enabled,created_at,updated_at) VALUES"
            + " ('TEST','测试实验室','测试地点',1,NOW(3),NOW(3))");
    Long labId = jdbc.queryForObject("SELECT id FROM laboratory WHERE code='TEST'", Long.class);
    jdbc.update(
        "INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES (?,?,NOW(3))", labId, lab.id);
    assertEquals(1, assignments.countAssignment(labId, lab.id));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO lab_admin(lab_id,user_id,created_at) VALUES (?,?,NOW(3))",
                labId,
                lab.id));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update("DELETE FROM laboratory WHERE id=?", labId));
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " VALUES (?,'TEST-EQ','测试设备','TOOL','测试地点','AVAILABLE',NOW(3),NOW(3))",
        labId);
    assertCheckViolation(
        () -> jdbc.update("UPDATE equipment SET status='BROKEN' WHERE asset_no='TEST-EQ'"));
    assertCheckViolation(
        () -> jdbc.update("UPDATE equipment SET category='UNKNOWN' WHERE asset_no='TEST-EQ'"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE equipment SET lab_id=-1 WHERE asset_no='TEST-EQ'"));
  }

  @Test
  void migrationRepeatAndDataPreserved() {
    assertEquals(3, flyway.info().applied().length);
    assertEquals("3", flyway.info().current().getVersion().toString());
    var restarted = Flyway.configure().dataSource(dataSource).cleanDisabled(true).load();
    assertEquals(0, restarted.migrate().migrationsExecuted);
    restarted.validate();
    assertEquals("admin", users.selectById(admin.id).username);
    assertThrows(org.flywaydb.core.api.FlywayException.class, restarted::clean);
  }

  @Test
  void databaseReadWriteAndTransactionRollback() {
    var tx = new TransactionTemplate(transactionManager);
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  create("rolled_back", "STUDENT", 1);
                  throw new IllegalStateException("test");
                }));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE username='rolled_back'", Integer.class));
    admin.realName = "更新姓名";
    users.updateById(admin);
    assertEquals("更新姓名", users.selectById(admin.id).realName);
    assertNotNull(admin.updatedAt);
  }

  @Test
  void largeIdSerializedAsString() throws Exception {
    jdbc.update("UPDATE sys_user SET id=9007199254740993 WHERE id=?", admin.id);
    mvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + jwt.issue(9007199254740993L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("9007199254740993"));
  }

  @Test
  void demoInitializationIsExplicitHashedAndRepeatable() {
    var seed = new DemoDataInitializer(jdbc, encoder, PASSWORD);
    var tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        status -> seed.run(new org.springframework.boot.DefaultApplicationArguments()));
    assertEquals(
        4,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'demo_%'", Integer.class));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM laboratory WHERE code LIKE 'DEMO-%'", Integer.class));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM equipment WHERE asset_no LIKE 'DEMO-%'", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lab_admin", Integer.class));
    String original =
        jdbc.queryForObject(
            "SELECT password_hash FROM sys_user WHERE username='demo_sys_admin'", String.class);
    assertTrue(encoder.matches(PASSWORD, original));
    tx.executeWithoutResult(
        status -> seed.run(new org.springframework.boot.DefaultApplicationArguments()));
    assertEquals(
        original,
        jdbc.queryForObject(
            "SELECT password_hash FROM sys_user WHERE username='demo_sys_admin'", String.class));
    assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
  }

  @Test
  void demoRejectsPartialDataWithoutOverwriting() {
    var existing = create("demo_sys_admin", "SYS_ADMIN", 1);
    var seed = new DemoDataInitializer(jdbc, encoder, PASSWORD);
    var tx = new TransactionTemplate(transactionManager);
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.executeWithoutResult(
                status -> seed.run(new org.springframework.boot.DefaultApplicationArguments())));
    assertEquals(existing.passwordHash, users.selectById(existing.id).passwordHash);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM laboratory", Integer.class));
  }

  @Test
  void paginationCountsRemainNumbersAndIdsRemainStrings() {
    var page = json.valueToTree(new cn.edu.lab.common.PageResult<>(List.of(), 2L, 1L, 20L));
    assertTrue(page.get("total").isNumber());
    assertTrue(page.get("page").isNumber());
    assertEquals(
        "9007199254740993", json.valueToTree(Map.of("id", 9007199254740993L)).get("id").asText());
  }
}
