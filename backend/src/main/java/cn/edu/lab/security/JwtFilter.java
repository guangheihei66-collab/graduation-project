package cn.edu.lab.security;

import cn.edu.lab.auth.dto.UserView;
import cn.edu.lab.common.ErrorCode;
import cn.edu.lab.user.service.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UserService users;
  private final SecurityJson errors;

  public JwtFilter(JwtService jwt, UserService users, SecurityJson errors) {
    this.jwt = jwt;
    this.users = users;
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return ("/api/v1/auth/login".equals(req.getServletPath()) && "POST".equals(req.getMethod()))
        || "/api/v1/health".equals(req.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null) {
      try {
        if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
        var user = users.byId(jwt.verify(header.substring(7)));
        if (user == null
            || !Integer.valueOf(1).equals(user.enabled)
            || !List.of("STUDENT", "LAB_ADMIN", "SYS_ADMIN").contains(user.role))
          throw new IllegalArgumentException();
        var auth =
            new UsernamePasswordAuthenticationToken(
                UserView.from(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
        errors.write(res, ErrorCode.UNAUTHORIZED);
        return;
      } catch (org.springframework.dao.DataAccessException e) {
        errors.write(res, ErrorCode.RETRYABLE_ERROR);
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
