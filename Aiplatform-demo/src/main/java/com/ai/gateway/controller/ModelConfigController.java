package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.ModelConfig;
import com.ai.gateway.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/model-config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/list")
    public Result<List<ModelConfig>> listModels() {
        List<ModelConfig> models = modelConfigService.listEnabledModels();
        models.forEach(m -> m.setApiKey(null));
        return Result.success(models);
    }
}
