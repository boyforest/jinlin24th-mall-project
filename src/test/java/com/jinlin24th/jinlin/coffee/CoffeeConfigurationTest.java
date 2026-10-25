package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class CoffeeConfigurationTest {
    @Test
    void coffeeSettingsBelongToAppAndKeepWechatConfigurationIntact() throws Exception {
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = (Map<?, ?>) new Yaml().loadAll(input).iterator().next();
        }
        assertEquals(
                "${COFFEE_CATALOG_ENABLED:false}",
                nested(config, "app", "coffee", "catalog-enabled"));
        assertEquals(
                "${GOODS_STORE_NAME:金霖二十四养}",
                nested(config, "app", "business-order", "goods-store-name"));
        assertEquals("${WX_MINIAPP_APPID:}", nested(config, "wx", "miniapp", "appid"));
        assertEquals("${WX_MINIAPP_SECRET:}", nested(config, "wx", "miniapp", "secret"));
        assertEquals("${WX_PAY_ENABLED:false}", nested(config, "wx", "pay", "enabled"));
    }

    @Test
    void composeForwardsCoffeeEnvironmentWithSafeDefaults() throws Exception {
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("docker-compose.yml"))) {
            config = new Yaml().load(input);
        }
        assertEquals(
                "${COFFEE_CATALOG_ENABLED:-false}",
                nested(config, "services", "backend", "environment", "COFFEE_CATALOG_ENABLED"));
        assertEquals(
                "${GOODS_STORE_NAME:-金霖二十四养}",
                nested(config, "services", "backend", "environment", "GOODS_STORE_NAME"));
    }

    private Object nested(Map<?, ?> config, String... keys) {
        Object value = config;
        for (String key : keys) {
            assertInstanceOf(Map.class, value);
            value = ((Map<?, ?>) value).get(key);
        }
        return value;
    }
}
