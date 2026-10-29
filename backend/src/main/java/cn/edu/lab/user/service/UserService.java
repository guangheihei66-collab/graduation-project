package cn.edu.lab.user.service;

import cn.edu.lab.user.entity.SysUser;
import cn.edu.lab.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final SysUserMapper mapper;

  public UserService(SysUserMapper mapper) {
    this.mapper = mapper;
  }

  public SysUser byUsername(String username) {
    return mapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
  }

  public SysUser byId(long id) {
    return mapper.selectById(id);
  }
}
