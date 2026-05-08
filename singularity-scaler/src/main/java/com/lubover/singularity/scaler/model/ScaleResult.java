package com.lubover.singularity.scaler.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScaleResult {
    private String serviceName;
    private ScaleAction action;
    private String message;
}
