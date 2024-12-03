package com.kv.thrift;

public enum ErrorCode {
    TIMEOUT(1),
    INTERRUPTED(2),
    EXECUTION_ERROR(3),
    GET_ERROR(4),
    DELETE_ERROR(5),
    EXISTS_ERROR(6),
    BATCH_GET_ERROR(7),
    BATCH_PUT_ERROR(8),
    NODE_INFO_ERROR(9),
    INVALID_ARGUMENT(10),
    NOT_LEADER(11),      // 添加这个错误码
    UNKNOWN_ERROR(99);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}