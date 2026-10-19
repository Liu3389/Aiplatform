package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserTwoFactorBackupCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserTwoFactorBackupCodeMapper extends BaseMapper<UserTwoFactorBackupCode> {

    @Update("UPDATE user_two_factor_backup_code SET consumed_at = CURRENT_TIMESTAMP "
            + "WHERE user_id = #{userId} AND code_hash = #{codeHash} AND consumed_at IS NULL")
    int consume(@Param("userId") Long userId, @Param("codeHash") String codeHash);

    @Select("SELECT COUNT(*) FROM user_two_factor_backup_code "
            + "WHERE user_id = #{userId} AND consumed_at IS NULL")
    int countUnused(@Param("userId") Long userId);

    @Delete("DELETE FROM user_two_factor_backup_code WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
