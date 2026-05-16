package com.ai.gateway.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Redis SCAN 工具类 — 替代阻塞的 KEYS 命令
 * 
 * KEYS 命令是 O(N) 全表扫描，在生产环境数据量大时会阻塞 Redis 主线程。
 * SCAN 是增量式迭代器，每次只返回少量 key，不会阻塞其他命令。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisScanUtil {

    private final StringRedisTemplate redisTemplate;

    private static final int SCAN_COUNT = 100;
    private static final int BATCH_DELETE_SIZE = 500;

    /**
     * 扫描匹配的 key 并逐个处理
     */
    public void scanAndProcess(String pattern, Consumer<String> processor) {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                processor.accept(cursor.next());
            }
        } catch (Exception e) {
            log.error("SCAN 迭代失败: pattern={}", pattern, e);
        }
    }

    /**
     * 扫描匹配的 key 并分批删除（防止一次性删除过多导致 Redis 卡顿）
     * 
     * @return 删除的 key 数量
     */
    public long deleteByPattern(String pattern) {
        Set<String> batch = new HashSet<>();
        long totalDeleted = 0;

        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= BATCH_DELETE_SIZE) {
                    totalDeleted += redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                totalDeleted += redisTemplate.delete(batch);
            }
        } catch (Exception e) {
            log.error("SCAN 删除失败: pattern={}", pattern, e);
        }

        log.info("SCAN 删除完成: pattern={}, deleted={}", pattern, totalDeleted);
        return totalDeleted;
    }

    /**
     * 扫描匹配的 key 并返回集合
     */
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("SCAN 获取 key 失败: pattern={}", pattern, e);
        }

        return keys;
    }
}
