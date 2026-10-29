package cn.edu.lab.auth.service;

import cn.edu.lab.auth.dto.*;
import cn.edu.lab.common.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserService users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final String dummyHash;

  public AuthService(UserService users, PasswordEncoder encoder, JwtService jwt) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
  }

  public LoginResponse login(LoginRequest req) {
    var user = users.byUsername(req.username());
    boolean matches = encoder.matches(req.password(), user == null ? dummyHash : user.passwordHash);
    if (user == null
        || !matches
        || !Integer.valueOf(1).equals(user.enabled)
        || !("LAB_ADMIN".equals(user.role) || "SYS_ADMIN".equals(user.role)))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    return new LoginResponse(jwt.issue(user.id), JwtService.TTL_SECONDS, UserView.from(user));
  }
}
