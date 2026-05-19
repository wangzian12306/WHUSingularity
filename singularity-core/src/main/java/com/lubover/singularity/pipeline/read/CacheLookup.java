package com.lubover.singularity.pipeline.read;

public class CacheLookup<T> {

    private final CacheLookupState state;
    private final T value;

    private CacheLookup(CacheLookupState state, T value) {
        this.state = state;
        this.value = value;
    }

    public static <T> CacheLookup<T> value(T value) {
        return new CacheLookup<>(CacheLookupState.HIT_VALUE, value);
    }

    public static <T> CacheLookup<T> nullHit() {
        return new CacheLookup<>(CacheLookupState.HIT_NULL, null);
    }

    public static <T> CacheLookup<T> miss() {
        return new CacheLookup<>(CacheLookupState.MISS, null);
    }

    public CacheLookupState getState() {
        return state;
    }

    public T getValue() {
        return value;
    }
}
