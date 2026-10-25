package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlin24th.jinlin.common.auth.CoffeeAdminAccessService;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.CoffeeCatalogService;
import com.jinlin24th.jinlin.service.CoffeeCatalogSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CoffeeCatalogServiceImpl implements CoffeeCatalogService {
    @org.springframework.beans.factory.annotation.Value("${app.coffee.ordering-enabled:false}")
    private boolean orderingEnabled;
    @org.springframework.beans.factory.annotation.Value("${app.coffee.payment-enabled:false}")
    private boolean paymentEnabled;
    private final CoffeeStoreMapper storeMapper;
    private final CoffeeCategoryMapper categoryMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeSkuMapper skuMapper;
    private final CoffeeCatalogSupport support;
    private final CoffeeAdminAccessService access;

    @Override
    public CoffeeStoreVO currentStore() {
        CoffeeStore store =
                storeMapper.selectOne(
                        Wrappers.<CoffeeStore>lambdaQuery().eq(CoffeeStore::getPrimarySlot, 1));
        if (store == null) {
            throw BizException.of(BizCode.COFFEE_STORE_NOT_FOUND);
        }
        support.requireBrowsable(store);
        return storeVO(store);
    }

    @Override
    public CoffeeMenuVO menu(Long storeId) {
        CoffeeStore store = support.store(storeId, false);
        support.requireBrowsable(store);
        List<CoffeeCategory> categories = categories(storeId);
        List<CoffeeProduct> products = products(storeId);
        List<CoffeeSku> skus = skus(storeId);
        Set<Long> enabledCategories =
                categories.stream()
                        .filter(c -> c.getStatus() == 1)
                        .map(CoffeeCategory::getId)
                        .collect(Collectors.toSet());
        CoffeeMenuVO result = new CoffeeMenuVO();
        result.setStoreId(storeId.toString());
        result.setVersion(version(store, categories, products, skus));
        result.setCategories(
                categories.stream()
                        .filter(c -> enabledCategories.contains(c.getId()))
                        .map(
                                c -> {
                                    CoffeeMenuVO.Category vo = new CoffeeMenuVO.Category();
                                    BeanUtils.copyProperties(c, vo);
                                    vo.setId(c.getId().toString());
                                    return vo;
                                })
                        .toList());
        Map<Long, List<CoffeeSku>> byProduct =
                skus.stream()
                        .filter(s -> s.getStatus() == 1)
                        .collect(Collectors.groupingBy(CoffeeSku::getProductId));
        result.setProducts(
                products.stream()
                        .filter(
                                p ->
                                        p.getStatus() == 1
                                                && enabledCategories.contains(p.getCategoryId()))
                        .map(
                                p -> {
                                    CoffeeMenuVO.Product vo = new CoffeeMenuVO.Product();
                                    BeanUtils.copyProperties(p, vo);
                                    vo.setId(p.getId().toString());
                                    vo.setCategoryId(p.getCategoryId().toString());
                                    vo.setSkus(
                                            byProduct.getOrDefault(p.getId(), List.of()).stream()
                                                    .map(
                                                            s -> {
                                                                CoffeeMenuVO.Sku sku =
                                                                        new CoffeeMenuVO.Sku();
                                                                BeanUtils.copyProperties(s, sku);
                                                                sku.setId(s.getId().toString());
                                                                sku.setProductId(
                                                                        p.getId().toString());
                                                                sku.setUnavailableReason(
                                                                        Boolean.TRUE.equals(
                                                                                        s
                                                                                                .getAvailable())
                                                                                ? null
                                                                                : "SOLD_OUT");
                                                                return sku;
                                                            })
                                                    .toList());
                                    return vo;
                                })
                        .toList());
        return result;
    }

    @Override
    public CoffeeAdminContextVO context(Long adminId) {
        access.requireAccess(adminId);
        CoffeeAdminContextVO result = new CoffeeAdminContextVO();
        result.setPermissions(access.permissions(adminId));
        result.setStores(
                access.stores(adminId)
                        .stream()
                        .map(
                                s -> {
                                    CoffeeAdminContextVO.Store vo =
                                            new CoffeeAdminContextVO.Store();
                                    vo.setId(s.getId().toString());
                                    vo.setName(s.getName());
                                    return vo;
                                })
                        .toList());
        return result;
    }

    @Override
    public CoffeeStoreVO adminStore(Long adminId, Long storeId) {
        access.requireAccess(adminId);
        access.requireStore(adminId, storeId, "coffee:menu:read");
        return storeVO(support.store(storeId, false));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoffeeStoreVO saveStore(Long adminId, Long storeId, CoffeeStoreSaveDTO dto) {
        access.requireAccess(adminId);
        CoffeeStore store;
        if (storeId == null) {
            access.requireSuper(adminId);
            if (storeMapper.selectCount(Wrappers.<CoffeeStore>lambdaQuery()) > 0) {
                throw BizException.of(BizCode.COFFEE_STORE_ALREADY_EXISTS);
            }
            store = new CoffeeStore();
            store.setPrimarySlot(1L);
            store.setStatus(1);
            store.setTimeZone("Asia/Shanghai");
        } else {
            access.requireStore(adminId, storeId, "coffee:store:write");
            store = support.store(storeId, true);
        }
        BeanUtils.copyProperties(dto, store);
        if (storeId == null) {
            try {
                storeMapper.insert(store);
            } catch (DuplicateKeyException e) {
                throw BizException.of(BizCode.COFFEE_STORE_ALREADY_EXISTS);
            }
        } else {
            // announcement 可清空，显式设置 null 避免 MyBatis-Plus 默认忽略空值。
            storeMapper.update(
                    null,
                    Wrappers.<CoffeeStore>lambdaUpdate()
                            .eq(CoffeeStore::getId, storeId)
                            .set(CoffeeStore::getName, dto.getName())
                            .set(CoffeeStore::getAddress, dto.getAddress())
                            .set(CoffeeStore::getOpeningHours, dto.getOpeningHours())
                            .set(CoffeeStore::getBusinessStatus, dto.getBusinessStatus())
                            .set(CoffeeStore::getAnnouncement, dto.getAnnouncement())
                            .setSql("update_time=CURRENT_TIMESTAMP"));
        }
        return storeVO(store);
    }

    @Override
    public CoffeeAdminMenuVO adminMenu(Long adminId, Long storeId) {
        access.requireAccess(adminId);
        access.requireStore(adminId,storeId,"coffee:menu:read");
        support.store(storeId, false);
        CoffeeAdminMenuVO result = new CoffeeAdminMenuVO();
        result.setStoreId(storeId.toString());
        result.setCategories(categories(storeId).stream().map(this::categoryVO).toList());
        Map<Long, List<CoffeeSku>> byProduct =
                skus(storeId).stream().collect(Collectors.groupingBy(CoffeeSku::getProductId));
        result.setProducts(
                products(storeId).stream()
                        .map(p -> productVO(p, byProduct.getOrDefault(p.getId(), List.of())))
                        .toList());
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoffeeAdminMenuVO.Category saveCategory(
            Long adminId, Long storeId, Long id, CoffeeCategorySaveDTO dto) {
        access.requireAccess(adminId);
        CoffeeCategory category = id == null ? new CoffeeCategory() : categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.of(BizCode.NOT_FOUND);
        }
        Long targetStore = id == null ? storeId : category.getStoreId();
        access.requireStore(adminId,targetStore,"coffee:menu:write");
        support.store(targetStore, true);
        if (dto.getStatus() == 0
                && id != null
                && productMapper.selectCount(
                                Wrappers.<CoffeeProduct>lambdaQuery()
                                        .eq(CoffeeProduct::getCategoryId, id)
                                        .eq(CoffeeProduct::getStatus, 1))
                        > 0) {
            throw CoffeeValues.invalid("请先下架分类下的在售饮品");
        }
        BeanUtils.copyProperties(dto, category);
        category.setStoreId(targetStore);
        if (id == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.updateById(category);
        }
        return categoryVO(category);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CoffeeAdminProductVO saveProduct(
            Long adminId, Long storeId, Long id, CoffeeProductSaveDTO dto) {
        access.requireAccess(adminId);
        CoffeeProduct product = id == null ? new CoffeeProduct() : productMapper.selectById(id);
        if (product == null) {
            throw BizException.of(BizCode.NOT_FOUND);
        }
        Long targetStore = id == null ? storeId : product.getStoreId();
        access.requireStore(adminId,targetStore,"coffee:menu:write");
        support.store(targetStore, true);
        CoffeeCategory category = categoryMapper.selectById(CoffeeValues.id(dto.getCategoryId()));
        if (category == null || !targetStore.equals(category.getStoreId())) {
            throw CoffeeValues.invalid("分类不属于当前门店");
        }
        if (dto.getStatus() == 1 && category.getStatus() != 1) {
            throw CoffeeValues.invalid("停用分类下不能上架饮品");
        }
        List<CoffeeSku> oldSkus =
                id == null
                        ? List.of()
                        : skuMapper.selectList(
                                Wrappers.<CoffeeSku>lambdaQuery()
                                        .eq(CoffeeSku::getProductId, id)
                                        .orderByAsc(CoffeeSku::getId));
        Map<Long, CoffeeSku> byId =
                oldSkus.stream().collect(Collectors.toMap(CoffeeSku::getId, s -> s));
        Map<String, CoffeeSku> byCombination =
                oldSkus.stream().collect(Collectors.toMap(this::combination, s -> s));
        Set<String> combinations = new HashSet<>();
        Set<Long> retained = new HashSet<>();
        List<CoffeeSku> saved = new ArrayList<>();
        for (CoffeeSkuSaveDTO input : dto.getSkus()) {
            if (input == null) {
                throw CoffeeValues.invalid("规格不能为空");
            }
            String key = input.getTemperature() + ":" + input.getSugar();
            if (!combinations.add(key)) {
                throw CoffeeValues.invalid("温度和甜度组合重复");
            }
            CoffeeSku sku =
                    input.getId() == null
                            ? byCombination.get(key)
                            : byId.get(CoffeeValues.id(input.getId()));
            if (input.getId() != null && sku == null) {
                throw CoffeeValues.invalid("规格不属于当前饮品");
            }
            CoffeeSku existingCombination = byCombination.get(key);
            if (sku != null
                    && existingCombination != null
                    && !sku.getId().equals(existingCombination.getId())) {
                throw CoffeeValues.invalid("规格组合已存在，请复用该组合的 ID");
            }
            CoffeeSku savedSku = new CoffeeSku();
            if (sku != null) {
                BeanUtils.copyProperties(sku, savedSku);
            }
            sku = savedSku;
            if (sku.getId() != null && !retained.add(sku.getId())) {
                throw CoffeeValues.invalid("规格 ID 重复");
            }
            BeanUtils.copyProperties(input, sku, "id");
            saved.add(sku);
        }
        BeanUtils.copyProperties(dto, product, "categoryId", "skus");
        product.setCategoryId(category.getId());
        product.setStoreId(targetStore);
        if (id == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
            productMapper.update(
                    null,
                    Wrappers.<CoffeeProduct>lambdaUpdate()
                            .eq(CoffeeProduct::getId, id)
                            .set(CoffeeProduct::getImageUrl, dto.getImageUrl()));
        }
        for (CoffeeSku sku : oldSkus) {
            if (!retained.contains(sku.getId())) {
                sku.setStatus(0);
                skuMapper.updateById(sku);
            }
        }
        for (CoffeeSku sku : saved) {
            sku.setStoreId(targetStore);
            sku.setProductId(product.getId());
            if (sku.getId() == null) {
                skuMapper.insert(sku);
            } else {
                skuMapper.updateById(sku);
            }
        }
        return productVO(
                product,
                skuMapper.selectList(
                        Wrappers.<CoffeeSku>lambdaQuery()
                                .eq(CoffeeSku::getProductId, product.getId())
                                .orderByAsc(CoffeeSku::getId)));
    }

    private String combination(CoffeeSku sku) {
        return sku.getTemperature() + ":" + sku.getSugar();
    }

    private List<CoffeeCategory> categories(Long storeId) {
        return categoryMapper.selectList(
                Wrappers.<CoffeeCategory>lambdaQuery()
                        .eq(CoffeeCategory::getStoreId, storeId)
                        .orderByAsc(CoffeeCategory::getSort, CoffeeCategory::getId));
    }

    private List<CoffeeProduct> products(Long storeId) {
        return productMapper.selectList(
                Wrappers.<CoffeeProduct>lambdaQuery()
                        .eq(CoffeeProduct::getStoreId, storeId)
                        .orderByAsc(CoffeeProduct::getSort, CoffeeProduct::getId));
    }

    private List<CoffeeSku> skus(Long storeId) {
        return skuMapper.selectList(
                Wrappers.<CoffeeSku>lambdaQuery()
                        .eq(CoffeeSku::getStoreId, storeId)
                        .orderByAsc(CoffeeSku::getId));
    }

    private CoffeeAdminMenuVO.Category categoryVO(CoffeeCategory category) {
        CoffeeAdminMenuVO.Category vo = new CoffeeAdminMenuVO.Category();
        BeanUtils.copyProperties(category, vo);
        vo.setId(category.getId().toString());
        return vo;
    }

    private CoffeeAdminProductVO productVO(CoffeeProduct product, List<CoffeeSku> skus) {
        CoffeeAdminProductVO vo = new CoffeeAdminProductVO();
        BeanUtils.copyProperties(product, vo);
        vo.setId(product.getId().toString());
        vo.setCategoryId(product.getCategoryId().toString());
        vo.setSkus(
                skus.stream()
                        .map(
                                s -> {
                                    CoffeeAdminProductVO.Sku sku = new CoffeeAdminProductVO.Sku();
                                    BeanUtils.copyProperties(s, sku);
                                    sku.setId(s.getId().toString());
                                    sku.setProductId(s.getProductId().toString());
                                    return sku;
                                })
                        .toList());
        return vo;
    }

    private CoffeeStoreVO storeVO(CoffeeStore store) {
        CoffeeStoreVO vo = new CoffeeStoreVO();
        BeanUtils.copyProperties(store, vo);
        vo.setId(store.getId().toString());
        CoffeeStoreVO.Capabilities caps = new CoffeeStoreVO.Capabilities();
        caps.setBrowse(support.browsable(store));
        caps.setAddToCart(support.browsable(store));
        boolean ordering = support.browsable(store) && orderingEnabled && paymentEnabled && "OPEN".equals(store.getBusinessStatus());
        caps.setPlaceOrder(ordering);
        caps.setPayOnline(ordering);
        vo.setCapabilities(caps);
        vo.setOrderUnavailableReason(
                !support.browsable(store)
                        ? "ORDERING_DISABLED"
                        : "CLOSED".equals(store.getBusinessStatus())
                                ? "STORE_CLOSED"
                                : "PAUSED".equals(store.getBusinessStatus())
                                        ? "STORE_PAUSED"
                                        : !orderingEnabled ? "ORDERING_DISABLED" : paymentEnabled ? null : "PAYMENT_NOT_READY");
        return vo;
    }

    private String version(Object... values) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(Arrays.toString(values).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
