package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.lab.common.*;
import cn.edu.lab.equipment.dto.*;
import cn.edu.lab.laboratory.dto.*;
import cn.edu.lab.user.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;

class ResourceValidationTest {
  private final ObjectMapper json = new ObjectMapper();

  private boolean valid(Object value) {
    try (var f = Validation.buildDefaultValidatorFactory()) {
      return f.getValidator().validate(value).isEmpty();
    }
  }

  @Test
  void absentPatchFieldIsPreserved() throws Exception {
    var req = json.readValue("{\"name\":\"新名称\"}", UpdateLabRequest.class);
    assertTrue(req.hasName());
    assertFalse(req.hasDescription());
    assertFalse(req.hasEnabled());
    assertTrue(valid(req));
  }

  @Test
  void explicitNullClearsOnlyNullableFields() throws Exception {
    var req = json.readValue("{\"description\":null,\"model\":null}", UpdateEquipmentRequest.class);
    assertTrue(req.hasDescription());
    assertNull(req.getDescription());
    assertTrue(req.hasModel());
    assertTrue(valid(req));
    assertFalse(valid(json.readValue("{\"name\":null}", UpdateLabRequest.class)));
    assertFalse(valid(json.readValue("{\"enabled\":null}", UpdateUserRequest.class)));
  }

  @Test
  void emptyPatchAndBlankRequiredValuesRejected() throws Exception {
    assertFalse(valid(json.readValue("{}", UpdateEquipmentRequest.class)));
    assertFalse(valid(json.readValue("{\"location\":\"  \"}", UpdateLabRequest.class)));
    assertFalse(valid(json.readValue("{\"enabled\":2}", UpdateUserRequest.class)));
  }

  @Test
  void passwordLimitsRespectUtf8Bytes() {
    assertTrue(InputRules.password("abcdefgh"));
    assertTrue(InputRules.password("a".repeat(64)));
    assertFalse(InputRules.password("密".repeat(25)));
    assertFalse(InputRules.password("a".repeat(65)));
    assertFalse(InputRules.password(" ".repeat(8)));
    assertTrue(valid(new ResetPasswordRequest("correct-password")));
    assertFalse(valid(new ResetPasswordRequest("short")));
  }

  @Test
  void accountAndAssignmentDtosValidateContract() {
    assertTrue(
        valid(new CreateUserRequest("new_admin", "管理员", UserRole.LAB_ADMIN, "test-password")));
    assertFalse(valid(new CreateUserRequest(" bad ", "管理员", UserRole.LAB_ADMIN, "test-password")));
    assertFalse(valid(new AssignAdminsRequest(java.util.List.of("-1"))));
    assertTrue(valid(new AssignAdminsRequest(java.util.List.of())));
  }

  @Test
  void keywordWildcardsAreLiteralAndBlankRejected() {
    assertEquals("a\\%b\\_c\\\\", InputRules.search("a%b_c\\", 100));
    assertThrows(BusinessException.class, () -> InputRules.search(" ", 100));
  }
}
