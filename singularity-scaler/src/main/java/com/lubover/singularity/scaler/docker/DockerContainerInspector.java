package com.lubover.singularity.scaler.docker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
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
