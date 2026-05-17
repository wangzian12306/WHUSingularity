package com.lubover.singularity.scaler.docker;

import com.lubover.singularity.scaler.config.ServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DockerCommandExecutor {

    /** 与 docker-compose.backend.yml 中 networks.default.name 一致 */
    private static final String DOCKER_NETWORK = "singularity-backend";
    /** compose name: singularity 时匿名卷名一般为 singularity_maven_repo */
    private static final String MAVEN_VOLUME = "singularity_maven_repo";

    private final String repoRoot;

    public DockerCommandExecutor() {
        String fromEnv = System.getenv("SCALER_REPO_ROOT");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            repoRoot = fromEnv;
        } else {
            String fromInspect = resolveRepoRootViaDockerInspect();
            if (fromInspect != null) {
                repoRoot = fromInspect;
            } else {
                Path current = Paths.get("").toAbsolutePath();
                if (current.endsWith("deploy")) {
                    repoRoot = current.getParent().toString();
                } else {
                    repoRoot = current.toString();
                }
            }
        }
    }

    private String resolveRepoRootViaDockerInspect() {
        String containerId = System.getenv("HOSTNAME");
        if (containerId == null || containerId.isEmpty()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "-f",
                    "{{ range .Mounts }}{{ if eq .Destination \"/workspace\" }}{{ .Source }}{{ end }}{{ end }}",
                    containerId
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                if (line != null && !line.isEmpty() && !line.startsWith("{{")) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve repo root via docker inspect", e);
        }
        return null;
    }

    public void startInstance(ServiceConfig config, int index, int port) {
        String containerName = config.getName() + "-" + index;
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--restart");
        cmd.add("unless-stopped");
        cmd.add("--network");
        cmd.add(DOCKER_NETWORK);
        cmd.add("-p");
        cmd.add(port + ":" + port);

        for (Map.Entry<String, String> entry : config.getEnv().entrySet()) {
            cmd.add("-e");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }
        cmd.add("-e");
        cmd.add("SERVER_PORT=" + port);

        cmd.add("-v");
        cmd.add(repoRoot + ":/workspace");
        cmd.add("-v");
        cmd.add(MAVEN_VOLUME + ":/root/.m2");
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add(config.getImage());
        cmd.add("sh");
        cmd.add("-c");

        String moduleName = config.getName();
        String jarName = moduleName + "/target/" + moduleName + "-1.0-SNAPSHOT.jar";
        cmd.add("java -jar " + jarName);

        execute(cmd, "start container " + containerName);
    }

    public void removeInstance(String containerName) {
        List<String> cmd = List.of("docker", "rm", "-f", containerName);
        execute(cmd, "remove container " + containerName);
    }

    /**
     * 在同一 compose 项目内调整指定 service 的副本数（与单文件、DNS、负载一体）。
     * <p>
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
