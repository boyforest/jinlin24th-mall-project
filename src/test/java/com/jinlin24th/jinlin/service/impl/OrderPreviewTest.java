package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPreviewTest {
    private final OrderServiceImpl service = new OrderServiceImpl();
    private final ProductSkuService skus = mock(ProductSkuService.class);
    private final ProductService products = mock(ProductService.class);
    private final AppUserService users = mock(AppUserService.class);
    private final SkuStockQueryService stocks = mock(SkuStockQueryService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final OrderMasterService orders = mock(OrderMasterService.class);
    private Product product;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "productSkuService", skus);
        ReflectionTestUtils.setField(service, "productService", products);
        ReflectionTestUtils.setField(service, "appUserService", users);
        ReflectionTestUtils.setField(service, "skuStockQueryService", stocks);
        ReflectionTestUtils.setField(service, "effectivePriceService", new EffectivePriceService());
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "orderMasterService", orders);
        ProductSku sku = new ProductSku();
        sku.setId(2L); sku.setProductId(3L); sku.setStatus(1); sku.setSkuName("六袋装");
        sku.setPrice(new BigDecimal("69.90")); sku.setMemberPrice(new BigDecimal("59.90"));
        when(skus.getById(2L)).thenReturn(sku);
        product = new Product(); product.setId(3L); product.setStatus(1); product.setName("秋养茶"); product.setMainImage("tea.png");
        when(products.getById(3L)).thenReturn(product);
        when(stocks.stockBySkuIds(any())).thenReturn(Map.of(2L, 1));
    }
    private OrderCreateDTO dto(int quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        OrderCreateDTO.Item item = new OrderCreateDTO.Item(); item.setSkuId(2L); item.setQuantity(quantity);
        dto.setItems(List.of(item)); return dto;
    }
    @Test void regularPriceAndStockShortageAreVisibleWithoutCreatingOrReserving() {
        var quote = service.preview(1L, dto(2));
        assertEquals(new BigDecimal("139.80"), quote.getPayAmount());
        assertEquals(quote.getPayAmount(), quote.getTotalAmount());
        assertEquals(BigDecimal.ZERO, quote.getFreightAmount());
        assertEquals(1, quote.getItems().get(0).getStock());
        assertEquals("秋养茶", quote.getItems().get(0).getProductName());
        assertEquals("六袋装", quote.getItems().get(0).getSkuName());
        assertEquals("tea.png", quote.getItems().get(0).getProductImage());
        verifyNoInteractions(inventory, orders);
    }
    @Test void activeDistributorGetsTheSameMembershipRuleAsOrderCreation() {
        AppUser user = new AppUser(); user.setIsDistributor(1); user.setStatus(1);
        when(users.getById(1L)).thenReturn(user);
        assertEquals(new BigDecimal("119.80"), service.preview(1L, dto(2)).getPayAmount());
        user.setStatus(0);
        assertEquals(new BigDecimal("139.80"), service.preview(1L, dto(2)).getPayAmount());
    }
    @Test void unavailableAndInvalidItemsCannotBeQuoted() {
        assertThrows(BizException.class, () -> service.preview(1L, dto(0)));
        product.setStatus(0);
        assertThrows(BizException.class, () -> service.preview(1L, dto(1)));
        verifyNoInteractions(inventory, orders);
    }
    @Test void duplicateSkusAreRejectedInsteadOfUnderstatingRequiredStock() {
        var dto = dto(1); dto.setItems(List.of(dto.getItems().get(0), dto.getItems().get(0)));
        assertThrows(BizException.class, () -> service.preview(1L, dto));
    }
}
