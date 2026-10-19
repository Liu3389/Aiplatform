package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/user/usage")
@RequiredArgsConstructor
public class UsageAnalyticsController {
    private final JdbcTemplate jdbc;
    private final TokenService tokenService;

    @GetMapping("/analytics")
    @Operation(summary = "个人用量分析", description = "最近365天，按日期及模型聚合成功调用与实际点数流水；不返回API密钥")
    public Result<Map<String, Object>> analytics(
            @RequestHeader(value = "X-User-Token", required = false) String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) return Result.error(401, "Token无效或已过期");
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate start = end.minusDays(364);
        // 在数据库内聚合，避免把一整年的原始调用日志加载进2G服务器内存。
        List<Map<String, Object>> calls = jdbc.queryForList("""
                SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
                       COALESCE(model, '') AS model,
                       COUNT(*) AS calls,
                       COALESCE(SUM(input_tokens), 0) AS inputTokens,
                       COALESCE(SUM(output_tokens), 0) AS outputTokens
                FROM call_log
                WHERE user_id = ? AND status = 1 AND create_time >= ? AND create_time < ?
                GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d'), model
                ORDER BY date, model
                """, userId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<Map<String, Object>> points = jdbc.queryForList("""
                SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
                       COALESCE(model_name, '') AS model,
                       SUM(ABS(points_change)) AS points
                FROM points_bill
                WHERE user_id = ? AND change_type IN ('SETTLE', 'DEDUCT')
                  AND points_change < 0 AND create_time >= ? AND create_time < ?
                GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d'), model_name
                ORDER BY date, model
                """, userId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return Result.success(Map.of("startDate", start.toString(), "endDate", end.toString(),
                "timezone", "Asia/Shanghai", "calls", calls, "points", points));
    }
}
