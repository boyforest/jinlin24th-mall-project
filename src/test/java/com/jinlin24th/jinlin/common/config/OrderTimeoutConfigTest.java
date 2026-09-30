package com.jinlin24th.jinlin.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 订单超时相关配置的解析测试。
 * <p>
 * `app.mq.order-timeout-cancel-delay-seconds` 使用了嵌套占位符
 * （`${ORDER_TIMEOUT_CANCEL_DELAY_SECONDS:${ORDER_TIMEOUT_CANCEL_SECONDS:1800}}`），
 * 目的是让"订单多久算超时"只有一个事实来源，同时保留单独覆盖 MQ 延迟的能力。
 * <p>
 * 风险：该属性只在 RocketMQ profile 激活、`RocketMQConfig` 绑定时才被解析，
 * 若占位符写错，报错会延迟到启用 MQ 的那一刻。因此这里用 Spring 自己的
 * YamlPropertySourceLoader + 占位符解析器**提前**验证它能解析出预期值。
 */
class OrderTimeoutConfigTest {

    private PropertySourcesPropertyResolver resolver() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources =
            loader.load("application", new ClassPathResource("application.yml"));

        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }

    @Test
    @DisplayName("订单超时时长默认 1800 秒")
    void timeoutShouldDefaultTo1800Seconds() throws Exception {
        assertEquals("1800", resolver().getProperty("app.order.timeout-cancel-seconds"));
    }

    @Test
    @DisplayName("MQ 延迟消息的延迟时长必须能解析，且默认与订单超时时长一致")
    void mqDelayShouldResolveToSameDefault() throws Exception {
        PropertySourcesPropertyResolver resolver = resolver();

        String mqDelay = resolver.getProperty("app.mq.order-timeout-cancel-delay-seconds");
        String orderTimeout = resolver.getProperty("app.order.timeout-cancel-seconds");

        assertEquals(orderTimeout, mqDelay,
            "两条取消路径的超时时限必须一致，否则 MQ 与兜底任务行为不一致");
        assertEquals("1800", mqDelay, "嵌套占位符必须能解析出数值，不能是未解析的原文");
    }

    @Test
    @DisplayName("兜底任务的扫描间隔与批量上限必须为正数")
    void scanSettingsShouldBePositive() throws Exception {
        PropertySourcesPropertyResolver resolver = resolver();

        assertEquals("300000", resolver.getProperty("app.order.timeout-scan-interval-ms"));
        assertEquals("200", resolver.getProperty("app.order.timeout-scan-batch-size"));
    }
}
