package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 会员价规则的**唯一事实来源**。
 * <p>
 * 业务规则：只有**分销会员**（{@code app_user.is_distributor=1} 且账号启用）享受
 * SKU 的「会员价」；其余人一律按「售价」。分销资格由超管在后台手动开通。
 * <p>
 * <b>为什么必须收敛到一处</b>：此前取价逻辑散落在「后端 1 处 + 小程序 6 处」
 * （`memberPrice || price`），而后端是**无条件**取会员价 —— 等于给所有用户打折，
 * 「会员价」名不副实。规则一旦改为"因人而异"，任何一处没对齐就会出现
 * 「显示 A 价、结算 B 价」，这是客诉和资损的直接来源。
 * <p>
 * <b>定价只允许发生在服务端</b>：前端不得自行判断价格，只显示后端下发的
 * {@code effectivePrice}；下单金额也由服务端按 skuId 查库重算，不信任客户端传值。
 * <p>
 * 容错原则（宁少收不许多收，但配置错误要尽早暴露）：
 * <ul>
 *   <li>会员价为空 / 为 0 / 为负 / **高于售价** → 一律回退售价，绝不让会员买得更贵；</li>
 *   <li>售价缺失 → 抛异常（售价是必填项，算出 null 金额比报错危险得多）。</li>
 * </ul>
 */
@Component
public class EffectivePriceService {

    /**
     * 判定是否享受会员价。
     *
     * @param user 当前登录用户，可为 null（匿名/未登录）
     */
    public boolean isDistributorMember(AppUser user) {
        return user != null
            && Integer.valueOf(1).equals(user.getIsDistributor())
            && Integer.valueOf(1).equals(user.getStatus());
    }

    /**
     * 计算某 SKU 对该用户的实际成交单价。
     *
     * @param sku             商品规格
     * @param distributorMember 该用户是否为分销会员
     * @return 实际单价（永不为 null）
     */
    public BigDecimal effectivePrice(ProductSku sku, boolean distributorMember) {
        if (sku == null) {
            throw new IllegalStateException("计算价格失败：SKU 为空");
        }
        BigDecimal listPrice = sku.getPrice();
        if (listPrice == null) {
            throw new IllegalStateException("计算价格失败：SKU " + sku.getId() + " 缺少售价（数据错误）");
        }

        if (!distributorMember) {
            return listPrice;
        }
        BigDecimal memberPrice = sku.getMemberPrice();
        // 会员价必须"真的更低"才采用：为空/0/负数/高于售价都属于配置错误，
        // 静默回退售价，避免出现"会员买得更贵"或"白送"。
        if (memberPrice == null || memberPrice.compareTo(BigDecimal.ZERO) <= 0
                || memberPrice.compareTo(listPrice) >= 0) {
            return listPrice;
        }
        return memberPrice;
    }

    /**
     * 该 SKU 是否存在对会员有效的优惠（供前端决定是否展示划线价与"分销会员 ¥X"提示）。
     */
    public boolean hasMemberDiscount(ProductSku sku) {
        if (sku == null || sku.getPrice() == null || sku.getMemberPrice() == null) {
            return false;
        }
        return sku.getMemberPrice().compareTo(BigDecimal.ZERO) > 0
            && sku.getMemberPrice().compareTo(sku.getPrice()) < 0;
    }
}
