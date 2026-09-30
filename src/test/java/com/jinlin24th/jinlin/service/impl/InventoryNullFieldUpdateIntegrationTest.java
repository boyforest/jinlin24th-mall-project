package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 库存更新时「null 字段不覆盖」的真实 SQL 行为测试（H2 内存库）。
 * <p>
 * <b>为什么需要这个测试：</b>单仓模式下管理端「库存」页**隐藏了仓库字段**，
 * 于是编辑保存时 PUT 请求体里不再带 {@code warehouseId}。
 * 如果 MyBatis-Plus 的 {@code updateById} 会把 null 字段也写回去，
 * 那么每次改库存都会**把 warehouse_id 清空**，行随之变成孤儿数据（所有仓库都查不到它），
 * 表现为"库存明明改了却显示不出来"甚至商品无法下单——这是静默的数据损坏。
 * <p>
 * 本测试用真实数据库跑真实 UPDATE 语句，证明 {@code updateById} 默认策略
 * （{@code FieldStrategy.NOT_NULL}）**跳过 null 字段**，因此不传 warehouseId 是安全的。
 * 结论不靠推断，靠真实 SQL 验证。
 */
class InventoryNullFieldUpdateIntegrationTest {

    private static DataSource dataSource;

    @TableName("inv_demo")
    public static class InvDemo {
        @TableId(type = IdType.INPUT)
        private Long id;
        private Long warehouseId;
        private Long skuId;
        private Integer stock;
        private Integer warningStock;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(Long warehouseId) {
            this.warehouseId = warehouseId;
        }

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }

        public Integer getWarningStock() {
            return warningStock;
        }

        public void setWarningStock(Integer warningStock) {
            this.warningStock = warningStock;
        }
    }

    public interface InvDemoMapper extends BaseMapper<InvDemo> {
    }

    /** 每个用例都从同一初始状态开始（H2 内存库在同一 JVM 内共享，避免用例间互相污染）。 */
    @org.junit.jupiter.api.BeforeEach
    void resetRow() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM inv_demo");
            st.execute("INSERT INTO inv_demo (id, warehouse_id, sku_id, stock, warning_stock) "
                + "VALUES (1, 1, 99, 10, 5)");
        }
    }

    @BeforeAll
    static void prepareDatabase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:inv_update_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS inv_demo");
            st.execute("CREATE TABLE inv_demo ("
                + "id BIGINT PRIMARY KEY, "
                + "warehouse_id BIGINT, "
                + "sku_id BIGINT, "
                + "stock INT, "
                + "warning_stock INT)");
            st.execute("INSERT INTO inv_demo (id, warehouse_id, sku_id, stock, warning_stock) "
                + "VALUES (1, 1, 99, 10, 5)");
        }
    }

    private SqlSessionFactory sessionFactory() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
            "test", new JdbcTransactionFactory(), dataSource));

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setTypeAliases(InvDemo.class);
        SqlSessionFactory factory = factoryBean.getObject();
        // 注册测试 Mapper（避免引入整个应用的 mapper 扫描）
        factory.getConfiguration().addMapper(InvDemoMapper.class);
        return factory;
    }

    private InvDemo readRow() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, warehouse_id, sku_id, stock, warning_stock FROM inv_demo WHERE id = 1")) {
            rs.next();
            InvDemo row = new InvDemo();
            row.setId(rs.getLong("id"));
            row.setWarehouseId(rs.getLong("warehouse_id"));
            row.setSkuId(rs.getLong("sku_id"));
            row.setStock(rs.getInt("stock"));
            row.setWarningStock(rs.getInt("warning_stock"));
            return row;
        }
    }

    @Test
    @DisplayName("updateById 只带 stock（warehouseId/skuId 为 null）时，不得清空 warehouse_id")
    void nullFieldsMustNotOverwriteExistingValues() throws Exception {
        SqlSessionFactory factory = sessionFactory();

        try (SqlSession session = factory.openSession(true)) {
            InvDemoMapper mapper = session.getMapper(InvDemoMapper.class);
            // 模拟单仓模式下前端提交：只有 id 与要改的数量，没有 warehouseId/skuId
            InvDemo patch = new InvDemo();
            patch.setId(1L);
            patch.setStock(66);
            mapper.updateById(patch);
        }

        InvDemo after = readRow();
        assertEquals(66, after.getStock(), "库存应被更新");
        assertEquals(1L, after.getWarehouseId(), "仓库不得被清空（否则该行会变成孤儿数据）");
        assertEquals(99L, after.getSkuId(), "SKU 不得被清空");
        assertEquals(5, after.getWarningStock(), "未提交的预警库存应保持原值");
    }

    @Test
    @DisplayName("显式提交 warehouseId 时仍可更新（多仓模式行为不受影响）")
    void explicitFieldsStillUpdate() throws Exception {
        SqlSessionFactory factory = sessionFactory();

        try (SqlSession session = factory.openSession(true)) {
            InvDemoMapper mapper = session.getMapper(InvDemoMapper.class);
            InvDemo patch = new InvDemo();
            patch.setId(1L);
            patch.setWarehouseId(7L);
            patch.setStock(20);
            mapper.updateById(patch);
        }

        InvDemo after = readRow();
        assertEquals(7L, after.getWarehouseId());
        assertEquals(20, after.getStock());
    }
}
