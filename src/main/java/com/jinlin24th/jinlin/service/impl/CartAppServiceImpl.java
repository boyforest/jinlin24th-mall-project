package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.pojo.dto.CartDTO;
import com.jinlin24th.jinlin.pojo.entity.Cart;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.CartVO;
import com.jinlin24th.jinlin.service.CartAppService;
import com.jinlin24th.jinlin.service.CartService;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartAppServiceImpl implements CartAppService {

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductSkuService productSkuService;

    @Override
    public List<CartVO> list(Long userId) {
        // 只查询当前用户的购物车记录（用户维度隔离，避免越权）
        List<Cart> carts = cartService.lambdaQuery()
            .eq(Cart::getUserId, userId)
            .orderByDesc(Cart::getId)
            .list();
        if (carts.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量收集商品/sku，避免 N+1 查询
        Set<Long> productIds = carts.stream().map(Cart::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> skuIds = carts.stream().map(Cart::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Product> productById = productIds.isEmpty()
            ? Collections.emptyMap()
            : productService.listByIds(productIds).stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, ProductSku> skuById = skuIds.isEmpty()
            ? Collections.emptyMap()
            : productSkuService.listByIds(skuIds).stream().collect(Collectors.toMap(ProductSku::getId, s -> s, (a, b) -> a));

        return carts.stream()
            .map(c -> toVO(c, productById.get(c.getProductId()), skuById.get(c.getSkuId())))
            .collect(Collectors.toList());
    }

    @Override
    public CartVO add(Long userId, CartDTO dto) {
        // 加购逻辑：同一 userId + skuId 只保留一条记录，重复添加则累加数量
        if (dto == null || dto.getSkuId() == null) {
            return null;
        }
        ProductSku sku = productSkuService.getById(dto.getSkuId());
        if (sku == null) {
            return null;
        }
        Long productId = dto.getProductId() != null ? dto.getProductId() : sku.getProductId();

        Cart cart = cartService.lambdaQuery()
            .eq(Cart::getUserId, userId)
            .eq(Cart::getSkuId, dto.getSkuId())
            .last("limit 1")
            .one();
        if (cart == null) {
            // 新增购物车记录
            cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setSkuId(dto.getSkuId());
            cart.setQuantity(dto.getQuantity() == null ? 1 : dto.getQuantity());
            cart.setChecked(dto.getChecked() == null ? 1 : dto.getChecked());
            cartService.save(cart);
        } else {
            // 已存在则累加数量，并按需更新选中状态
            int addQty = dto.getQuantity() == null ? 1 : dto.getQuantity();
            cart.setQuantity((cart.getQuantity() == null ? 0 : cart.getQuantity()) + addQty);
            if (dto.getChecked() != null) {
                cart.setChecked(dto.getChecked());
            }
            cartService.updateById(cart);
        }

        Product product = productService.getById(productId);
        return toVO(cart, product, sku);
    }

    @Override
    public CartVO update(Long userId, Long id, CartDTO dto) {
        // 更新购物车项（推荐写法）：直接在 UPDATE 语句里带上 userId 条件，避免“先查再改”导致越权
        if (userId == null || id == null || dto == null) {
            return null;
        }

        boolean updated = cartService.lambdaUpdate()
            .eq(Cart::getId, id)
            .eq(Cart::getUserId, userId)
            .set(dto.getQuantity() != null, Cart::getQuantity, dto.getQuantity())
            .set(dto.getChecked() != null, Cart::getChecked, dto.getChecked())
            .update();

        // 更新失败：可能是记录不存在，或不属于当前用户
        if (!updated) {
            return null;
        }

        // 更新成功后再查一次用于组装返回值（也带 userId 条件，防止误取）
        Cart cart = cartService.lambdaQuery()
            .eq(Cart::getId, id)
            .eq(Cart::getUserId, userId)
            .one();
        if (cart == null) {
            return null;
        }

        Product product = productService.getById(cart.getProductId());
        ProductSku sku = productSkuService.getById(cart.getSkuId());
        return toVO(cart, product, sku);
    }

    @Override
    public Boolean delete(Long userId, Long id) {
        // 删除购物车项：同样用“带 userId 条件的删除”，防止越权删除
        if (userId == null || id == null) {
            return false;
        }
        return cartService.lambdaUpdate()
            .eq(Cart::getId, id)
            .eq(Cart::getUserId, userId)
            .remove();
    }

    private CartVO toVO(Cart cart, Product product, ProductSku sku) {
        // VO = 购物车记录 + 商品信息快照（名称/主图）+ SKU 信息（规格/价格/库存）
        CartVO vo = new CartVO();
        BeanUtils.copyProperties(cart, vo);
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductMainImage(product.getMainImage());
        }
        if (sku != null) {
            vo.setSkuName(sku.getSkuName());
            vo.setSkuImage(sku.getSkuImage());
            vo.setPrice(sku.getPrice());
            vo.setMemberPrice(sku.getMemberPrice());
            vo.setStock(sku.getStock());
        }
        return vo;
    }
}
