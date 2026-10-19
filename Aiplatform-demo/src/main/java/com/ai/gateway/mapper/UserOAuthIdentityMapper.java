package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserOAuthIdentity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserOAuthIdentityMapper extends BaseMapper<UserOAuthIdentity> {
    @Select("SELECT * FROM user_oauth_identity WHERE provider = #{provider} AND provider_subject = #{subject} LIMIT 1")
    UserOAuthIdentity selectByProviderSubject(@Param("provider") String provider,
                                               @Param("subject") String subject);

    @Select("SELECT * FROM user_oauth_identity WHERE user_id = #{userId} ORDER BY provider")
    List<UserOAuthIdentity> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_oauth_identity WHERE user_id = #{userId} AND provider = #{provider} LIMIT 1")
    UserOAuthIdentity selectByUserAndProvider(@Param("userId") Long userId,
                                               @Param("provider") String provider);
}
