package cn.edu.lab.reservation.mapper;

import cn.edu.lab.reservation.entity.Reservation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

public interface ReservationMapper extends BaseMapper<Reservation> {
  @Select("SELECT * FROM reservation WHERE id=#{id} FOR UPDATE")
  Reservation lockById(@Param("id") long id);
}
