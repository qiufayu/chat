package com.fayu.user.mepper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.user.domain.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    User lockById(@Param("id") Long id);
}
