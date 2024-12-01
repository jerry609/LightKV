package com.kv.common.exception;

public class KVException extends RuntimeException {
    private ErrorCode errorCode;

    public KVException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public enum ErrorCode {
        KEY_NOT_FOUND,
        STORAGE_ERROR,
        NETWORK_ERROR,
        CONSISTENCY_ERROR
    }
}