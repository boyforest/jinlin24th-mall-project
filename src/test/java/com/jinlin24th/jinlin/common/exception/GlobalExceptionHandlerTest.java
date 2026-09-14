package com.jinlin24th.jinlin.common.exception;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 全局异常处理：未知访问路径应返回 404，而不是 500 系统异常。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noResourceFoundShouldReturn404InsteadOfSystemError() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/mcp");
        ResponseEntity<Result<Void>> response = handler.handleNoResourceFound(exception);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(40400, response.getBody().getCode());
        assertEquals(BizCode.NOT_FOUND.getMessage(), response.getBody().getMessage());
    }
}
