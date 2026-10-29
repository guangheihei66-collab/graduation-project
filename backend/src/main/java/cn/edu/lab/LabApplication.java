package cn.edu.lab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@MapperScan("cn.edu.lab.**.mapper")
public class LabApplication {
  public static void main(String[] args) {
    SpringApplication.run(LabApplication.class, args);
  }
}
