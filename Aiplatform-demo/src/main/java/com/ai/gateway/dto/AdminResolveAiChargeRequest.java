package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "依据上游计费证据处理一笔歧义 AI 预扣")
public class AdminResolveAiChargeRequest {
    @NotBlank
    @Pattern(regexp = "REFUND|SETTLE", message = "处理方式必须是 REFUND 或 SETTLE")
    @Schema(description = "REFUND=已确认上游未计费后退款；SETTLE=已确认上游计费后按实际点数结算")
    private String action;

    @DecimalMin(value = "0.001", message = "实际点数必须大于 0")
    @DecimalMax(value = "9999999.999", message = "实际点数超出支持范围")
    @Digits(integer = 7, fraction = 3, message = "实际点数最多保留 3 位小数")
    @Schema(description = "SETTLE 必填，且不得超过原预扣点数；REFUND 必须省略")
    private BigDecimal actualPoints;

    @NotBlank(message = "必须记录供应商日志编号或核验依据")
    @Size(min = 8, max = 1000, message = "核验依据长度必须为 8 到 1000 个字符")
    @Schema(description = "上游请求/账单日志引用及核验结论；不得粘贴密钥或完整敏感日志")
    private String evidence;
}
