package com.jinlin24th.jinlin.common.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.annotation.DbType;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页越界的真实 SQL 行为测试（H2 内存库）。
 * <p>
 * 修复的缺陷：15 个列表接口直接接收未校验的 {@code size}，而 MyBatis-Plus 的
 * {@code PaginationInnerInterceptor} 在 {@code size < 0} 且未设置 maxLimit 时会
 * **跳过整个分页**（不构造 LIMIT），于是 {@code GET /user/order/list?size=-1}
 * 能一次拉走全表——既是性能风险也是数据导出风险。
 * <p>
 * 本测试用真实数据库跑真实 SQL，证明：
 * <ol>
 *   <li>不设 maxLimit 时 {@code size=-1} 确实返回全表（漏洞存在）；</li>
 *   <li>设了 maxLimit 后 {@code size=-1} 被钳制到 maxLimit（修复有效）。</li>
 * </ol>
 * 结论依据来自 {@code PaginationInnerInterceptor.beforeQuery} 的真实分支：
 * {@code if (size < 0 && currentMaxLimit == null) return;} —— 只要配置了 maxLimit，
 * 就会继续走 {@code handlerLimit}，把负 size 钳成 maxLimit。
 */
class PaginationLimitIntegrationTest {

    private static final int TOTAL_ROWS = 150;
    private static final long MAX_LIMIT = 100L;

    private static DataSource dataSource;

    @BeforeAll
    static void prepareDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pagination_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS page_demo");
            st.execute("CREATE TABLE page_demo (id BIGINT PRIMARY KEY, name VARCHAR(64))");
            for (int i = 1; i <= TOTAL_ROWS; i++) {
                st.execute("INSERT INTO page_demo (id, name) VALUES (" + i + ", 'row-" + i + "')");
            }
        }
    }

    @TableName("page_demo")
    public static class PageDemo {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Mapper
    public interface PageDemoMapper {
        @Select("SELECT id, name FROM page_demo ORDER BY id")
        IPage<PageDemo> selectPage(IPage<PageDemo> page);
    }

    /** 构造一个带/不带 maxLimit 的 MyBatis-Plus 会话工厂。 */
    private SqlSessionFactory sessionFactory(Long maxLimit) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
            "test", new JdbcTransactionFactory(), dataSource));

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2);
        if (maxLimit != null) {
            pagination.setMaxLimit(maxLimit);
        }
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setPlugins(interceptor);
        factoryBean.setTypeAliases(PageDemo.class);
        factoryBean.setMapperLocations();
        SqlSessionFactory factory = factoryBean.getObject();
        // 注册测试 Mapper（避免引入整个应用的 mapper 扫描）
        factory.getConfiguration().addMapper(PageDemoMapper.class);
        return factory;
    }

    private List<PageDemo> queryWithSize(Long maxLimit, long size) throws Exception {
        try (SqlSession session = sessionFactory(maxLimit).openSession()) {
            PageDemoMapper mapper = session.getMapper(PageDemoMapper.class);
            IPage<PageDemo> page = new Page<>(1, size);
            return mapper.selectPage(page).getRecords();
        }
    }

    @Test
    @DisplayName("不设 maxLimit 时 size=-1 会拉走全表（这就是漏洞本身）")
    void withoutMaxLimitNegativeSizeReturnsEverything() throws Exception {
        List<PageDemo> records = queryWithSize(null, -1);

        assertEquals(TOTAL_ROWS, records.size(),
            "未设 maxLimit 时 size=-1 跳过分页、返回全表——本测试确认该风险真实存在");
    }

    @Test
    @DisplayName("设了 maxLimit 后 size=-1 被钳制到上限（修复生效）")
    void withMaxLimitNegativeSizeIsClamped() throws Exception {
        List<PageDemo> records = queryWithSize(MAX_LIMIT, -1);

        assertEquals((int) MAX_LIMIT, records.size(),
            "配置 maxLimit 后，负 size 必须被钳制为 maxLimit，而不是返回全表");
        assertTrue(records.size() < TOTAL_ROWS, "必须少于总行数，否则等于没保护");
    }

    @Test
    @DisplayName("超大 size 同样被钳制到上限")
    void oversizedSizeIsClamped() throws Exception {
        List<PageDemo> records = queryWithSize(MAX_LIMIT, 1_000_000L);

        assertEquals((int) MAX_LIMIT, records.size(),
            "size 远超上限时必须被钳制，否则仍可一次拉走全表");
    }

    @Test
    @DisplayName("正常 size 不受影响，分页语义保持正确")
    void normalSizeIsUnaffected() throws Exception {
        assertEquals(10, queryWithSize(MAX_LIMIT, 10).size());
        assertEquals(1, queryWithSize(MAX_LIMIT, 1).size());
    }

    @Test
    @DisplayName("生产配置必须真的设置了 maxLimit（防止有人把这一行删掉）")
    void productionConfigMustConfigureMaxLimit() throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        List<com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor> inner =
            interceptor.getInterceptors();
        PaginationInnerInterceptor pagination = inner.stream()
            .filter(PaginationInnerInterceptor.class::isInstance)
            .map(PaginationInnerInterceptor.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("必须注册分页拦截器"));

        Long maxLimit = pagination.getMaxLimit();
        assertTrue(maxLimit != null && maxLimit > 0,
            "生产分页拦截器必须设置 maxLimit，否则 size=-1 可全表导出");
        assertTrue(maxLimit <= 200,
            "maxLimit 应是一个合理的上限（当前 " + maxLimit + "）");
    }
}
