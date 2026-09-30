package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.pojo.entity.Warehouse;
import com.jinlin24th.jinlin.pojo.vo.InventoryModeVO;
import com.jinlin24th.jinlin.service.InventoryMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 单仓模式接口测试。
 * <p>
 * 前端靠这个接口决定「库存」页是否隐藏仓库筛选/列/表单字段。
 * 关键约定：**多仓时前端必须回退到显式选择仓库**，绝不能由系统替运营猜仓位。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminInventoryModeEndpointTest {

    @Mock
    private InventoryMaintenanceService inventoryMaintenanceService;

    private AdminInventoryController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminInventoryController();
        ReflectionTestUtils.setField(controller, "inventoryMaintenanceService", inventoryMaintenanceService);
    }

    @Test
    @DisplayName("单仓模式：返回 singleWarehouse=true 与默认仓 id/名称")
    void shouldReportSingleWarehouseMode() {
        Warehouse only = new Warehouse();
        only.setId(1L);
        only.setName("主仓库");
        when(inventoryMaintenanceService.resolveSingleWarehouse()).thenReturn(only);

        InventoryModeVO data = controller.mode().getData();

        assertTrue(data.getSingleWarehouse());
        assertEquals(1L, data.getDefaultWarehouseId());
        assertEquals("主仓库", data.getDefaultWarehouseName());
    }

    @Test
    @DisplayName("多仓/未配置仓库：返回 singleWarehouse=false 且不带默认仓")
    void shouldReportMultiWarehouseMode() {
        when(inventoryMaintenanceService.resolveSingleWarehouse()).thenReturn(null);

        InventoryModeVO data = controller.mode().getData();

        assertFalse(data.getSingleWarehouse(), "多仓时前端必须回退到显式选择仓库");
        assertNull(data.getDefaultWarehouseId());
        assertNull(data.getDefaultWarehouseName());
    }
}
