package com.lubover.singularity.scaler.docker;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DockerContainerInfo {
    private String name;
    private List<Integer> hostPorts = new ArrayList<>();
}
