package com.jinlin24th.jinlin.common.result;

import com.jinlin24th.jinlin.common.constant.BizCode;
import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(BizCode.SUCCESS.getCode());
        result.setMessage(BizCode.SUCCESS.getMessage());
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(BizCode.SUCCESS.getCode());
        result.setMessage(BizCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(BizCode.SYSTEM_ERROR.getCode(), message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(BizCode bizCode) {
        Result<T> result = new Result<>();
        result.setCode(bizCode.getCode());
        result.setMessage(bizCode.getMessage());
        return result;
    }

    public static <T> Result<T> error(BizCode bizCode, String message) {
        Result<T> result = new Result<>();
        result.setCode(bizCode.getCode());
        result.setMessage(message);
        return result;
    }
}
