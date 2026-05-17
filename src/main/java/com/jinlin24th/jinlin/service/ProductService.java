package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.pojo.dto.ProductDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;

import java.util.List;

public interface ProductService extends IService<Product> {
    IPage<ProductVO> adminPage(long page, long size, Integer status, Long categoryId, String keyword);

    ProductVO getVO(Long id);

    ProductVO getUserVO(Long id);

    ProductVO create(ProductDTO dto);

    ProductVO update(Long id, ProductDTO dto);

    Boolean delete(Long id);

    IPage<ProductVO> userPage(long page, long size, Long categoryId, String keyword);

    List<ProductSkuVO> userSkus(Long productId);
}
