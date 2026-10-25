package com.jinlin24th.jinlin.coffee;

import static org.mockito.Mockito.mock;

import cn.binarywang.wx.miniapp.api.WxMaService;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.config.*;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.service.*;
import com.jinlin24th.jinlin.service.impl.*;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import javax.sql.DataSource;

@org.springframework.boot.test.context.TestConfiguration
@EnableTransactionManagement
@MapperScan("com.jinlin24th.jinlin.mapper")
@Import({
    SysAdminServiceImpl.class,
    AdminPermissionService.class,
    CoffeeAdminAccessService.class,
    AdminAccountService.class,
    CoffeeCatalogSupport.class,
    CoffeeCatalogServiceImpl.class,
    CoffeeOrderService.class,
    CoffeePaymentService.class,
    CoffeeNotificationService.class,
    com.jinlin24th.jinlin.task.CoffeeRecoveryTask.class
})
public class WorkbenchTestConfig {
    @Bean
    DataSource dataSource() throws Exception {
        String mysql = System.getProperty("coffee.test.mysql.url");
        if (mysql != null
                && !mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/coffee_phase_a_test\\?.*"))
            throw new IllegalArgumentException("只允许本机一次性库");
        DriverManagerDataSource ds =
                new DriverManagerDataSource(
                        mysql == null
                                ? "jdbc:h2:mem:coffee_workbench;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
                                : mysql,
                        mysql == null ? "sa" : "root",
                        mysql == null ? "" : "coffee-test-only");
        String schema = Files.readString(Path.of("sql/jinlin24th_init.sql"));
        StringBuilder sql = new StringBuilder();
        for (String table :
                List.of(
                        "sys_admin",
                        "sys_role",
                        "sys_permission",
                        "sys_admin_role",
                        "sys_role_permission",
                        "sys_operation_log",
                        "app_user")) {
            var matcher =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS `" + table + "` \\(.*?;",
                                    Pattern.DOTALL)
                            .matcher(schema);
            if (!matcher.find()) throw new IllegalStateException(table);
            sql.append(
                            mysql == null
                                    ? matcher.group().replace("KEY `", "KEY `" + table + "_")
                                    : matcher.group())
                    .append('\n');
        }
        sql.append(Files.readString(Path.of("sql/migrations/20261024_coffee_catalog_cart.sql")));
        sql.append(Files.readString(Path.of("sql/migrations/20261026_coffee_workbench_rbac.sql")));
        String ddl = sql.toString();
        if (mysql == null)
            ddl =
                    ddl.replaceAll("(?m)^SET NAMES utf8mb4;", "")
                            .replaceAll("ENGINE=InnoDB[^;]*;", ";")
                            .replace("CHARACTER SET ascii COLLATE ascii_bin", "");
        new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)))
                .execute(ds);
        return ds;
    }

    @Bean
    org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
        var c = new MybatisConfiguration();
        c.setMapUnderscoreToCamelCase(true);
        c.setCacheEnabled(false);
        GlobalConfig global = GlobalConfigUtils.defaults();
        global.setMetaObjectHandler(new AutoFillHandler());
        GlobalConfigUtils.setGlobalConfig(c, global);
        var f = new MybatisSqlSessionFactoryBean();
        f.setDataSource(ds);
        f.setConfiguration(c);
        f.setGlobalConfig(global);
        f.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        return f.getObject();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(4);
    }

    @Bean
    JwtUtil jwt() {
        return mock(JwtUtil.class);
    }

    @Bean
    AuthSessionService sessions() {
        return mock(AuthSessionService.class);
    }

    @Bean
    CoffeePayClient client() {
        return mock(CoffeePayClient.class);
    }

    @Bean
    WxMaService wx() {
        return mock(WxMaService.class);
    }

    @Bean
    ObjectMapper json() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    CoffeePayConfig coffeeConfig() {
        var c = new CoffeePayConfig();
        c.setOrderingEnabled(true);
        c.setPaymentEnabled(true);
        c.setMchId("coffee-merchant");
        c.setAppId("coffee-app");
        c.setApiV3Key("12345678901234567890123456789012");
        c.setSerialNo("merchant-serial");
        c.setPlatformPublicKeyId("platform-key");
        c.setPrivateKeyPath("test-only");
        c.setPlatformPublicKeyPath("test-only");
        c.setNotifyUrl("https://test.invalid/api/coffee/payment/notify");
        c.setRefundNotifyUrl("https://test.invalid/api/coffee/refund/notify");
        return c;
    }
}
