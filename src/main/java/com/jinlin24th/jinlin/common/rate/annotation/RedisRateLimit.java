package com.jinlin24th.jinlin.common.rate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 接口限流注解。
 * <p>
 * 使用方式：在 Controller 方法上添加该注解，切面会按 IP + 方法维度统计访问次数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisRateLimit {

    /**
     * 限流业务前缀，用于区分不同接口。
     */
    String key() default "";

    /**
     * 时间窗口，单位秒。
     */
    long windowSeconds() default 60;

    /**
     * 时间窗口内允许的最大请求数。
     */
    long limit() default 60;
}
