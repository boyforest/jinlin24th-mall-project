package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.OrderItem;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.EffectivePriceService;
import com.jinlin24th.jinlin.service.InventoryLogService;
import com.jinlin24th.jinlin.service.InventoryService;
import com.jinlin24th.jinlin.service.OrderItemService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 下单定价的权威性测试。
 * <p>
 * 修复的缺陷：下单时**无条件**取 SKU 的 memberPrice（`memberPrice != null ? memberPrice : price`），
 * 等于给所有用户（含未登录/非分销会员）自动打折，「会员价」名不副实。
 * <p>
 * 正确规则：只有分销会员享受会员价，其余人按售价。且金额必须由服务端按 skuId 查库算出，
 * 不信任任何客户端传值。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPricingTest {

    private static final Long USER_ID = 1L;
    private static final Long SKU_ID = 99L;
    private static final String LIST_PRICE = "69.90";
    private static final String MEMBER_PRICE = "59.90";

    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private InventoryLogService inventoryLogService;
    @Mock
    private AppUserService appUserService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    /** 默认返回 null → 等价于 MQ/短信未开启（与生产默认一致）。 */
    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.jinlin24th.jinlin.common.mq.producer.OrderTimeoutMessageProducer> orderTimeoutMessageProducerProvider;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.jinlin24th.jinlin.common.mq.producer.SmsMessageProducer> smsMessageProducerProvider;
    @Mock
    private LambdaQueryChainWrapper<Inventory> inventoryQuery;
    @Mock
    private LambdaQueryChainWrapper<AppUser> appUserQuery;
    @Mock
    private com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper<Inventory> inventoryUpdate;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), Inventory.class);
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppUser.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMasterService", orderMasterService);
        ReflectionTestUtils.setField(service, "orderItemService", orderItemService);
        ReflectionTestUtils.setField(service, "productSkuService", productSkuService);
        ReflectionTestUtils.setField(service, "productService", productService);
        ReflectionTestUtils.setField(service, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(service, "inventoryLogService", inventoryLogService);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "applicationEventPublisher", applicationEventPublisher);
        ReflectionTestUtils.setField(service, "effectivePriceService", new EffectivePriceService());
        ReflectionTestUtils.setField(service, "orderTimeoutMessageProducerProvider", orderTimeoutMessageProducerProvider);
        ReflectionTestUtils.setField(service, "smsMessageProducerProvider", smsMessageProducerProvider);

        // SKU：售价 69.90 / 会员价 59.90
        ProductSku sku = new ProductSku();
        sku.setId(SKU_ID);
        sku.setProductId(7L);
        sku.setSkuName("规格A");
        sku.setPrice(new BigDecimal(LIST_PRICE));
        sku.setMemberPrice(new BigDecimal(MEMBER_PRICE));
        sku.setStatus(1);
        when(productSkuService.getById(SKU_ID)).thenReturn(sku);

        Product product = new Product();
        product.setId(7L);
        product.setName("商品A");
        product.setStatus(1);
        when(productService.getById(7L)).thenReturn(product);

        // 库存充足：占用 2 件
        Inventory inventory = new Inventory();
        inventory.setId(5L);
        inventory.setWarehouseId(1L);
        inventory.setSkuId(SKU_ID);
        inventory.setStock(100);
        when(inventoryService.lambdaQuery()).thenReturn(inventoryQuery);
        when(inventoryQuery.eq(any(), any())).thenReturn(inventoryQuery);
        when(inventoryQuery.ge(any(), any())).thenReturn(inventoryQuery);
        when(inventoryQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
            .thenReturn(inventoryQuery);
        when(inventoryQuery.last(anyString())).thenReturn(inventoryQuery);
        when(inventoryQuery.one()).thenReturn(inventory);

        // 占用库存：CAS 更新成功
        when(inventoryService.lambdaUpdate()).thenReturn(inventoryUpdate);
        when(inventoryUpdate.set(any(), any())).thenReturn(inventoryUpdate);
        when(inventoryUpdate.eq(any(), any())).thenReturn(inventoryUpdate);
        when(inventoryUpdate.update()).thenReturn(true);

        when(orderMasterService.save(any())).thenReturn(true);
        when(orderItemService.saveBatch(any())).thenReturn(true);

        // 推荐官查询（本测试不关心分销体系，统一返回空）
        when(appUserService.lambdaQuery()).thenReturn(appUserQuery);
        when(appUserQuery.eq(any(), any())).thenReturn(appUserQuery);
        when(appUserQuery.one()).thenReturn(null);
    }

    private void stubBuyer(Integer isDistributor) {
        AppUser buyer = new AppUser();
        buyer.setId(USER_ID);
        buyer.setIsDistributor(isDistributor);
        buyer.setStatus(1);
        when(appUserService.getById(USER_ID)).thenReturn(buyer);
    }

    private OrderCreateDTO dto(int quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setReceiverAddress("测试地址");
        OrderCreateDTO.Item item = new OrderCreateDTO.Item();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        return dto;
    }

    /** 捕获保存的订单主表，读取权威金额。 */
    private OrderMaster capturedMaster() {
        ArgumentCaptor<OrderMaster> captor = ArgumentCaptor.forClass(OrderMaster.class);
        org.mockito.Mockito.verify(orderMasterService).save(captor.capture());
        return captor.getValue();
    }

    /** 捕获订单明细，读取价格快照。 */
    @SuppressWarnings("unchecked")
    private List<OrderItem> capturedItems() {
        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(orderItemService).saveBatch(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("分销会员下单：按会员价计费")
    void distributorMemberShouldPayMemberPrice() {
        stubBuyer(1);

        OrderVO vo = service.create(USER_ID, dto(2));

        OrderMaster master = capturedMaster();
        // 59.90 × 2 = 119.80
        assertEquals(0, new BigDecimal("119.80").compareTo(master.getTotalAmount()),
            "分销会员应按会员价合计");
        assertEquals(0, new BigDecimal("119.80").compareTo(master.getPayAmount()));
        assertEquals(0, new BigDecimal(MEMBER_PRICE).compareTo(capturedItems().get(0).getPrice()),
            "订单明细的价格快照应为会员价");
        org.junit.jupiter.api.Assertions.assertNotNull(vo);
    }

    @Test
    @DisplayName("非分销会员下单：必须按售价计费（这是本次修复的核心）")
    void nonMemberShouldPayListPrice() {
        stubBuyer(0);

        service.create(USER_ID, dto(2));

        OrderMaster master = capturedMaster();
        // 69.90 × 2 = 139.80
        assertEquals(0, new BigDecimal("139.80").compareTo(master.getPayAmount()),
            "非分销会员不得享受会员价");
        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(capturedItems().get(0).getPrice()));
    }

    @Test
    @DisplayName("买家信息缺失（异常数据）时按非会员处理，不能误给会员价")
    void missingBuyerShouldFallBackToListPrice() {
        when(appUserService.getById(USER_ID)).thenReturn(null);

        service.create(USER_ID, dto(1));

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(capturedMaster().getPayAmount()),
            "拿不到买家身份时应保守按原价，宁可少给权益也不能误打折");
    }

    @Test
    @DisplayName("被禁用的分销会员不得享受会员价")
    void disabledDistributorShouldNotGetMemberPrice() {
        AppUser disabled = new AppUser();
        disabled.setId(USER_ID);
        disabled.setIsDistributor(1);
        disabled.setStatus(0); // 已禁用
        when(appUserService.getById(USER_ID)).thenReturn(disabled);

        service.create(USER_ID, dto(1));

        assertEquals(0, new BigDecimal(LIST_PRICE).compareTo(capturedMaster().getPayAmount()));
    }

    @Test
    @DisplayName("实付金额始终等于合计金额（本阶段无券无积分，不允许出现差额）")
    void payAmountMustEqualTotalAmount() {
        stubBuyer(1);

        service.create(USER_ID, dto(3));

        OrderMaster master = capturedMaster();
        assertEquals(0, master.getTotalAmount().compareTo(master.getPayAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(master.getDiscountAmount()),
            "当前阶段不使用优惠券，折扣额必须为 0");
    }
}
