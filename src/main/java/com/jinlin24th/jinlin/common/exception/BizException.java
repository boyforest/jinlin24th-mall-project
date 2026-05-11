package com.jinlin24th.jinlin.common.exception;

import lombok.Getter;

/**
 * 业务异常（用于向前端返回可预期的错误码与错误信息）
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }
}

