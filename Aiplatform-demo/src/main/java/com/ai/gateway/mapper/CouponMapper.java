package com.ai.gateway.mapper;

import com.ai.gateway.entity.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 原子条件更新：仅当券属于该用户、未使用且未过期时，才标记为已使用。
     * 返回受影响行数：1 表示成功抢占，0 表示已被占用/过期/不存在。
     * 用单条 SQL 消除 select-then-update 的 TOCTOU 竞态。
     */
    @Update("UPDATE coupon SET used = 1, used_time = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId} AND used = 0 " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    int updateToUsedIfAvailable(@Param("id") Long id, @Param("userId") Long userId);
}
