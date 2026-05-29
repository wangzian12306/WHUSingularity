package com.lubover.singularity.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultOperation implements Operation {

    private final String id;
    private final OperationType type;
    private final String key;
    private final Map<String, Object> metadata;

    public DefaultOperation(String id, OperationType type, String key, Map<String, Object> metadata) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public static DefaultOperation read(String key) {
        return new DefaultOperation(null, OperationType.READ, key, Collections.emptyMap());
    }

    public static DefaultOperation read(String key, Map<String, Object> metadata) {
        return new DefaultOperation(null, OperationType.READ, key, metadata);
    }

    public static DefaultOperation write(String key, Map<String, Object> metadata) {
        return new DefaultOperation(null, OperationType.WRITE, key, metadata);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public OperationType getType() {
        return type;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
