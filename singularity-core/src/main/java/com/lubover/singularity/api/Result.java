package com.lubover.singularity.api;

/**
 * 在 allocate 过程中可能产生的结果
 */
public class Result {

    boolean isSuccess = false;

    String message;
    
    /**
     * Result 的构造函数，可以由开发者创建
     */
    public Result(boolean isSuccess, String message) {
        this.isSuccess = isSuccess;
        this.message = message;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
