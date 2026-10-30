package cn.edu.lab.auth.wechat;

import cn.edu.lab.auth.dto.*;
import cn.edu.lab.auth.mapper.BindTicketMapper;
import cn.edu.lab.common.*;
import cn.edu.lab.security.JwtService;
import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WechatAuthService {
  private final WechatClient client;
  private final SysUserMapper users;
  private final BindTicketMapper tickets;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;

  public WechatAuthService(
      WechatClient client,
      SysUserMapper users,
      BindTicketMapper tickets,
      PasswordEncoder encoder,
      JwtService jwt,
      Clock clock,
      PlatformTransactionManager manager) {
    this.client = client;
    this.users = users;
    this.tickets = tickets;
    this.encoder = encoder;
    this.jwt = jwt;
    this.clock = clock;
    this.tx = new TransactionTemplate(manager);
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  // 微信 HTTP 在任何数据库事务、行锁之外完成。
  public WechatLoginResponse login(WechatLoginRequest req) {
    String openid = client.exchange(req.code());
    var user = users.selectOne(new QueryWrapper<SysUser>().eq("wx_openid", openid));
    if (user != null) {
      requireStudent(user);
      return new WechatLoginResponse(
          false, null, jwt.issue(user.id), JwtService.TTL_SECONDS, UserView.from(user));
    }
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var ticket = new BindTicket();
    ticket.ticketHash = hash(raw);
    ticket.wxOpenid = openid;
    ticket.createdAt = now();
    ticket.expiresAt = ticket.createdAt.plusMinutes(5);
    tickets.insert(ticket);
    return new WechatLoginResponse(true, raw, null, 300, null);
  }

  public LoginResponse bind(WechatBindRequest req) {
    var candidate = users.selectOne(new QueryWrapper<SysUser>().eq("username", req.username()));
    boolean matches =
        encoder.matches(req.password(), candidate == null ? dummyHash : candidate.passwordHash);
    if (!matches || candidate == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    requireStudent(candidate);
    return tx.execute(
        status -> {
          // 账号 → 票据；不在之后获取实验室、设备或预约锁。BCrypt 在行锁外计算，锁内比较哈希防重置竞争。
          var user = users.lockById(candidate.id);
          requireStudent(user);
          if (!Objects.equals(user.passwordHash, candidate.passwordHash))
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
          var ticket = tickets.lock(hash(req.bindTicket()));
          if (ticket == null || !ticket.expiresAt.isAfter(now()))
            throw new BusinessException(ErrorCode.BIND_TICKET_INVALID);
          if (user.wxOpenid != null) throw new BusinessException(ErrorCode.WECHAT_BIND_CONFLICT);
          user.wxOpenid = ticket.wxOpenid;
          try {
            users.updateById(user);
          } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.WECHAT_BIND_CONFLICT);
          }
          if (tickets.consume(ticket.ticketHash) != 1)
            throw new BusinessException(ErrorCode.BIND_TICKET_INVALID);
          return new LoginResponse(jwt.issue(user.id), JwtService.TTL_SECONDS, UserView.from(user));
        });
  }

  private void requireStudent(SysUser user) {
    if (user == null || !Integer.valueOf(1).equals(user.enabled) || !"STUDENT".equals(user.role))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用");
    }
  }
}
