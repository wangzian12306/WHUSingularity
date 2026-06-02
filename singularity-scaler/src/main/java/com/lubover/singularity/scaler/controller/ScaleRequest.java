package com.lubover.singularity.scaler.controller;

import com.lubover.singularity.scaler.model.ScaleAction;
import lombok.Data;

@Data
public class ScaleRequest {
    private String service;
    private ScaleAction action;
}
