package com.ai.gateway.mapper;

import com.ai.gateway.entity.ApiKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * API Key Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
    
    /**
     * 批量查询每个用户的API Key数量
     * @param userIds 用户ID列表
     * @return Map<userId, count>
     */
    @Select("<script>" +
            "SELECT user_id, COUNT(*) as count FROM api_key " +
            "WHERE user_id IN " +
            "<foreach item='userId' collection='userIds' open='(' separator=',' close=')'>" +
            "#{userId}" +
            "</foreach> " +
            "GROUP BY user_id" +
            "</script>")
    List<Map<String, Object>> countApiKeysByUserIds(List<Long> userIds);
}
