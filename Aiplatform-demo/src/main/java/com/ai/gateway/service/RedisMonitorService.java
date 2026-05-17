package com.ai.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Redis监控服务 — 定时采集Redis运行状态指标
 * 
 * 监控指标：
 * 1. 内存使用情况（used_memory, maxmemory）
 * 2. 连接数（connected_clients）
 * 3. 命令执行统计（total_commands_processed）
 * 4. Key数量（db0 keys）
 * 5. 命中率（keyspace_hits / (keyspace_hits + keyspace_misses)）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMonitorService {

    private final StringRedisTemplate redisTemplate;
    
    private volatile Map<String, Object> lastStats = new HashMap<>();

    @Scheduled(fixedRate = 60000)
    public void collectStats() {
        try {
            Properties info = redisTemplate.execute((RedisConnection connection) -> connection.info());
            if (info == null) {
                return;
            }

            Map<String, Object> stats = new HashMap<>();
            
            stats.put("usedMemory", parseMemory(info.getProperty("used_memory")));
            stats.put("maxMemory", parseMemory(info.getProperty("maxmemory")));
            stats.put("connectedClients", info.getProperty("connected_clients"));
            stats.put("totalCommandsProcessed", info.getProperty("total_commands_processed"));
            stats.put("keyspaceHits", info.getProperty("keyspace_hits"));
            stats.put("keyspaceMisses", info.getProperty("keyspace_misses"));
            
            String db0 = info.getProperty("db0");
            if (db0 != null) {
                stats.put("totalKeys", extractKeys(db0));
            }
            
            double hits = parseLong(info.getProperty("keyspace_hits"));
            double misses = parseLong(info.getProperty("keyspace_misses"));
            double hitRate = (hits + misses) > 0 ? (hits / (hits + misses)) * 100 : 0;
            stats.put("hitRate", String.format("%.2f%%", hitRate));
            
            lastStats = stats;
            
            long usedMem = parseLong(info.getProperty("used_memory"));
            long maxMem = parseLong(info.getProperty("maxmemory"));
            if (maxMem > 0 && usedMem > maxMem * 0.8) {
                log.warn("Redis内存使用率过高: {:.2f}%", (usedMem * 100.0 / maxMem));
            }
            
        } catch (Exception e) {
            log.error("Redis状态采集失败", e);
        }
    }

    public Map<String, Object> getLastStats() {
        return lastStats;
    }

    private long parseMemory(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long extractKeys(String db0) {
        if (db0 == null) return 0;
        String[] parts = db0.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("keys=")) {
                return parseLong(part.trim().substring(5));
            }
        }
        return 0;
    }
}
