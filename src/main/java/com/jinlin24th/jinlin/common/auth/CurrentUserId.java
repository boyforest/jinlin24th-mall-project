package com.jinlin24th.jinlin.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 参数注解：注入当前登录用户ID
 * <p>
 * 用法：public Result<?> xxx(@CurrentUserId Long userId, ...)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
