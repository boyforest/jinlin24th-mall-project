package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.ProductSkuDTO;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;

public interface ProductSkuService extends IService<ProductSku> {
    IPage<ProductSkuVO> adminPage(long page, long size, Long productId, Integer status);

    ProductSkuVO getVO(Long id);

    ProductSkuVO create(ProductSkuDTO dto);

    ProductSkuVO update(Long id, ProductSkuDTO dto);

    Boolean delete(Long id);
}


