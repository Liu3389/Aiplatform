package com.ai.gateway.mapper;

import com.ai.gateway.entity.CallLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调用日志Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface CallLogMapper extends BaseMapper<CallLog> {
}
