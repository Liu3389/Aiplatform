package com.ai.gateway.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Docker 容器控制服务
 * <p>
 * 通过挂载的 /var/run/docker.sock 与 Docker 守护进程通信，
 * 实现容器的启动、停止、重启、状态查询和日志获取。
 * <p>
 * 容器名映射（与 docker-compose.prod.yml 中 container_name 一致）：
 * - mysql    → lingdian-mysql
 * - redis    → lingdian-redis
 * - backend  → lingdian-backend
 * - frontend → lingdian-frontend
 *
 * @author AI Gateway Platform
 */
@Slf4j
@Service
public class DockerControlService {

    private DockerClient dockerClient;

    /** 短名 → 容器名映射 */
    private static final Map<String, String> CONTAINER_NAME_MAP = Map.of(
            "mysql", "lingdian-mysql",
            "redis", "lingdian-redis",
            "backend", "lingdian-backend",
            "frontend", "lingdian-frontend"
    );

    /** 允许控制的容器短名白名单 */
    public static final Set<String> ALLOWED_CONTAINERS = CONTAINER_NAME_MAP.keySet();

    @PostConstruct
    public void init() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix:///var/run/docker.sock")
                    .build();
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            // 测试连接
            dockerClient.pingCmd().exec();
            log.info("Docker 客户端初始化成功, 已连接 docker.sock");
        } catch (Exception e) {
            log.warn("Docker 客户端初始化失败 (本地开发环境无 Docker 时正常): {}", e.getMessage());
            dockerClient = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Docker 是否可用 */
    public boolean isAvailable() {
        return dockerClient != null;
    }

    /** 短名转容器全名，非法名抛异常 */
    public String getContainerName(String shortName) {
        String fullName = CONTAINER_NAME_MAP.get(shortName);
        if (fullName == null) {
            throw new IllegalArgumentException("未知容器: " + shortName + ", 允许: " + ALLOWED_CONTAINERS);
        }
        return fullName;
    }

    /** 启动容器 */
    public void startContainer(String shortName) {
        ensureAvailable();
        String containerName = getContainerName(shortName);
        log.info("启动容器: {}", containerName);
        dockerClient.startContainerCmd(containerName).exec();
    }

    /** 停止容器 */
    public void stopContainer(String shortName) {
        ensureAvailable();
        String containerName = getContainerName(shortName);
        log.info("停止容器: {}", containerName);
        dockerClient.stopContainerCmd(containerName).withTimeout(10).exec();
    }

    /** 重启容器 */
    public void restartContainer(String shortName) {
        ensureAvailable();
        String containerName = getContainerName(shortName);
        log.info("重启容器: {}", containerName);
        dockerClient.restartContainerCmd(containerName).withTimeout(10).exec();
    }

    /**
     * 获取所有受控容器的状态
     *
     * @return 容器状态列表，每项包含 name/containerName/status/running/state/image/startedAt
     */
    public List<Map<String, Object>> getAllContainerStatus() {
        if (dockerClient == null) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (String shortName : ALLOWED_CONTAINERS) {
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("name", shortName);
                status.put("containerName", CONTAINER_NAME_MAP.get(shortName));
                status.put("status", "unavailable");
                status.put("running", false);
                status.put("state", "Docker不可用");
                status.put("image", "");
                status.put("startedAt", "");
                result.add(status);
            }
            return result;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();

        for (Map.Entry<String, String> entry : CONTAINER_NAME_MAP.entrySet()) {
            String shortName = entry.getKey();
            String containerName = entry.getValue();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("name", shortName);
            status.put("containerName", containerName);

            Container matched = containers.stream()
                    .filter(c -> Arrays.asList(c.getNames()).contains("/" + containerName))
                    .findFirst()
                    .orElse(null);

            if (matched == null) {
                status.put("status", "not_found");
                status.put("running", false);
                status.put("state", "不存在");
                status.put("image", "");
                status.put("startedAt", "");
            } else {
                String state = matched.getState();
                boolean running = "running".equals(state);
                status.put("status", state);
                status.put("running", running);
                status.put("state", running ? "运行中" : "已停止");
                status.put("image", matched.getImage());
                status.put("startedAt", formatStartedAt(containerName, running));
            }
            result.add(status);
        }
        return result;
    }

    /** 获取容器日志（最近 N 行） */
    public List<String> getContainerLogs(String shortName, int lines) {
        ensureAvailable();
        String containerName = getContainerName(shortName);
        List<String> logLines = new ArrayList<>();

        try {
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    String line = new String(frame.getPayload()).trim();
                    if (!line.isEmpty()) {
                        logLines.add(line);
                    }
                }
            };
            dockerClient.logContainerCmd(containerName)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(callback)
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取容器日志被中断: {}", containerName);
        }
        return logLines;
    }

    // ==================== 私有方法 ====================

    private void ensureAvailable() {
        if (dockerClient == null) {
            throw new IllegalStateException("Docker 不可用（未挂载 docker.sock 或本地无 Docker）");
        }
    }

    private String formatStartedAt(String containerName, boolean running) {
        if (!running) {
            return "";
        }
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerName).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            if (state != null && state.getStartedAt() != null) {
                return state.getStartedAt();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
