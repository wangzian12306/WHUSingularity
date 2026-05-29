package com.lubover.singularity.pipeline.read;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ReadSlot {

    private final String id;
    private final Map<String, Object> metadata;

    public ReadSlot(String id) {
        this(id, Collections.emptyMap());
    }

    public ReadSlot(String id, Map<String, Object> metadata) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("read slot id must not be blank");
        }
        this.id = id;
        this.metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadSlot readSlot)) {
            return false;
        }
        return Objects.equals(id, readSlot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
