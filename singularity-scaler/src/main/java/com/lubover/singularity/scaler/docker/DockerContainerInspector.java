package com.lubover.singularity.scaler.docker;

import com.lubover.singularity.scaler.model.ResourceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DockerContainerInspector {

    private static final Pattern PORT_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+:(\\d+)->");

    public List<DockerContainerInfo> listContainers() {
        List<DockerContainerInfo> result = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "--format", "{{.Names}}\t{{.Ports}}"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", 2);
                    if (parts.length < 1) {
                        continue;
                    }
                    DockerContainerInfo info = new DockerContainerInfo();
                    info.setName(parts[0].trim());
                    if (parts.length > 1) {
                        Matcher m = PORT_PATTERN.matcher(parts[1]);
                        while (m.find()) {
                            info.getHostPorts().add(Integer.parseInt(m.group(1)));
                        }
                    }
                    result.add(info);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.error("Failed to list docker containers", e);
        }
        return result;
    }

    public List<String> getContainerNamesForService(String serviceName) {
        String prefix = serviceName + "-";
        List<String> names = new ArrayList<>();
        for (DockerContainerInfo info : listContainers()) {
            String name = info.getName();
            if (name.startsWith(prefix) && name.substring(prefix.length()).matches("\\d+")) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Compose 编排下副本容器名形如 {@code singularity-singularity-order-1}，不再匹配 {@code singularity-order-0}；
     * 用 compose 标签计数。
     */
    public int countComposeReplicas(String composeProject, String composeService) {
        if (composeProject == null || composeService == null) {
            return 0;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-q",
                    "-f", "label=com.docker.compose.project=" + composeProject,
                    "-f", "label=com.docker.compose.service=" + composeService
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int n = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    n++;
                }
            }
            process.waitFor();
            return n;
        } catch (Exception e) {
            log.error("Failed to count compose replicas for project={} service={}", composeProject, composeService, e);
            return 0;
        }
    }

    /** 列出 compose 服务当前运行中的容器 ID（短 ID 亦可用于 inspect/stats）。 */
    public List<String> listComposeServiceContainerIds(String composeProject, String composeService) {
        List<String> ids = new ArrayList<>();
        if (composeProject == null || composeService == null) {
            return ids;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-q",
                    "-f", "label=com.docker.compose.project=" + composeProject,
                    "-f", "label=com.docker.compose.service=" + composeService
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String id = line.trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("docker ps for ids failed project={} service={}: {}", composeProject, composeService, e.getMessage());
        }
        return ids;
    }

    /**
     * 多副本时取「最年轻」副本自 StartedAt 起的存活秒数；扩容前可与 grace 比较，避免启动期 stats 误触。
     */
    public OptionalLong youngestReplicaUptimeSeconds(String composeProject, String composeService) {
        List<String> ids = listComposeServiceContainerIds(composeProject, composeService);
        if (ids.isEmpty()) {
            return OptionalLong.empty();
        }
        long minAge = Long.MAX_VALUE;
        for (String id : ids) {
            OptionalLong age = containerStartedAgeSeconds(id);
            if (age.isPresent()) {
                minAge = Math.min(minAge, age.getAsLong());
            }
        }
        if (minAge == Long.MAX_VALUE) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(minAge);
    }

    private OptionalLong containerStartedAgeSeconds(String containerId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "-f", "{{.State.StartedAt}}", containerId
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = reader.readLine();
            }
            int code = process.waitFor();
            if (code != 0 || line == null || line.isBlank()) {
                return OptionalLong.empty();
            }
            String raw = line.trim();
            if (raw.startsWith("0001-01-01")) {
                return OptionalLong.empty();
            }
            Instant started = Instant.parse(raw);
            long sec = Duration.between(started, Instant.now()).getSeconds();
            return OptionalLong.of(Math.max(0L, sec));
        } catch (Exception e) {
            log.debug("inspect StartedAt failed for {}: {}", containerId, e.getMessage());
            return OptionalLong.empty();
        }
    }

    /**
     * 从 docker stats 读取 compose 服务下各副本的 CPU / 内存占比（与 {@code docker stats} 一致），
     * 取副本间偏保守的 max 再归一化到 0~1，供扩容决策；不依赖 actuator，避免高压下 metrics 拉不动。
     */
    public Optional<ResourceMetrics> sampleComposeServiceDockerStats(String composeProject, String composeService) {
        if (composeProject == null || composeService == null) {
            return Optional.empty();
        }
        List<String> ids = listComposeServiceContainerIds(composeProject, composeService);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("stats");
        cmd.add("--no-stream");
        cmd.add("--format");
        cmd.add("{{.CPUPerc}}\t{{.MemPerc}}");
        cmd.addAll(ids);
        double maxCpuPct = 0.0;
        double maxMemPct = 0.0;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\t");
                    if (parts.length < 2) {
                        continue;
                    }
                    maxCpuPct = Math.max(maxCpuPct, parsePercent(parts[0]));
                    maxMemPct = Math.max(maxMemPct, parsePercent(parts[1]));
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                log.warn("docker stats exited {} for {}", code, composeService);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("docker stats failed for {}: {}", composeService, e.getMessage());
            return Optional.empty();
        }
        // 与 PolicyEvaluator 阈值（0~1）对齐：stats 为百分比；CPU 可能 >100%（多核）
        double cpuUsage = Math.min(1.0, maxCpuPct / 100.0);
        double memoryUsage = Math.min(1.0, maxMemPct / 100.0);
        return Optional.of(new ResourceMetrics(0.0, cpuUsage, memoryUsage));
    }

    private static double parsePercent(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String s = raw.trim();
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public int getMaxIndex(String serviceName) {
        String prefix = serviceName + "-";
        int max = -1;
        for (DockerContainerInfo info : listContainers()) {
            String name = info.getName();
            if (name.startsWith(prefix)) {
                String suffix = name.substring(prefix.length());
                try {
                    int idx = Integer.parseInt(suffix);
                    if (idx > max) {
                        max = idx;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }
}
