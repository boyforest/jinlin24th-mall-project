package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;

/** 新域拒绝未知字段，避免静默接收客户端身份、价格或支付开关。 */
public abstract class CoffeeRequestDTO {
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw CoffeeValues.invalid("不支持的请求字段：" + field);
    }
}
