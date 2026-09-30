package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.mapper.WarehouseMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单仓模式判定与"建 SKU 自动建库存行"的测试。
 * <p>
 * 背景：上架一个商品要跨「商品 → 规格 → 库存」三个页面，且建完 SKU 若忘记去库存页建记录，
 * 商品**看起来已上架、实际库存为 0**，用户看到无货而运营以为已上架。
 * <p>
 * 设计约束（见 docs/superpowers/specs/2026-09-21-single-warehouse-listing-design.md）：
 * <ul>
 *   <li>单仓模式 = 启用仓库**恰好一个**（派生判定，不引入配置项、不做数据库迁移）；
 *   <li>多仓或未配仓库时**不替运营决定**仓位，只记日志跳过；
 *   <li><b>任何异常都不得向上抛出</b>——库存行创建失败绝不能阻断商品上架。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryMaintenanceServiceTest {

    private static final Long SKU_ID = 99L;
    private static final Long WAREHOUSE_ID = 1L;

    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private InventoryMapper inventoryMapper;

    private InventoryMaintenanceService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Warehouse.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Inventory.class);
        service = new InventoryMaintenanceService(warehouseMapper, inventoryMapper);
    }

    private Warehouse warehouse(Long id, Integer status) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setName("仓" + id);
        w.setStatus(status);
        return w;
    }

    /** 只让 mapper 返回启用仓库（模拟 SQL 的 status=1 条件）。 */
    private void stubEnabledWarehouses(Warehouse... enabled) {
        when(warehouseMapper.selectList(any())).thenReturn(List.of(enabled));
    }

    private void stubExistingInventory(Inventory... rows) {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(rows));
    }

    // ── 单仓模式判定 ──────────────────────────────────────────

    @Test
    @DisplayName("恰好一个启用仓库 → 单仓模式，返回该仓")
    void exactlyOneEnabledWarehouseIsSingleMode() {
        Warehouse only = warehouse(WAREHOUSE_ID, 1);
        stubEnabledWarehouses(only);

        assertSame(only, service.resolveSingleWarehouse());
    }

    @Test
    @DisplayName("两个启用仓库 → 不是单仓模式（不能替运营决定仓位）")
    void twoEnabledWarehousesIsNotSingleMode() {
        stubEnabledWarehouses(warehouse(1L, 1), warehouse(2L, 1));

        assertNull(service.resolveSingleWarehouse());
    }

    @Test
    @DisplayName("没有启用仓库 → 不是单仓模式")
    void noEnabledWarehouseIsNotSingleMode() {
        stubEnabledWarehouses();

        assertNull(service.resolveSingleWarehouse());
    }

    // ── 自动建库存行 ──────────────────────────────────────────

    @Test
    @DisplayName("单仓 + SKU 无库存行 → 自动为该仓建一行 stock=0")
    void shouldAutoCreateInventoryRowInSingleWarehouseMode() {
        stubEnabledWarehouses(warehouse(WAREHOUSE_ID, 1));
        stubExistingInventory(); // 该 SKU 还没有任何库存行

        service.ensureInventoryForSku(SKU_ID);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryMapper).insert(captor.capture());
        Inventory inserted = captor.getValue();

        assertEquals(SKU_ID, inserted.getSkuId());
        assertEquals(WAREHOUSE_ID, inserted.getWarehouseId(), "必须建在默认仓上");
        assertEquals(0, inserted.getStock(), "新上架商品库存为 0，等运营填数量");
        assertNull(inserted.getWarningStock(), "预警库存交给表默认值，不在这里臆造");
        assertNotNull(inserted.getUpdateTime());
    }

    @Test
    @DisplayName("已有库存行 → 幂等：不重复插入、不改动现有库存")
    void shouldBeIdempotentWhenInventoryRowExists() {
        stubEnabledWarehouses(warehouse(WAREHOUSE_ID, 1));
        Inventory existing = new Inventory();
        existing.setId(5L);
        existing.setSkuId(SKU_ID);
        existing.setWarehouseId(WAREHOUSE_ID);
        existing.setStock(88);
        stubExistingInventory(existing);

        service.ensureInventoryForSku(SKU_ID);

        verify(inventoryMapper, never()).insert(any());
        verify(inventoryMapper, never()).updateById(any());
        assertEquals(88, existing.getStock(), "已有库存不得被清零或改动");
    }

    @Test
    @DisplayName("多仓模式 → 不自动建行（避免放错仓）")
    void shouldNotAutoCreateInMultiWarehouseMode() {
        stubEnabledWarehouses(warehouse(1L, 1), warehouse(2L, 1));
        stubExistingInventory();

        service.ensureInventoryForSku(SKU_ID);

        verify(inventoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("未配置任何启用仓库 → 跳过且不抛异常")
    void shouldSkipAndNotThrowWhenNoWarehouseConfigured() {
        stubEnabledWarehouses();
        stubExistingInventory();

        assertDoesNotThrow(() -> service.ensureInventoryForSku(SKU_ID));
        verify(inventoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("skuId 为空 → 直接跳过")
    void shouldSkipWhenSkuIdMissing() {
        service.ensureInventoryForSku(null);

        verify(inventoryMapper, never()).insert(any());
    }

    // ── 异常绝不外抛（最关键的一条：不能阻断上架） ──────────────

    @Test
    @DisplayName("插入失败（数据库异常）→ 吞掉异常，绝不能阻断商品上架")
    void insertFailureMustNotBlockProductCreation() {
        stubEnabledWarehouses(warehouse(WAREHOUSE_ID, 1));
        stubExistingInventory();
        when(inventoryMapper.insert(any())).thenThrow(new RuntimeException("数据库暂时不可用"));

        assertDoesNotThrow(() -> service.ensureInventoryForSku(SKU_ID),
            "自动建库存行失败不能让上架失败，否则为了修小坑制造了大坑");
    }

    @Test
    @DisplayName("并发重复插入（唯一键冲突）→ 同样吞掉异常")
    void duplicateKeyMustBeSwallowed() {
        stubEnabledWarehouses(warehouse(WAREHOUSE_ID, 1));
        stubExistingInventory();
        when(inventoryMapper.insert(any()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_warehouse_sku"));

        assertDoesNotThrow(() -> service.ensureInventoryForSku(SKU_ID));
    }

    @Test
    @DisplayName("查询仓库失败 → 吞掉异常（同样不能阻断上架）")
    void warehouseQueryFailureMustNotThrow() {
        when(warehouseMapper.selectList(any())).thenThrow(new RuntimeException("连接池耗尽"));

        assertDoesNotThrow(() -> service.ensureInventoryForSku(SKU_ID));
    }
}
