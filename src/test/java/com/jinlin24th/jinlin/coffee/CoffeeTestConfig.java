package com.jinlin24th.jinlin.coffee;

import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jinlin24th.jinlin.common.auth.CoffeeAdminAccessService;
import com.jinlin24th.jinlin.common.config.AutoFillHandler;
import com.jinlin24th.jinlin.common.config.MybatisPlusConfig;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.service.*;
import com.jinlin24th.jinlin.service.impl.*;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

/** 仅隔离的内存库或一次性本机 MySQL 容器，不读取应用环境/生产配置。 */
@org.springframework.boot.test.context.TestConfiguration
@EnableTransactionManagement
public class CoffeeTestConfig {
    @Bean
    DataSource dataSource() throws Exception {
        String mysqlUrl = System.getProperty("coffee.test.mysql.url");
        if (mysqlUrl != null
                && !mysqlUrl.matches(
                        "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/coffee_phase_a_test\\?.*")) {
            throw new IllegalArgumentException("MySQL 验证仅允许本机 coffee_phase_a_test 临时库");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(
                mysqlUrl == null
                        ? "jdbc:h2:mem:coffee_phase_a;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
                        : mysqlUrl);
        ds.setUsername(mysqlUrl == null ? "sa" : "root");
        ds.setPassword(mysqlUrl == null ? "" : "coffee-test-only");
        String sql = Files.readString(Path.of("sql/migrations/20261024_coffee_catalog_cart.sql"));
        String schema = Files.readString(Path.of("sql/jinlin24th_init.sql"));
        for (String table : List.of("order_master", "order_item", "payment_record")) {
            var matcher =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS `" + table + "` \\(.*?;",
                                    Pattern.DOTALL)
                            .matcher(schema);
            if (!matcher.find()) {
                throw new IllegalStateException("找不到原表结构：" + table);
            }
            sql +=
                    "\n"
                            + (mysqlUrl == null
                                    ? matcher.group().replace("KEY `", "KEY `" + table + "_")
                                    : matcher.group());
        }
        if (mysqlUrl == null) {
            sql =
                    sql.replaceAll("(?m)^SET NAMES utf8mb4;", "")
                            .replaceAll("ENGINE=InnoDB[^;]*;", ";");
        }
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)))
                .execute(ds);
        return ds;
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        GlobalConfig global = GlobalConfigUtils.defaults();
        global.setMetaObjectHandler(new AutoFillHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        for (Class<?> mapper :
                List.of(
                        CoffeeStoreMapper.class,
                        CoffeeCategoryMapper.class,
                        CoffeeProductMapper.class,
                        CoffeeSkuMapper.class,
                        CoffeeCartMapper.class,
                        OrderMasterMapper.class,
                        OrderItemMapper.class,
                        PaymentRecordMapper.class)) {
            configuration.addMapper(mapper);
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(global);
        factory.setPlugins(
                new MybatisPlusConfig().mybatisPlusInterceptor(), new CoffeeQueryCounter());
        return factory.getObject();
    }

    @Bean
    SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    CoffeeStoreMapper storeMapper(SqlSessionTemplate t) {
        return t.getMapper(CoffeeStoreMapper.class);
    }

    @Bean
    CoffeeCategoryMapper categoryMapper(SqlSessionTemplate t) {
        return t.getMapper(CoffeeCategoryMapper.class);
    }

    @Bean
    CoffeeProductMapper productMapper(SqlSessionTemplate t) {
        return t.getMapper(CoffeeProductMapper.class);
    }

    @Bean
    CoffeeSkuMapper skuMapper(SqlSessionTemplate t) {
        return t.getMapper(CoffeeSkuMapper.class);
    }

    @Bean
    CoffeeCartMapper cartMapper(SqlSessionTemplate t) {
        return t.getMapper(CoffeeCartMapper.class);
    }

    @Bean
    OrderMasterMapper orderMapper(SqlSessionTemplate t) {
        return t.getMapper(OrderMasterMapper.class);
    }

    @Bean
    OrderItemMapper itemMapper(SqlSessionTemplate t) {
        return t.getMapper(OrderItemMapper.class);
    }

    @Bean
    PaymentRecordMapper paymentMapper(SqlSessionTemplate t) {
        return t.getMapper(PaymentRecordMapper.class);
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
    SysAdminService admins() {
        return mock(SysAdminService.class);
    }

    @Bean
    CoffeeAdminAccessService access(SysAdminService admins) {
        return new CoffeeAdminAccessService(admins);
    }

    @Bean
    CoffeeCatalogSupport support(CoffeeStoreMapper stores) {
        return new CoffeeCatalogSupport(stores);
    }

    @Bean
    CoffeeCatalogService catalog(
            CoffeeStoreMapper stores,
            CoffeeCategoryMapper categories,
            CoffeeProductMapper products,
            CoffeeSkuMapper skus,
            CoffeeCatalogSupport support,
            CoffeeAdminAccessService access) {
        return new CoffeeCatalogServiceImpl(stores, categories, products, skus, support, access);
    }

    @Bean
    CoffeeCartAppService cart(
            CoffeeCartMapper carts,
            CoffeeSkuMapper skus,
            CoffeeProductMapper products,
            CoffeeCategoryMapper categories,
            CoffeeCatalogSupport support) {
        return new CoffeeCartAppServiceImpl(carts, skus, products, categories, support);
    }

    @Bean
    BusinessOrderQueryService orders(
            OrderMasterMapper orders, OrderItemMapper items, PaymentRecordMapper payments) {
        return new BusinessOrderQueryServiceImpl(orders, items, payments);
    }
}
