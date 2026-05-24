package com.lubover.singularity.pipeline;

import java.util.Map;

public interface Operation {

    String getId();

    OperationType getType();

    String getKey();

    Map<String, Object> getMetadata();
}
