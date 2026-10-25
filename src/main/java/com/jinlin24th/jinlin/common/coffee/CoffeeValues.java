package com.jinlin24th.jinlin.common.coffee;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;

import java.math.BigDecimal;

public final class CoffeeValues {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private CoffeeValues() {}

    public static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid("ID 必须为正整数十进制字符串");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw invalid("ID 超出范围");
        }
    }

    public static long cents(BigDecimal value) {
        long cents = value.movePointRight(2).longValueExact();
        if (cents < 0 || cents > MAX_SAFE_INTEGER) {
            throw new IllegalStateException("历史金额超出分制安全整数范围");
        }
        return cents;
    }

    public static BizException invalid(String message) {
        return BizException.of(BizCode.PARAM_INVALID, message);
    }
}
