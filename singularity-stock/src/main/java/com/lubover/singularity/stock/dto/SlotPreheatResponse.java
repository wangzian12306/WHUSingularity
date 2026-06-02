package com.lubover.singularity.stock.dto;

public class SlotPreheatResponse {

    private String redisKey;
    private boolean written;
    private String currentValue;
    private String message;

    public SlotPreheatResponse() {
    }

    public SlotPreheatResponse(String redisKey, boolean written, String currentValue, String message) {
        this.redisKey = redisKey;
        this.written = written;
        this.currentValue = currentValue;
        this.message = message;
    }

    public String getRedisKey() {
        return redisKey;
    }

    public void setRedisKey(String redisKey) {
        this.redisKey = redisKey;
    }

    public boolean isWritten() {
        return written;
    }

    public void setWritten(boolean written) {
        this.written = written;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
