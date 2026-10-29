package cn.edu.lab.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size) {
  public static <T> PageResult<T> from(IPage<T> p) {
    return new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
  }
}
