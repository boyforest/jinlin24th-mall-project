package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.CoffeeCartVO;
import com.jinlin24th.jinlin.service.CoffeeCartAppService;
import com.jinlin24th.jinlin.service.CoffeeCatalogSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CoffeeCartAppServiceImpl implements CoffeeCartAppService {
    private final CoffeeCartMapper cartMapper;
    private final CoffeeSkuMapper skuMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeCategoryMapper categoryMapper;
    private final CoffeeCatalogSupport support;

    @Override
    public List<CoffeeCartVO> list(Long userId, Long storeId) {
        requireUser(userId);
        // 目录停用也允许读取和移除本人旧条目。
        CoffeeStore store = support.store(storeId, false);
        return views(store, rows(userId, storeId));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoffeeCartVO add(Long userId, CoffeeCartAddDTO dto) {
        requireUser(userId);
        Long storeId = CoffeeValues.id(dto.getStoreId());
        // 单店小菜单：先锁门店行，再读/写购物车；唯一键兜底，不吞重复键异常。
        CoffeeStore store = support.store(storeId, true);
        CoffeeSku sku = skuMapper.selectById(CoffeeValues.id(dto.getSkuId()));
        if (sku == null || !storeId.equals(sku.getStoreId())) {
            throw BizException.of(BizCode.COFFEE_SKU_NOT_FOUND);
        }
        CoffeeProduct product = productMapper.selectById(sku.getProductId());
        CoffeeCategory category = categoryMapper.selectById(product.getCategoryId());
        if (support.unavailableReason(store, category, product, sku) != null) {
            throw BizException.of(BizCode.COFFEE_SKU_UNAVAILABLE);
        }
        CoffeeCart row =
                cartMapper.selectOne(
                        Wrappers.<CoffeeCart>lambdaQuery()
                                .eq(CoffeeCart::getUserId, userId)
                                .eq(CoffeeCart::getStoreId, storeId)
                                .eq(CoffeeCart::getSkuId, sku.getId()));
        int checked = dto.getChecked() == null ? 1 : dto.getChecked();
        if (row == null) {
            row = new CoffeeCart();
            row.setUserId(userId);
            row.setStoreId(storeId);
            row.setSkuId(sku.getId());
            row.setQuantity(dto.getQuantity());
            row.setChecked(checked);
            row.setVersion(0L);
            cartMapper.insert(row);
        } else {
            int quantity = row.getQuantity() + dto.getQuantity();
            if (quantity > 99) {
                throw CoffeeValues.invalid("同一规格数量不能超过 99");
            }
            change(row, quantity, checked, row.getVersion());
        }
        return views(store, List.of(row)).get(0);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoffeeCartVO update(Long userId, Long id, CoffeeCartUpdateDTO dto) {
        requireUser(userId);
        if (dto.getQuantity() == null && dto.getChecked() == null) {
            throw CoffeeValues.invalid("数量和勾选至少提供一项");
        }
        CoffeeCart initial = owned(userId, id);
        CoffeeStore store = support.store(initial.getStoreId(), true);
        CoffeeCart row = owned(userId, id);
        if (!row.getVersion().equals(dto.getExpectedVersion())) {
            throw BizException.of(BizCode.COFFEE_CART_VERSION_CONFLICT);
        }
        CoffeeCartVO current = views(store, List.of(row)).get(0);
        int quantity = dto.getQuantity() == null ? row.getQuantity() : dto.getQuantity();
        int checked = dto.getChecked() == null ? row.getChecked() : dto.getChecked();
        if (!current.getAvailable()
                && (quantity > row.getQuantity() || Integer.valueOf(1).equals(dto.getChecked()))) {
            throw BizException.of(BizCode.COFFEE_SKU_UNAVAILABLE);
        }
        change(row, quantity, checked, dto.getExpectedVersion());
        return views(store, List.of(row)).get(0);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<CoffeeCartVO> selection(Long userId, CoffeeCartSelectionDTO dto) {
        requireUser(userId);
        CoffeeStore store = support.store(CoffeeValues.id(dto.getStoreId()), true);
        List<CoffeeCart> rows = rows(userId, store.getId());
        Map<String, CoffeeCartVO> current =
                views(store, rows).stream().collect(Collectors.toMap(CoffeeCartVO::getId, v -> v));
        for (CoffeeCart row : rows) {
            if (current.get(row.getId().toString()).getAvailable()
                    && !row.getChecked().equals(dto.getChecked())) {
                change(row, row.getQuantity(), dto.getChecked(), row.getVersion());
            }
        }
        return views(store, rows);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean delete(Long userId, Long id) {
        requireUser(userId);
        CoffeeCart row =
                cartMapper.selectOne(
                        Wrappers.<CoffeeCart>lambdaQuery()
                                .eq(CoffeeCart::getId, id)
                                .eq(CoffeeCart::getUserId, userId));
        if (row != null) {
            support.store(row.getStoreId(), true);
            cartMapper.delete(
                    Wrappers.<CoffeeCart>lambdaQuery()
                            .eq(CoffeeCart::getId, id)
                            .eq(CoffeeCart::getUserId, userId));
        }
        return true;
    }

    private void change(CoffeeCart row, int quantity, int checked, long version) {
        if (cartMapper.updateVersioned(row.getId(), row.getUserId(), quantity, checked, version)
                != 1) {
            throw BizException.of(BizCode.COFFEE_CART_VERSION_CONFLICT);
        }
        row.setQuantity(quantity);
        row.setChecked(checked);
        row.setVersion(version + 1);
    }

    private CoffeeCart owned(Long userId, Long id) {
        CoffeeCart row =
                cartMapper.selectOne(
                        Wrappers.<CoffeeCart>lambdaQuery()
                                .eq(CoffeeCart::getUserId, userId)
                                .eq(CoffeeCart::getId, id));
        if (row == null) {
            throw BizException.of(BizCode.COFFEE_CART_NOT_FOUND);
        }
        return row;
    }

    private List<CoffeeCart> rows(Long userId, Long storeId) {
        return cartMapper.selectList(
                Wrappers.<CoffeeCart>lambdaQuery()
                        .eq(CoffeeCart::getUserId, userId)
                        .eq(CoffeeCart::getStoreId, storeId)
                        .orderByAsc(CoffeeCart::getId));
    }

    private List<CoffeeCartVO> views(CoffeeStore store, List<CoffeeCart> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, CoffeeSku> skus =
                index(
                        skuMapper.selectBatchIds(
                                rows.stream().map(CoffeeCart::getSkuId).distinct().toList()),
                        CoffeeSku::getId);
        Map<Long, CoffeeProduct> products =
                index(
                        productMapper.selectBatchIds(
                                skus.values().stream()
                                        .map(CoffeeSku::getProductId)
                                        .distinct()
                                        .toList()),
                        CoffeeProduct::getId);
        Map<Long, CoffeeCategory> categories =
                index(
                        categoryMapper.selectBatchIds(
                                products.values().stream()
                                        .map(CoffeeProduct::getCategoryId)
                                        .distinct()
                                        .toList()),
                        CoffeeCategory::getId);
        return rows.stream()
                .map(
                        row -> {
                            CoffeeSku sku = skus.get(row.getSkuId());
                            CoffeeProduct product = products.get(sku.getProductId());
                            CoffeeCartVO vo = new CoffeeCartVO();
                            vo.setId(row.getId().toString());
                            vo.setStoreId(store.getId().toString());
                            vo.setSkuId(sku.getId().toString());
                            vo.setProductId(product.getId().toString());
                            vo.setProductName(product.getName());
                            vo.setSkuName(sku.getName());
                            vo.setImageUrl(product.getImageUrl());
                            vo.setTemperature(sku.getTemperature());
                            vo.setSugar(sku.getSugar());
                            vo.setUnitPriceCent(sku.getPriceCent());
                            vo.setQuantity(row.getQuantity());
                            vo.setChecked(row.getChecked());
                            vo.setVersion(row.getVersion());
                            vo.setUnavailableReason(
                                    support.unavailableReason(
                                            store,
                                            categories.get(product.getCategoryId()),
                                            product,
                                            sku));
                            vo.setAvailable(vo.getUnavailableReason() == null);
                            return vo;
                        })
                .toList();
    }

    private <T> Map<Long, T> index(List<T> values, Function<T, Long> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw BizException.of(BizCode.UNAUTHORIZED);
        }
    }
}
