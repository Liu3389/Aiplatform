package com.ai.gateway.mapper;

import com.ai.gateway.entity.ApiKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Key Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
}
