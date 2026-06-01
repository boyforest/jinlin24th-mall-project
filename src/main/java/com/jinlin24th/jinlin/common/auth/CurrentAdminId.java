package com.jinlin24th.jinlin.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 参数注解：注入当前登录管理员 ID
 * <p>
 * 用法：public Result<?> xxx(@CurrentAdminId Long adminId, ...)
 * <p>
 * ID 从 JWT Token 中提取（由 AdminJwtInterceptor 写入 CurrentUserContext），
 * 不可通过请求参数伪造。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAdminId {
}
