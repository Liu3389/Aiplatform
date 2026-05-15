package com.ai.gateway.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Jackson 序列化配置
 * 统一处理 BigDecimal 返回前端的精度（保留两位小数）
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        
        // 自定义 BigDecimal 序列化器：前端展示统一保留两位小数
        module.addSerializer(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value != null) {
                    // 四舍五入保留两位小数
                    gen.writeNumber(value.setScale(2, RoundingMode.HALF_UP));
                } else {
                    gen.writeNull();
                }
            }
        });
        
        mapper.registerModule(module);
        return mapper;
    }
}
