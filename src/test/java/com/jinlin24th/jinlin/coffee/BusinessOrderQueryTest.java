package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.pojo.dto.BusinessOrderQueryDTO;
import com.jinlin24th.jinlin.pojo.vo.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class BusinessOrderQueryTest extends CoffeeDatabaseTestSupport {
    @BeforeEach
    void seedRealGoodsOrders() {
        int[] statuses = {0, 10, 20, 30, 40, 50, 60};
        for (int i = 0; i < statuses.length; i++) {
            insertOrder(i + 1, 10, statuses[i]);
        }
        insertOrder(8, 20, 0);
        for (int i = 1; i <= 4; i++) {
            jdbc.update(
                    "INSERT INTO order_item"
                        + " (order_id,order_no,product_id,sku_id,product_name,sku_name,price,quantity,total_price)"
                        + " VALUES(1,'ORDER1',?,?, '历史饮品名称','历史规格',1.23,?,?)",
                    100L + i,
                    200L + i,
                    i,
                    new java.math.BigDecimal("1.23").multiply(java.math.BigDecimal.valueOf(i)));
        }
        jdbc.update("UPDATE order_master SET pay_time='2026-10-24 10:00:00' WHERE id=2");
        jdbc.update(
                "INSERT INTO"
                    + " payment_record(payment_no,order_no,user_id,total_amount,pay_amount,status,refund_amount)"
                    + " VALUES('REFUNDED','ORDER7',10,10.10,10.20,3,5.00),('UNPAID','ORDER1',10,10.10,10.20,0,NULL)");
    }

    private void insertOrder(long id, long user, int status) {
        jdbc.update(
                "INSERT INTO"
                    + " order_master(id,order_no,user_id,total_amount,pay_amount,discount_amount,freight_amount,status,receiver_name,receiver_phone,receiver_address,create_time)"
                    + " VALUES(?,?,?,10.10,10.20,0.10,0.20,?,'仅测试','仅测试','仅测试','2026-10-24"
                    + " 09:00:00')",
                id,
                "ORDER" + id,
                user,
                status);
    }

    private BusinessOrderQueryDTO query() {
        return new BusinessOrderQueryDTO();
    }

    @Test
    void stablePagingTotalsAndUserIsolationUseRealSql() {
        BusinessOrderQueryDTO q = query();
        q.setSize(3);
        BusinessOrderPageVO first = orders.list(10L, q);
        assertEquals(7, first.total());
        assertEquals(3, first.pages());
        assertEquals(1, first.current());
        assertEquals(
                List.of("7", "6", "5"),
                first.records().stream().map(BusinessOrderSummaryVO::getId).toList());
        q.setPage(2);
        assertEquals(
                List.of("4", "3", "2"),
                orders.list(10L, q).records().stream().map(BusinessOrderSummaryVO::getId).toList());
        q.setPage(99);
        assertEquals(7, orders.list(10L, q).total());
        assertTrue(orders.list(10L, q).records().isEmpty());
        assertEquals(1, orders.list(20L, query()).total());
    }

    @Test
    void summariesUseSnapshotsFullCountAndExactCentsWithNullablePaidAmount() {
        BusinessOrderQueryDTO q = query();
        q.setStatus("PENDING_PAY");
        CoffeeQueryCounter.reset();
        BusinessOrderSummaryVO vo = orders.list(10L, q).records().get(0);
        assertEquals(4, CoffeeQueryCounter.count(), "计数、订单页、明细批量、支付批量各一次");
        assertEquals(10, vo.getItemCount());
        assertEquals(3, vo.getSummaryItems().size());
        assertEquals("历史饮品名称", vo.getSummaryItems().get(0).getProductName());
        assertEquals(123, vo.getSummaryItems().get(0).getUnitPriceCent());
        assertEquals(369, vo.getSummaryItems().get(2).getLineTotalCent());
        assertNull(vo.getSummaryItems().get(0).getImageUrl());
        assertEquals(1010, vo.getAmounts().getTotalAmountCent());
        assertEquals(10, vo.getAmounts().getDiscountAmountCent());
        assertEquals(20, vo.getAmounts().getFreightAmountCent());
        assertEquals(1020, vo.getAmounts().getPayableAmountCent());
        assertNull(vo.getAmounts().getPaidAmountCent());
        assertEquals("+08:00", vo.getCreatedAt().getOffset().toString());
        assertNull(vo.getPickupCode());
        assertNull(vo.getPickupDate());
        assertNull(vo.getStoreId());
        assertEquals("DELIVERY", vo.getFulfillmentType());
        CoffeeQueryCounter.reset();
        orders.list(10L, query());
        assertEquals(4, CoffeeQueryCounter.count(), "订单数量增加不得增加 SQL 次数");
    }

    @Test
    void paymentEvidenceAndRefundPreserveOriginalPayment() {
        BusinessOrderQueryDTO q = query();
        q.setStatus("PENDING_SHIP");
        assertEquals(1020, orders.list(10L, q).records().get(0).getAmounts().getPaidAmountCent());
        q.setStatus("REFUNDED");
        assertEquals(1020, orders.list(10L, q).records().get(0).getAmounts().getPaidAmountCent());
        jdbc.update("UPDATE payment_record SET pay_amount=9.99 WHERE order_no='ORDER7'");
        assertEquals(999, orders.list(10L, q).records().get(0).getAmounts().getPaidAmountCent());
        jdbc.update("UPDATE order_master SET discount_amount=NULL,freight_amount=NULL WHERE id=7");
        BusinessOrderSummaryVO.Amounts amounts = orders.list(10L, q).records().get(0).getAmounts();
        assertEquals(0, amounts.getDiscountAmountCent());
        assertEquals(0, amounts.getFreightAmountCent());
    }

    @Test
    void groupsAndCoffeeEmptyPagesMatchTheContract() {
        BusinessOrderQueryDTO q = query();
        q.setStatusGroup("IN_PROGRESS");
        assertEquals(3, orders.list(10L, q).total());
        q.setStatusGroup("AFTER_SALES");
        assertEquals(2, orders.list(10L, q).total());
        q.setStatusGroup("CLOSED");
        assertEquals("CANCELLED", orders.list(10L, q).records().get(0).getStatus());
        q.setBusinessType("COFFEE");
        q.setStatusGroup("ALL");
        q.setStatus("READY");
        BusinessOrderPageVO coffee = orders.list(10L, q);
        assertEquals(0, coffee.total());
        assertEquals(0, coffee.pages());
        assertEquals(List.of(), coffee.records());
        q.setBusinessType(null);
        assertEquals(0, orders.list(10L, q).total());
        q.setBusinessType("GOODS");
        error(BizCode.PARAM_INVALID, () -> orders.list(10L, q));
        q.setBusinessType("COFFEE");
        q.setStatus("PENDING_SHIP");
        error(BizCode.PARAM_INVALID, () -> orders.list(10L, q));
        q.setStatus("PENDING_PAY");
        q.setStatusGroup("CLOSED");
        error(BizCode.PARAM_INVALID, () -> orders.list(10L, q));
        q.setStatus(null);
        q.setStatusGroup("ALL");
        q.setPage(0);
        error(BizCode.PARAM_INVALID, () -> orders.list(10L, q));
        q.setPage(1);
        q.setSize(101);
        error(BizCode.PARAM_INVALID, () -> orders.list(10L, q));
    }

    @Test
    void longIdsAreStringsWithoutPrecisionLoss() {
        insertOrder(9007199254740993L, 10, 0);
        BusinessOrderQueryDTO q = query();
        q.setStatus("PENDING_PAY");
        assertEquals("9007199254740993", orders.list(10L, q).records().get(0).getId());
    }
}
