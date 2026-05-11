package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.ProductCategory;
import com.jinlin24th.jinlin.mapper.ProductCategoryMapper;
import com.jinlin24th.jinlin.service.ProductCategoryService;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
    implements ProductCategoryService {
}


