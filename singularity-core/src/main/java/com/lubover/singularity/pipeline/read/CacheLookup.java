package com.lubover.singularity.pipeline.read;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CacheLookup<T> {

    private final CacheLookupState state;
    private final T value;
    private final Map<String, Object> meta;

    private CacheLookup(CacheLookupState state, T value, Map<String, Object> meta) {
        this.state = state;
        this.value = value;
        this.meta = meta == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(meta));
    }

    public static <T> CacheLookup<T> value(T value) {
        return new CacheLookup<>(CacheLookupState.HIT_VALUE, value, Collections.emptyMap());
    }

    public static <T> CacheLookup<T> nullHit() {
        return new CacheLookup<>(CacheLookupState.HIT_NULL, null, Collections.emptyMap());
    }

    public static <T> CacheLookup<T> miss() {
        return new CacheLookup<>(CacheLookupState.MISS, null, Collections.emptyMap());
    }

    public CacheLookup<T> withMeta(Map<String, Object> meta) {
        return new CacheLookup<>(state, value, meta);
    }

    public CacheLookupState getState() {
        return state;
    }

    public T getValue() {
        return value;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
