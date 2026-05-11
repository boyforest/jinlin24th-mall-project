package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.Cart;
import com.jinlin24th.jinlin.mapper.CartMapper;
import com.jinlin24th.jinlin.service.CartService;
import org.springframework.stereotype.Service;

@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart>
    implements CartService {
}


