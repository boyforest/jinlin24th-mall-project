package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.CoffeeStoreMapper;
import com.jinlin24th.jinlin.pojo.entity.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CoffeeCatalogSupport {
    private final CoffeeStoreMapper storeMapper;

    @Value("${app.coffee.catalog-enabled:false}")
    private boolean enabled;

    public CoffeeStore store(Long id, boolean lock) {
        CoffeeStore store = lock ? storeMapper.lockById(id) : storeMapper.selectById(id);
        if (store == null) {
            throw BizException.of(BizCode.COFFEE_STORE_NOT_FOUND);
        }
        return store;
    }

    public boolean browsable(CoffeeStore store) {
        return enabled && Integer.valueOf(1).equals(store.getStatus());
    }

    public void requireBrowsable(CoffeeStore store) {
        if (!browsable(store)) {
            throw BizException.of(BizCode.COFFEE_STORE_NOT_FOUND);
        }
    }

    public String unavailableReason(
            CoffeeStore store, CoffeeCategory category, CoffeeProduct product, CoffeeSku sku) {
        if (!browsable(store)) {
            return "STORE_DISABLED";
        }
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            return "CATEGORY_DISABLED";
        }
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            return "PRODUCT_OFF_SHELF";
        }
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            return "SKU_DISABLED";
        }
        if (!Boolean.TRUE.equals(sku.getAvailable())) {
            return "SOLD_OUT";
        }
        return null;
    }
}
