package cn.edu.lab.common;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final String password;

  public DemoDataInitializer(
      JdbcTemplate jdbc, PasswordEncoder encoder, @Value("${app.demo.password}") String password) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password.length() < 8
        || password.length() > 64
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || password.isBlank()) throw new IllegalStateException("启用演示数据时须提供符合密码规则的 DEMO_PASSWORD");
    int count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE username IN"
                + " ('demo_sys_admin','demo_lab_admin','demo_student_1','demo_student_2')",
            Integer.class);
    if (count == 4) return; // 重启不覆盖原有账号、密码或资源。
    if (count != 0) throw new IllegalStateException("演示账号已部分存在，请使用专用空开发库，禁止覆盖已有数据");
    String hash = encoder.encode(password);
    String sql =
        "INSERT INTO sys_user(username,password_hash,real_name,role,enabled,created_at,updated_at)"
            + " VALUES (?,?,?,?,1,NOW(3),NOW(3))";
    jdbc.update(sql, "demo_sys_admin", hash, "演示系统管理员", "SYS_ADMIN");
    jdbc.update(sql, "demo_lab_admin", hash, "演示实验室管理员", "LAB_ADMIN");
    jdbc.update(sql, "demo_student_1", hash, "演示学生一", "STUDENT");
    jdbc.update(sql, "demo_student_2", hash, "演示学生二", "STUDENT");
    jdbc.update(
        "INSERT INTO laboratory(code,name,location,enabled,created_at,updated_at) VALUES"
            + " ('DEMO-LAB-1','电子实验室','教学楼一层',1,NOW(3),NOW(3)),('DEMO-LAB-2','计算机实验室','教学楼二层',1,NOW(3),NOW(3))");
    jdbc.update(
        "INSERT INTO lab_admin(lab_id,user_id,created_at) SELECT l.id,u.id,NOW(3) FROM laboratory l"
            + " JOIN sys_user u ON u.username='demo_lab_admin' WHERE l.code='DEMO-LAB-1'");
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " SELECT id,'DEMO-EQ-1','示波器','INSTRUMENT','实验台一','AVAILABLE',NOW(3),NOW(3) FROM"
            + " laboratory WHERE code='DEMO-LAB-1'");
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " SELECT id,'DEMO-EQ-2','万用表','TOOL','实验台二','AVAILABLE',NOW(3),NOW(3) FROM laboratory"
            + " WHERE code='DEMO-LAB-1'");
    jdbc.update(
        "INSERT INTO equipment(lab_id,asset_no,name,category,location,status,created_at,updated_at)"
            + " SELECT id,'DEMO-EQ-3','实验计算机','COMPUTER','机房一','AVAILABLE',NOW(3),NOW(3) FROM"
            + " laboratory WHERE code='DEMO-LAB-2'");
  }
}
