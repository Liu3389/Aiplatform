package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserTwoFactor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserTwoFactorMapper extends BaseMapper<UserTwoFactor> {

    @Select("SELECT * FROM user_two_factor WHERE user_id = #{userId} LIMIT 1 FOR UPDATE")
    UserTwoFactor selectByUserForUpdate(@Param("userId") Long userId);

    @Update("UPDATE user_two_factor SET secret_ciphertext = pending_secret_ciphertext, "
            + "pending_secret_ciphertext = NULL, enabled = 1, last_totp_step = #{step}, update_time = #{now} "
            + "WHERE user_id = #{userId} AND enabled = 0 AND pending_secret_ciphertext IS NOT NULL")
    int activateEnrollment(@Param("userId") Long userId, @Param("step") long step,
                           @Param("now") LocalDateTime now);

    @Update("UPDATE user_two_factor SET last_totp_step = #{step}, update_time = #{now} "
            + "WHERE user_id = #{userId} AND enabled = 1 AND last_totp_step < #{step}")
    int consumeTotpStep(@Param("userId") Long userId, @Param("step") long step,
                        @Param("now") LocalDateTime now);
}
