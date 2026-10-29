package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.edu.lab.auth.dto.UserView;
import cn.edu.lab.common.*;
import cn.edu.lab.equipment.dto.UpdateEquipmentRequest;
import cn.edu.lab.equipment.service.EquipmentService;
import cn.edu.lab.laboratory.mapper.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class ResourceManagementIT {
  private static MySQLContainer<?> mysql;
  private static final String PASSWORD = UUID.randomUUID().toString();
  private static String hash;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    // 仅一次性测试库关闭 binlog，允许普通测试账号建立故障注入触发器；不改生产配置。
    mysql = new MySQLContainer<>("mysql:8.0.45").withCommand("--skip-log-bin");
    mysql.start();
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
  }

  @AfterAll
  static void cleanup() {
    if (mysql != null) mysql.stop();
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired SysUserMapper users;
  @Autowired LaboratoryMapper labs;
  @Autowired LabAdminMapper assignments;
  @Autowired JwtService jwt;
  @Autowired PasswordEncoder passwords;
  @Autowired PlatformTransactionManager manager;
  @Autowired EquipmentService equipmentService;
  private SysUser sys, admin, other, student, disabled;
  private String l1, l2, e1, e2;

  @BeforeEach
  void setup() throws Exception {
    jdbc.update("DELETE FROM equipment");
    jdbc.update("DELETE FROM lab_admin");
    jdbc.update("DELETE FROM laboratory");
    jdbc.update("DELETE FROM sys_user");
    if (hash == null) hash = passwords.encode(PASSWORD);
    sys = user("sys", "SYS_ADMIN", 1);
    admin = user("lab", "LAB_ADMIN", 1);
    other = user("other", "LAB_ADMIN", 1);
    student = user("student", "STUDENT", 1);
    disabled = user("disabled", "LAB_ADMIN", 0);
    l1 = createLab("L1");
    l2 = createLab("L2");
    call(
            put("/api/v1/labs/" + l1 + "/admins"),
            sys,
            Map.of("userIds", List.of(admin.id.toString())))
        .andExpect(status().isOk());
    e1 = createEquipment(l1, "EQ-1");
    e2 = createEquipment(l2, "EQ-2");
  }

  private SysUser user(String username, String role, int enabled) {
    var u = new SysUser();
    u.username = username;
    u.realName = "测试" + username;
    u.passwordHash = hash;
    u.role = role;
    u.enabled = enabled;
    users.insert(u);
    return u;
  }

  private ResultActions call(MockHttpServletRequestBuilder request, SysUser actor, Object body)
      throws Exception {
    request.header("Authorization", "Bearer " + jwt.issue(actor.id));
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  private JsonNode data(ResultActions action) throws Exception {
    return json.readTree(action.andReturn().getResponse().getContentAsString()).get("data");
  }

  private String createLab(String code) throws Exception {
    return data(call(
                post("/api/v1/labs"),
                sys,
                Map.of("code", code, "name", "实验室" + code, "location", "地点", "description", "原描述"))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private String createEquipment(String lab, String asset) throws Exception {
    return data(call(
                post("/api/v1/equipment"),
                sys,
                Map.of(
                    "labId",
                    lab,
                    "assetNo",
                    asset,
                    "name",
                    "设备" + asset,
                    "category",
                    "INSTRUMENT",
                    "location",
                    "实验台",
                    "model",
                    "原型号",
                    "description",
                    "原描述"))
            .andExpect(status().isCreated()))
        .get("id")
        .asText();
  }

  private Map<String, Object> newUser(String username) {
    return Map.of(
        "username", username, "realName", "新用户", "role", "LAB_ADMIN", "initialPassword", PASSWORD);
  }

  @Test
  void systemAdminCreatesHashedUserAndSafeView() throws Exception {
    var d =
        data(
            call(post("/api/v1/users"), sys, newUser("created"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.wxOpenid").doesNotExist()));
    String saved =
        jdbc.queryForObject(
            "SELECT password_hash FROM sys_user WHERE id=?", String.class, d.get("id").asText());
    assertTrue(passwords.matches(PASSWORD, saved));
    assertNotEquals(PASSWORD, saved);
  }

  @Test
  void userManagementRolesDenied() throws Exception {
    call(post("/api/v1/users"), admin, newUser("denied")).andExpect(status().isForbidden());
    call(get("/api/v1/users"), student, null).andExpect(status().isForbidden());
    call(put("/api/v1/users/" + sys.id + "/password"), admin, Map.of("newPassword", PASSWORD))
        .andExpect(status().isForbidden());
  }

  @Test
  void duplicateUsernameConflict() throws Exception {
    call(post("/api/v1/users"), sys, newUser("sys"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void userFiltersAndStablePagination() throws Exception {
    call(
            get("/api/v1/users")
                .param("username", "lab")
                .param("role", "LAB_ADMIN")
                .param("enabled", "1")
                .param("size", "1"),
            sys,
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].username").value("lab"));
    var first =
        data(call(get("/api/v1/users").param("size", "2"), sys, null).andExpect(status().isOk()));
    var second =
        data(
            call(get("/api/v1/users").param("size", "2").param("page", "2"), sys, null)
                .andExpect(status().isOk()));
    assertNotEquals(first.get("records").get(0).get("id"), second.get("records").get(0).get("id"));
    assertTrue(first.get("total").isNumber());
  }

  @Test
  void resetPasswordChangesLoginCredential() throws Exception {
    String replacement = UUID.randomUUID().toString();
    call(put("/api/v1/users/" + admin.id + "/password"), sys, Map.of("newPassword", replacement))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(admin.id.toString()));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("username", admin.username, "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("username", admin.username, "password", replacement))))
        .andExpect(status().isOk());
  }

  @Test
  void disableAndEnableUserEnforcedByExistingJwt() throws Exception {
    String token = jwt.issue(admin.id);
    call(patch("/api/v1/users/" + admin.id), sys, Map.of("enabled", 0, "realName", "改名"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/labs").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("username", admin.username, "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    call(patch("/api/v1/users/" + admin.id), sys, Map.of("enabled", 1)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/labs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void cannotDisableSelfOrChangeImmutableUserFields() throws Exception {
    call(patch("/api/v1/users/" + sys.id), sys, Map.of("enabled", 0))
        .andExpect(status().isConflict());
    for (String f : List.of("username", "role"))
      call(patch("/api/v1/users/" + admin.id), sys, Map.of(f, "STUDENT"))
          .andExpect(status().isBadRequest());
    assertEquals(1, users.selectById(sys.id).enabled);
  }

  @Test
  void invalidUserAndPasswordPayloads() throws Exception {
    call(
            post("/api/v1/users"),
            sys,
            Map.of(
                "username", "bad", "realName", "用户", "role", "ROOT", "initialPassword", PASSWORD))
        .andExpect(status().isBadRequest());
    call(put("/api/v1/users/" + admin.id + "/password"), sys, Map.of("newPassword", "密".repeat(25)))
        .andExpect(status().isBadRequest());
    call(patch("/api/v1/users/" + admin.id), sys, Collections.singletonMap("enabled", null))
        .andExpect(status().isBadRequest());
  }

  @Test
  void labCreationAndUniqueCode() throws Exception {
    call(post("/api/v1/labs"), sys, Map.of("code", "L1", "name", "重复", "location", "地点"))
        .andExpect(status().isConflict());
    call(post("/api/v1/labs"), admin, Map.of("code", "X", "name", "无权限", "location", "地点"))
        .andExpect(status().isForbidden());
    call(get("/api/v1/labs/" + l1), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").isString())
        .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+08:00")));
  }

  @Test
  void labAdminCanOnlyReadAndUpdateAssignedLab() throws Exception {
    call(patch("/api/v1/labs/" + l1), admin, Map.of("name", "授权更新"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("原描述"));
    call(patch("/api/v1/labs/" + l2), admin, Map.of("name", "越权更新"))
        .andExpect(status().isForbidden());
    call(get("/api/v1/labs/" + l2), admin, null).andExpect(status().isForbidden());
    call(get("/api/v1/labs"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").value(l1));
  }

  @Test
  void studentLabVisibilityTracksEnabledState() throws Exception {
    call(patch("/api/v1/labs/" + l2), sys, Map.of("enabled", 0)).andExpect(status().isOk());
    call(get("/api/v1/labs/" + l2), student, null).andExpect(status().isForbidden());
    call(get("/api/v1/labs"), student, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    call(get("/api/v1/equipment/" + e2), student, null).andExpect(status().isForbidden());
    call(patch("/api/v1/labs/" + l2), sys, Map.of("enabled", 1)).andExpect(status().isOk());
    call(get("/api/v1/labs/" + l2), student, null).andExpect(status().isOk());
  }

  @Test
  void patchPreservesMissingAndClearsExplicitNull() throws Exception {
    call(patch("/api/v1/labs/" + l1), sys, Map.of("name", "新名称"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("原描述"));
    call(patch("/api/v1/labs/" + l1), sys, Collections.singletonMap("description", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").isEmpty());
    call(patch("/api/v1/equipment/" + e1), sys, Collections.singletonMap("model", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.model").isEmpty())
        .andExpect(jsonPath("$.data.description").value("原描述"));
    call(patch("/api/v1/equipment/" + e1), sys, Collections.singletonMap("description", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").isEmpty());
  }

  @Test
  void labCodeCannotChangeAndLabSearchWorks() throws Exception {
    call(patch("/api/v1/labs/" + l1), sys, Map.of("code", "NEW"))
        .andExpect(status().isBadRequest());
    call(get("/api/v1/labs").param("name", "L1"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  void assignmentReplacesAndClears() throws Exception {
    call(
            put("/api/v1/labs/" + l1 + "/admins"),
            sys,
            Map.of("userIds", List.of(admin.id.toString(), other.id.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
    call(get("/api/v1/labs/" + l1 + "/admins"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").isString())
        .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    call(
            put("/api/v1/labs/" + l1 + "/admins"),
            sys,
            Map.of("userIds", List.of(other.id.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("name", "撤权后写入"))
        .andExpect(status().isForbidden());
    call(put("/api/v1/labs/" + l1 + "/admins"), sys, Map.of("userIds", List.of()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    assertEquals(0, assignments.countAssignment(Long.parseLong(l1), other.id));
  }

  @Test
  void assignmentOnlySystemAdmin() throws Exception {
    for (SysUser u : List.of(admin, student)) {
      call(
              put("/api/v1/labs/" + l1 + "/admins"),
              u,
              Map.of("userIds", List.of(other.id.toString())))
          .andExpect(status().isForbidden());
      call(get("/api/v1/labs/" + l1 + "/admins"), u, null).andExpect(status().isForbidden());
    }
  }

  @Test
  void assignmentRejectsWrongRoleDisabledDuplicateAndMissing() throws Exception {
    for (SysUser u : List.of(student, disabled))
      call(put("/api/v1/labs/" + l1 + "/admins"), sys, Map.of("userIds", List.of(u.id.toString())))
          .andExpect(status().isConflict());
    call(
            put("/api/v1/labs/" + l1 + "/admins"),
            sys,
            Map.of("userIds", List.of(admin.id.toString(), admin.id.toString())))
        .andExpect(status().isBadRequest());
    call(
            put("/api/v1/labs/" + l1 + "/admins"),
            sys,
            Map.of("userIds", List.of("9223372036854775807")))
        .andExpect(status().isNotFound());
    assertEquals(1, assignments.countAssignment(Long.parseLong(l1), admin.id));
  }

  @Test
  void replacementRollsBackAfterDatabaseInsertFailure() throws Exception {
    jdbc.execute(
        "CREATE TRIGGER stage2_reject_assignment BEFORE INSERT ON lab_admin FOR EACH ROW SIGNAL"
            + " SQLSTATE '45000' SET MESSAGE_TEXT='test insertion failure'");
    try {
      call(
              put("/api/v1/labs/" + l1 + "/admins"),
              sys,
              Map.of("userIds", List.of(other.id.toString())))
          .andExpect(status().isServiceUnavailable());
      assertEquals(1, assignments.countAssignment(Long.parseLong(l1), admin.id));
      assertEquals(0, assignments.countAssignment(Long.parseLong(l1), other.id));
    } finally {
      jdbc.execute("DROP TRIGGER stage2_reject_assignment");
    }
  }

  @Test
  void equipmentCreationAndDuplicateAsset() throws Exception {
    call(
            post("/api/v1/equipment"),
            admin,
            Map.of(
                "labId",
                l1,
                "assetNo",
                "ADMIN-EQ",
                "name",
                "新增",
                "category",
                "TOOL",
                "location",
                "位置"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").isString())
        .andExpect(jsonPath("$.data.labName").value("实验室L1"));
    call(
            post("/api/v1/equipment"),
            sys,
            Map.of(
                "labId",
                l2,
                "assetNo",
                "EQ-1",
                "name",
                "重复",
                "category",
                "OTHER",
                "location",
                "位置"))
        .andExpect(status().isConflict());
  }

  @Test
  void equipmentWriteIsolationAndStudentDenial() throws Exception {
    call(patch("/api/v1/equipment/" + e2), admin, Map.of("name", "越权"))
        .andExpect(status().isForbidden());
    call(
            post("/api/v1/equipment"),
            admin,
            Map.of(
                "labId",
                l2,
                "assetNo",
                "UNAUTH",
                "name",
                "越权",
                "category",
                "TOOL",
                "location",
                "位置"))
        .andExpect(status().isForbidden());
    call(post("/api/v1/equipment"), student, Map.of()).andExpect(status().isForbidden());
    call(patch("/api/v1/equipment/" + e1), student, Map.of("status", "DISABLED"))
        .andExpect(status().isForbidden());
  }

  @Test
  void equipmentPatchAndImmutableFields() throws Exception {
    call(
            patch("/api/v1/equipment/" + e1),
            admin,
            Map.of("name", "改名", "category", "TOOL", "location", "新位置"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.model").value("原型号"));
    for (String field : List.of("assetNo", "labId"))
      call(patch("/api/v1/equipment/" + e1), sys, Map.of(field, l2))
          .andExpect(status().isBadRequest());
    assertEquals(
        l1, jdbc.queryForObject("SELECT lab_id FROM equipment WHERE id=?", String.class, e1));
  }

  @Test
  void studentOnlySeesAvailableEquipmentInEnabledLabs() throws Exception {
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "MAINTENANCE"))
        .andExpect(status().isOk());
    call(get("/api/v1/equipment/" + e1), student, null).andExpect(status().isForbidden());
    call(get("/api/v1/equipment"), student, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").value(e2));
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "DISABLED"))
        .andExpect(status().isOk());
    call(get("/api/v1/equipment/" + e1), student, null).andExpect(status().isForbidden());
    call(patch("/api/v1/labs/" + l2), sys, Map.of("enabled", 0)).andExpect(status().isOk());
    call(get("/api/v1/equipment"), student, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    call(patch("/api/v1/equipment/" + e1), admin, Map.of("status", "AVAILABLE"))
        .andExpect(status().isOk());
    call(get("/api/v1/equipment/" + e1), student, null).andExpect(status().isOk());
  }

  @Test
  void equipmentListsAreScopedInSql() throws Exception {
    call(get("/api/v1/equipment"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").value(e1));
    call(get("/api/v1/equipment").param("labId", l2), admin, null)
        .andExpect(status().isForbidden());
    call(get("/api/v1/equipment/" + e2), admin, null).andExpect(status().isForbidden());
    call(get("/api/v1/equipment"), other, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void equipmentFiltersSearchAndPagination() throws Exception {
    call(
            get("/api/v1/equipment")
                .param("labId", l1)
                .param("keyword", "EQ-1")
                .param("category", "INSTRUMENT")
                .param("status", "AVAILABLE"),
            sys,
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    call(get("/api/v1/equipment").param("keyword", "设备"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));
    call(get("/api/v1/equipment").param("keyword", "%"), sys, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
    var first = data(call(get("/api/v1/equipment").param("size", "1"), sys, null));
    var second =
        data(call(get("/api/v1/equipment").param("size", "1").param("page", "2"), sys, null));
    assertNotEquals(first.get("records").get(0).get("id"), second.get("records").get(0).get("id"));
    assertEquals(2, first.get("total").asInt());
  }

  @Test
  void invalidFiltersPaginationAndIdentifiersReturn400() throws Exception {
    for (String path : List.of("/api/v1/users", "/api/v1/labs", "/api/v1/equipment")) {
      call(get(path).param("page", "0"), sys, null).andExpect(status().isBadRequest());
      call(get(path).param("size", "101"), sys, null).andExpect(status().isBadRequest());
    }
    call(get("/api/v1/equipment").param("status", "BROKEN"), sys, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/equipment").param("category", "INVALID"), sys, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/equipment").param("labId", "-1"), sys, null)
        .andExpect(status().isBadRequest());
    call(get("/api/v1/equipment/no-id"), sys, null).andExpect(status().isBadRequest());
    call(get("/api/v1/users").param("enabled", "2"), sys, null).andExpect(status().isBadRequest());
    call(get("/api/v1/users").param("role", "ROOT"), sys, null).andExpect(status().isBadRequest());
  }

  @Test
  void invalidPatchEnumsNullsAndEmptyBodyRejected() throws Exception {
    call(patch("/api/v1/equipment/" + e1), sys, Map.of("status", "BROKEN"))
        .andExpect(status().isBadRequest());
    call(patch("/api/v1/equipment/" + e1), sys, Map.of("category", 0))
        .andExpect(status().isBadRequest());
    call(patch("/api/v1/equipment/" + e1), sys, Collections.singletonMap("status", null))
        .andExpect(status().isBadRequest());
    call(patch("/api/v1/labs/" + l1), sys, Map.of()).andExpect(status().isBadRequest());
  }

  @Test
  void missingResourcesReturn404() throws Exception {
    call(get("/api/v1/labs/9223372036854775807"), sys, null).andExpect(status().isNotFound());
    call(get("/api/v1/equipment/9223372036854775807"), sys, null).andExpect(status().isNotFound());
    call(get("/api/v1/equipment").param("labId", "9223372036854775807"), sys, null)
        .andExpect(status().isNotFound());
    call(patch("/api/v1/users/9223372036854775807"), sys, Map.of("realName", "不存在"))
        .andExpect(status().isNotFound());
  }

  @Test
  void noPhysicalDeleteRoutes() throws Exception {
    call(delete("/api/v1/users/" + student.id), sys, null).andExpect(status().isMethodNotAllowed());
    call(delete("/api/v1/labs/" + l1), sys, null).andExpect(status().isMethodNotAllowed());
    call(delete("/api/v1/equipment/" + e1), sys, null).andExpect(status().isMethodNotAllowed());
    assertNotNull(users.selectById(student.id));
  }

  @Test
  void revocationSerializesWithResourceWriteInMysql() throws Exception {
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
                      s -> {
                        labs.lockById(Long.parseLong(l1));
                        assignments.deleteForLab(Long.parseLong(l1));
                        locked.countDown();
                        try {
                          if (!release.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("test timeout");
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var write =
          executor.submit(
              () -> {
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            UserView.from(admin), null, List.of()));
                try {
                  started.countDown();
                  var req = new UpdateEquipmentRequest();
                  req.setName("并发越权");
                  equipmentService.update(Long.parseLong(e1), req);
                  return "unexpected success";
                } catch (BusinessException ex) {
                  return ex.code.name();
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> write.get(250, TimeUnit.MILLISECONDS));
      release.countDown();
      revoke.get(5, TimeUnit.SECONDS);
      assertEquals("FORBIDDEN", write.get(5, TimeUnit.SECONDS));
      assertEquals(
          "设备EQ-1", jdbc.queryForObject("SELECT name FROM equipment WHERE id=?", String.class, e1));
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
