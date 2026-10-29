package cn.edu.lab;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.edu.lab.auth.dto.LoginRequest;
import cn.edu.lab.auth.service.AuthService;
import cn.edu.lab.common.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.service.UserService;
import jakarta.validation.Validation;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class FoundationTest {
  private static final String KEY = "unit-test-only-key-at-least-thirty-two-bytes-123456";
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void bcryptChecksPassword() {
    var encoder = new BCryptPasswordEncoder(4);
    String hash = encoder.encode("test-only-password");
    assertNotEquals("test-only-password", hash);
    assertTrue(encoder.matches("test-only-password", hash));
    assertFalse(encoder.matches("wrong", hash));
  }

  @Test
  void tokenRoundTrip() {
    var jwt = new JwtService(KEY, clock);
    assertEquals(9007199254740993L, jwt.verify(jwt.issue(9007199254740993L)));
  }

  @Test
  void expiredTokenRejected() {
    String token = new JwtService(KEY, Clock.offset(clock, Duration.ofHours(-3))).issue(1);
    assertThrows(
        io.jsonwebtoken.ExpiredJwtException.class, () -> new JwtService(KEY, clock).verify(token));
  }

  @Test
  void tamperedTokenRejected() {
    String token = new JwtService(KEY, clock).issue(1);
    assertThrows(
        io.jsonwebtoken.JwtException.class,
        () -> new JwtService(KEY + "other", clock).verify(token));
  }

  @Test
  void weakKeyRejected() {
    assertThrows(IllegalArgumentException.class, () -> new JwtService("short", clock));
  }

  @Test
  void malformedTokenRejected() {
    assertThrows(
        io.jsonwebtoken.JwtException.class,
        () -> new JwtService(KEY, clock).verify("not.jwt.value"));
  }

  @Test
  void validationRejectsBcryptOverflowAndBlankUsername() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertFalse(validator.validate(new LoginRequest(" ", "abcdefgh")).isEmpty());
      assertFalse(validator.validate(new LoginRequest("admin", "密".repeat(30))).isEmpty());
      assertTrue(validator.validate(new LoginRequest("admin", "abcdefgh")).isEmpty());
      assertFalse(validator.validate(new PageQuery(0, 101)).isEmpty());
    }
  }

  @Test
  void studentCannotUsePasswordLogin() {
    var users = mock(UserService.class);
    var encoder = new BCryptPasswordEncoder(4);
    var user = new SysUser();
    user.id = 1L;
    user.username = "student";
    user.enabled = 1;
    user.role = "STUDENT";
    user.passwordHash = encoder.encode("test-password");
    when(users.byUsername("student")).thenReturn(user);
    var auth = new AuthService(users, encoder, new JwtService(KEY, clock));
    assertEquals(
        ErrorCode.UNAUTHORIZED,
        assertThrows(
                BusinessException.class,
                () -> auth.login(new LoginRequest("student", "test-password")))
            .code);
  }
}
