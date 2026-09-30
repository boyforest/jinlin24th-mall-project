package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.mapper.InventoryMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 可售库存聚合的正确性测试。
 * <p>
 * 背景：库存分散在 {@code inventory} 表（按仓库 + SKU），而 C 端此前展示的是
 * {@code product_sku.stock}——那个字段从不随销售变化。展示必须改为实时汇总
 * {@code inventory}，本测试锁定汇总语义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkuStockQueryServiceTest {

    @Mock
    private InventoryMapper inventoryMapper;

    private SkuStockQueryService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Inventory.class);
        service = new SkuStockQueryService(inventoryMapper);
    }

    private Inventory row(Long id, Long skuId, Long warehouseId, Integer stock) {
        Inventory inv = new Inventory();
        inv.setId(id);
        inv.setSkuId(skuId);
        inv.setWarehouseId(warehouseId);
        inv.setStock(stock);
        return inv;
    }

    @Test
    @DisplayName("同一 SKU 多个仓库的库存必须相加（用户看到的是总可售量）")
    void shouldSumStockAcrossWarehouses() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(
            row(1L, 99L, 1L, 30),
            row(2L, 99L, 2L, 12),
            row(3L, 555L, 1L, 8)));

        Map<Long, Integer> stock = service.stockBySkuIds(List.of(99L, 555L));

        assertEquals(42, stock.get(99L), "同 SKU 各仓库存应相加");
        assertEquals(8, stock.get(555L));
    }

    @Test
    @DisplayName("没有库存记录的 SKU 不出现在结果中（调用方按 0 处理）")
    void shouldOmitSkuWithoutInventoryRecord() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(row(1L, 99L, 1L, 5)));

        Map<Long, Integer> stock = service.stockBySkuIds(List.of(99L, 777L));

        assertEquals(5, stock.get(99L));
        assertTrue(!stock.containsKey(777L), "无记录的 SKU 不应被编造成某个数字");
    }

    @Test
    @DisplayName("stock 为 null 时按 0 处理，不能抛 NPE")
    void shouldTreatNullStockAsZero() {
        when(inventoryMapper.selectList(any())).thenReturn(List.of(
            row(1L, 99L, 1L, null),
            row(2L, 99L, 2L, 7)));

        Map<Long, Integer> stock = service.stockBySkuIds(List.of(99L));

        assertEquals(7, stock.get(99L));
    }

    @Test
    @DisplayName("空入参或 null 入参必须短路返回空 Map，不查库")
    void shouldShortCircuitOnEmptyInput() {
        assertEquals(Map.of(), service.stockBySkuIds(List.of()));
        assertEquals(Map.of(), service.stockBySkuIds(null));

        verify(inventoryMapper, never()).selectList(any());
    }
}
