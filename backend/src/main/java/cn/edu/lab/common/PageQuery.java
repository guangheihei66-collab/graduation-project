package cn.edu.lab.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.*;

public record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer size) {
  public PageQuery {
    if (page == null) page = 1;
    if (size == null) size = 20;
  }

  public <T> Page<T> toPage() {
    return new Page<>(page, size);
  }
}
