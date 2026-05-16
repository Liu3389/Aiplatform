package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.service.PlatformModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform-model")
@RequiredArgsConstructor
public class PlatformModelController {

    private final PlatformModelService platformModelService;

    @GetMapping("/list")
    public Result<List<PlatformModelConfig>> listModels() {
        List<PlatformModelConfig> models = platformModelService.listEnabledModels();
        return Result.success(models);
    }
}
