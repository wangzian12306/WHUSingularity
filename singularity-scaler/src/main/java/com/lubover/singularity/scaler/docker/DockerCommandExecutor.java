package com.lubover.singularity.scaler.docker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DockerCommandExecutor {

    /**
     * 在同一 compose 项目内调整指定 service 的副本数（与单文件、DNS、负载一体）。
     * <p>
     * 使用 {@code --no-deps}：不启动依赖链上其它服务，运行中伸缩对其它容器影响最小。
     * 使用 {@code --no-recreate}：扩容时尽量不重建已在跑的容器，避免短暂无监听端口（压测里若 ORDER_TARGETS
     * 盯住固定容器名/IP，重建期间易出现 {@code connection refused}）。缩容仍会按副本数停掉多余实例：
     * 此时固定三条 URL 的 k6 可能仍打到已删容器，需改用服务名/LB 或压测前关 scaler。
     */
    public void scaleComposeService(String composeFile, String composeProject, String composeService, int desiredReplicas) {
        if (desiredReplicas < 0) {
            throw new IllegalArgumentException("desiredReplicas must be >= 0");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-f");
        cmd.add(composeFile);
        cmd.add("-p");
        cmd.add(composeProject);
        cmd.add("up");
        cmd.add("-d");
        cmd.add("--no-deps");
        cmd.add("--no-recreate");
        cmd.add("--scale");
        cmd.add(composeService + "=" + desiredReplicas);
        cmd.add(composeService);
        execute(cmd, "compose scale " + composeService + " to " + desiredReplicas);
    }

    private void execute(List<String> cmd, String description) {
        log.info("Executing: {}", String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Failed to {}: exitCode={}, output={}", description, exitCode, output);
                throw new RuntimeException("Docker command failed for " + description + ": " + output);
            }
            log.info("Successfully {}: {}", description, output.toString().trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to " + description, e);
        }
    }
}
