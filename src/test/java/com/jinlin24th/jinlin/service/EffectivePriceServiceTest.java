package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员价规则测试（定价的唯一事实来源）。
 * <p>
 * 业务规则：**只有分销会员**享受 SKU 的「会员价」；其余人按「售价」。
 * 分销资格由超管在后台手动开通（{@code app_user.is_distributor}）。
 * <p>
 * 为什么必须收敛到一个服务：此前取价逻辑散落在「后端 1 处 + 小程序 6 处」
 * （`memberPrice || price`），且后端是**无条件**取会员价 —— 等于给所有人打折。
 * 规则改为"因人而异"后，任何一处没对齐都会造成
 * "显示 A 价、结算 B 价"，而这是客诉与资损的直接来源。
 */
class EffectivePriceServiceTest {

    private final EffectivePriceService service = new EffectivePriceService();

    private ProductSku sku(String price, String memberPrice) {
        ProductSku sku = new ProductSku();
        sku.setId(1L);
        sku.setPrice(price == null ? null : new BigDecimal(price));
        sku.setMemberPrice(memberPrice == null ? null : new BigDecimal(memberPrice));
        return sku;
    }

    private AppUser user(Integer isDistributor, Integer status) {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setIsDistributor(isDistributor);
        u.setStatus(status);
        return u;
    }

    // ── 身份判定 ──────────────────────────────────────────────

    @Test
    @DisplayName("只有 is_distributor=1 且账号启用(status=1)才算分销会员")
    void shouldIdentifyDistributorMember() {
        assertTrue(service.isDistributorMember(user(1, 1)));
        assertFalse(service.isDistributorMember(user(0, 1)), "非分销不能享受");
        assertFalse(service.isDistributorMember(user(1, 0)), "被禁用的账号不该享受会员权益");
        assertFalse(service.isDistributorMember(user(null, 1)));
        assertFalse(service.isDistributorMember(null), "匿名/未登录一律不是会员");
    }

    // ── 取价 ────────────────────────────────────────────────

    @Test
    @DisplayName("分销会员取会员价")
    void memberShouldGetMemberPrice() {
        assertEquals(new BigDecimal("59.90"),
            service.effectivePrice(sku("69.90", "59.90"), true));
    }

    @Test
    @DisplayName("非分销会员取售价（这是本次修复的核心：此前所有人都拿会员价）")
    void nonMemberShouldGetListPrice() {
        assertEquals(new BigDecimal("69.90"),
            service.effectivePrice(sku("69.90", "59.90"), false));
    }

    @Test
    @DisplayName("未设会员价时，会员与非会员都按售价")
    void shouldFallBackToListPriceWhenMemberPriceMissing() {
        assertEquals(new BigDecimal("69.90"), service.effectivePrice(sku("69.90", null), true));
        assertEquals(new BigDecimal("69.90"), service.effectivePrice(sku("69.90", null), false));
    }

    @Test
    @DisplayName("会员价等于售价时不做特殊处理（避免出现假的'已优惠'）")
    void shouldTreatEqualPricesAsNoDiscount() {
        ProductSku same = sku("69.90", "69.90");
        assertEquals(new BigDecimal("69.90"), service.effectivePrice(same, true));
        assertFalse(service.hasMemberDiscount(same),
            "会员价与售价相同不应被视为有优惠，否则前端会显示无意义的划线价");
    }

    @Test
    @DisplayName("会员价高于售价属于配置错误，必须按较低者收（不能让会员买得更贵）")
    void shouldNeverChargeMoreThanListPrice() {
        assertEquals(new BigDecimal("69.90"),
            service.effectivePrice(sku("69.90", "99.90"), true),
            "会员价配错高于售价时，按售价收，绝不向会员多收钱");
    }

    @Test
    @DisplayName("会员价为 0 或负数属于配置错误，必须忽略并回退售价")
    void shouldIgnoreNonPositiveMemberPrice() {
        assertEquals(new BigDecimal("69.90"), service.effectivePrice(sku("69.90", "0"), true));
        assertEquals(new BigDecimal("69.90"), service.effectivePrice(sku("69.90", "-1"), true));
    }

    @Test
    @DisplayName("售价缺失时不得返回 null 造成 NPE，必须报错")
    void shouldFailFastWhenListPriceMissing() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
            () -> service.effectivePrice(sku(null, "59.90"), false),
            "售价是必填项，缺失属于数据错误，应尽早暴露而不是算出 null 金额");
    }

    @Test
    @DisplayName("hasMemberDiscount 仅对'真的更便宜'返回 true（供前端决定是否展示划线价）")
    void hasMemberDiscountShouldReflectRealDiscount() {
        assertTrue(service.hasMemberDiscount(sku("69.90", "59.90")));
        assertFalse(service.hasMemberDiscount(sku("69.90", null)));
        assertFalse(service.hasMemberDiscount(sku("69.90", "69.90")));
        assertFalse(service.hasMemberDiscount(null));
    }
}
