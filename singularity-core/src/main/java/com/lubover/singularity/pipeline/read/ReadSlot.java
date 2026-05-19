package com.lubover.singularity.pipeline.read;

import java.util.Objects;

public class ReadSlot {

    private final String id;

    public ReadSlot(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("read slot id must not be blank");
        }
        this.id = id;
    }

    public String getId() {
        return id;
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
