package com.ai.gateway.service;

import com.ai.gateway.entity.StatisticsCounter;
import com.ai.gateway.mapper.StatisticsCounterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统计计数器服务
 * 用于实时更新计数器，避免全表扫描
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsCounterService {
    
    private final StatisticsCounterMapper counterMapper;
    
    /**
     * 增加用户总数计数器
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementUserTotal() {
        incrementCounter("USER_TOTAL", 1L);
    }
    
    /**
     * 增加会话总数计数器
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementConversationTotal() {
        incrementCounter("CONVERSATION_TOTAL", 1L);
    }
    
    /**
     * 增加消息总数计数器
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementMessageTotal(Long increment) {
        incrementCounter("MESSAGE_TOTAL", increment);
    }
    
    /**
     * 增加API调用总数计数器
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementApiCallTotal(boolean success) {
        incrementCounter("API_CALL_TOTAL", 1L);
        if (success) {
            incrementCounter("API_CALL_SUCCESS", 1L);
        } else {
            incrementCounter("API_CALL_FAILED", 1L);
        }
    }
    
    /**
     * 通用增加计数器方法（原子操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementCounter(String counterType, Long increment) {
        try {
            int rows = counterMapper.incrementCounter(counterType, increment);
            if (rows == 0) {
                // 如果计数器不存在，创建一个新的
                StatisticsCounter counter = new StatisticsCounter();
                counter.setCounterType(counterType);
                counter.setCounterValue(increment);
                counterMapper.insert(counter);
                log.info("创建新计数器: {} = {}", counterType, increment);
            }
        } catch (Exception e) {
            log.error("增加计数器失败: {}", counterType, e);
        }
    }
    
    /**
     * 获取计数器值
     */
    public Long getCounterValue(String counterType) {
        StatisticsCounter counter = counterMapper.selectByType(counterType);
        return counter != null ? counter.getCounterValue() : 0L;
    }
    
    /**
     * 批量获取计数器值
     */
    public java.util.Map<String, Long> getCounters(java.util.List<String> counterTypes) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (String type : counterTypes) {
            result.put(type, getCounterValue(type));
        }
        return result;
    }
}
