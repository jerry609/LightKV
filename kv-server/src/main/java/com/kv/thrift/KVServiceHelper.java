package com.kv.thrift;

import java.util.concurrent.TimeoutException;

public class KVServiceHelper {
    public static KVServiceException wrapException(ErrorCode code, String message) {
        return new KVServiceException(code.getCode(), message);
    }

    public static KVServiceException wrapException(ErrorCode code, String message, TimeoutException e) {
        return new KVServiceException(code.getCode(), message + ": " + e.getMessage());
    }

    public static KVServiceException wrapException(ErrorCode code, String message, InterruptedException e) {
        return new KVServiceException(code.getCode(), message + ": " + e.getMessage());
    }

    public static KVServiceException wrapException(ErrorCode code, String message, Throwable e) {
        return new KVServiceException(code.getCode(), message + ": " + e.getMessage());
    }

    public static KVServiceException wrapException(ErrorCode code, String message, Exception e) {
        return new KVServiceException(code.getCode(), message + ": " + e.getMessage());
    }
}