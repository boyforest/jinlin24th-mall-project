package com.jinlin24th.jinlin.common.exception;

import com.jinlin24th.jinlin.common.constant.BizCode;
import lombok.Getter;

/**
 * 业务异常（用于向前端返回可预期的错误码与错误信息）
 */
@Getter
public class BizException extends RuntimeException {

    private final BizCode bizCode;

    public BizException(BizCode bizCode, String message) {
        super(message);
        this.bizCode = bizCode;
    }

    public static BizException unauthorized(String message) {
        return new BizException(BizCode.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(BizCode.FORBIDDEN, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(BizCode.BAD_REQUEST, message);
    }

    public static BizException of(BizCode bizCode) {
        return new BizException(bizCode, bizCode.getMessage());
    }

    public static BizException of(BizCode bizCode, String message) {
        return new BizException(bizCode, message);
    }

    public int getCode() {
        return bizCode.getCode();
    }

    public int getHttpStatus() {
        return bizCode.getHttpStatus();
    }
}
