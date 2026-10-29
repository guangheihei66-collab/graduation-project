package cn.edu.lab.common;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.*;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class PersistenceConfig {
  @Bean
  Clock clock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
  }

  @Bean
  MybatisPlusInterceptor pagination() {
    var interceptor = new MybatisPlusInterceptor();
    var page = new PaginationInnerInterceptor(DbType.MYSQL);
    page.setMaxLimit(100L);
    interceptor.addInnerInterceptor(page);
    return interceptor;
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer ids() {
    return b -> {
      b.serializerByType(Long.class, ToStringSerializer.instance);
    };
  }

  @Bean
  MetaObjectHandler timestamps(Clock clock) {
    return new MetaObjectHandler() {
      public void insertFill(MetaObject o) {
        var now = LocalDateTime.now(clock);
        strictInsertFill(o, "createdAt", LocalDateTime.class, now);
        strictInsertFill(o, "updatedAt", LocalDateTime.class, now);
      }

      public void updateFill(MetaObject o) {
        setFieldValByName("updatedAt", LocalDateTime.now(clock), o);
      }
    };
  }
}
