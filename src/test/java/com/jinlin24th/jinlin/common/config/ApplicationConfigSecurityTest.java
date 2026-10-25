package com.jinlin24th.jinlin.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置安全默认值的回归测试。
 * <p>
 * 背景（这是已经真实发生过一次的事故）：生产环境此前没有 application-prod.yml，
 * 而基座配置里 {@code log-impl: StdOutImpl} 且没有任何 springdoc 开关，
 * 导致生产环境 Swagger 公网可访问、MyBatis 打印全量 SQL（含参数与 PII）。
 * 根因是"安全开关只写在未生效的模板文件里"。
 * <p>
 * 本测试把默认值锁死，防止有人再次把调试开关放回基座配置：
 * <ul>
 *   <li>基座 application.yml 必须是安全默认（关 Swagger、关 SQL 日志）；</li>
 *   <li>application-dev.yml 才允许反向打开（本地开发需要）；</li>
 *   <li>application-prod.yml 必须存在且关闭两者。</li>
 * </ul>
 * 说明：一般不对配置文件做测试，但此处不是"配置正确性"，而是安全默认值的防回归锁，
 * 且该缺陷已经造成过真实暴露。
 */
class ApplicationConfigSecurityTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private Map<String, Object> load(String fileName) throws IOException {
        assertTrue(Files.exists(RESOURCES.resolve(fileName)),
            fileName + " 必须存在（路径：" + RESOURCES.toAbsolutePath() + "）");
        try (InputStream in = Files.newInputStream(RESOURCES.resolve(fileName))) {
            // application.yml 含 `---` 分隔的多文档（profile 段落），
            // 基座安全开关在第一个文档里，因此只取首文档。
            for (Object document : new Yaml().loadAll(in)) {
                if (document instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    return typed;
                }
            }
            throw new IllegalStateException(fileName + " 首个 YAML 文档为空");
        }
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }

    @Test
    @DisplayName("基座配置必须默认关闭 Swagger（生产暴露接口签名的回归）")
    void baseConfigMustDisableSwaggerByDefault() throws IOException {
        Map<String, Object> base = load("application.yml");

        assertEquals(Boolean.FALSE, nested(base, "springdoc", "api-docs", "enabled"),
            "基座必须默认关闭 /v3/api-docs");
        assertEquals(Boolean.FALSE, nested(base, "springdoc", "swagger-ui", "enabled"),
            "基座必须默认关闭 /swagger-ui");
    }

    @Test
    @DisplayName("基座配置必须默认关闭 SQL 日志（打印含参数 SQL 的回归）")
    void baseConfigMustDisableSqlLoggingByDefault() throws IOException {
        Map<String, Object> base = load("application.yml");

        assertEquals("org.apache.ibatis.logging.nologging.NoLoggingImpl",
            nested(base, "mybatis-plus", "configuration", "log-impl"),
            "基座必须默认关闭 MyBatis SQL 日志，否则每条查询含参数都会进 stdout");
    }

    @Test
    @DisplayName("基座配置不得包含 JWT 密钥硬编码兜底值")
    void baseConfigMustNotHardcodeJwtSecret() throws IOException {
        Map<String, Object> base = load("application.yml");

        String secret = String.valueOf(nested(base, "jwt", "secret"));
        assertEquals("${JWT_SECRET}", secret,
            "JWT 密钥必须只来自环境变量，不得有默认值（历史泄漏值曾被写进兜底默认）");
    }

    @Test
    @DisplayName("dev profile 可以反向开启调试开关（仅本地开发）")
    void devProfileMayEnableDebugSwitches() throws IOException {
        // 新检出和 CI 使用已提交的 dev 示例，不依赖个人未入库的数据库配置。
        Map<String, Object> dev = load("application-dev.example.yml");

        assertEquals("org.apache.ibatis.logging.stdout.StdOutImpl",
            nested(dev, "mybatis-plus", "configuration", "log-impl"),
            "本地开发需要 SQL 日志");
        assertEquals(Boolean.TRUE, nested(dev, "springdoc", "swagger-ui", "enabled"),
            "本地开发需要 Swagger");
    }

    @Test
    @DisplayName("application-prod.yml 必须入库且关闭 Swagger 与 SQL 日志")
    void productionProfileMustExistAndBeSecure() throws IOException {
        Map<String, Object> prod = load("application-prod.yml");

        assertEquals(Boolean.FALSE, nested(prod, "springdoc", "api-docs", "enabled"));
        assertEquals(Boolean.FALSE, nested(prod, "springdoc", "swagger-ui", "enabled"));
        assertEquals("org.apache.ibatis.logging.nologging.NoLoggingImpl",
            nested(prod, "mybatis-plus", "configuration", "log-impl"));
    }
}
