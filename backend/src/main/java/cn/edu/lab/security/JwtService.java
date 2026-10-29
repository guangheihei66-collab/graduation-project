package cn.edu.lab.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  public static final int TTL_SECONDS = 7200;
  private final SecretKey key;
  private final Clock clock;

  public JwtService(@Value("${app.jwt-secret}") String secret, Clock clock) {
    if (secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT 密钥至少需要 32 字节");
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
  }

  public String issue(long userId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(Long.toString(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
        .id(UUID.randomUUID().toString())
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public long verify(String token) {
    var jws =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(token);
    if (!"HS256".equals(jws.getHeader().getAlgorithm()))
      throw new IllegalArgumentException("签名算法不正确");
    Claims c = jws.getPayload();
    if (c.getExpiration() == null
        || c.getIssuedAt() == null
        || c.getId() == null
        || c.getIssuedAt().toInstant().isAfter(clock.instant()))
      throw new IllegalArgumentException("令牌声明不完整");
    long id = Long.parseLong(c.getSubject());
    if (id <= 0) throw new IllegalArgumentException("账号标识无效");
    return id;
  }
}
