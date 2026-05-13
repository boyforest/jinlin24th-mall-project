package com.jinlin24th.jinlin.common.exception;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转换成 Result 返回
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> bizException(BizException e) {
        log.warn("业务异常：httpStatus={},code={},bizCode={},message={}",
                e.getHttpStatus(), e.getCode(), e.getBizCode(), e.getMessage());
        return response(e.getBizCode(), e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleValidException(Exception e) {
        log.warn("请求参数异常：{}", e.getMessage());
        return response(BizCode.PARAM_INVALID);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return response(BizCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("请求内容类型不支持：{}", e.getMessage());
        return response(BizCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknownException(Exception e) {
        log.error("系统异常", e);
        return response(BizCode.SYSTEM_ERROR);
    }

    private ResponseEntity<Result<Void>> response(BizCode bizCode) {
        return response(bizCode, bizCode.getMessage());
    }

    private ResponseEntity<Result<Void>> response(BizCode bizCode, String message) {
        return ResponseEntity.status(bizCode.getHttpStatus())
                .body(Result.error(bizCode, message));
    }
}
