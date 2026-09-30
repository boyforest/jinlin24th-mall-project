package com.jinlin24th.jinlin.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 手机号脱敏测试。
 * <p>
 * 用途：C 端把"推荐官手机号"展示给下游用户时，不得明文输出完整号码。
 * 完整号码属于个人信息，一旦暴露就会变成可批量收集的资源（竞品挖人、电话骚扰）。
 * 脱敏后仍能让人认出是哪个号（用于联系对接），但不构成可直接拨打的号码清单。
 * <p>
 * 设计原则：**宁可多遮**。任何非预期的输入（超长、超短、含分隔符）都不得泄漏
 * 超过前 3 位与后 2 位。
 */
class PhoneMaskUtilTest {

    @Test
    @DisplayName("标准 11 位手机号：保留前 3 后 4")
    void shouldMaskStandardMobile() {
        assertEquals("138****8000", PhoneMaskUtil.mask("13800138000"));
    }

    @Test
    @DisplayName("null 与空白原样返回，不抛异常")
    void shouldHandleNullOrBlank() {
        assertNull(PhoneMaskUtil.mask(null));
        assertEquals("", PhoneMaskUtil.mask(""));
        assertEquals("   ", PhoneMaskUtil.mask("   "));
    }

    @Test
    @DisplayName("极短输入（<=4 位）必须整体遮蔽，不能泄漏")
    void shouldFullyMaskVeryShortInput() {
        assertEquals("****", PhoneMaskUtil.mask("1"));
        assertEquals("****", PhoneMaskUtil.mask("1234"));
    }

    @Test
    @DisplayName("非标准长度：最多保留前 3 后 2，中间全部遮蔽")
    void shouldNotLeakMoreThanThreePlusTwoForOddLengths() {
        // 7 位：前 3 后 2 → 138****00
        assertEquals("138****00", PhoneMaskUtil.mask("1380000"));
        // 5 位：只能保留前 2 → 13****
        assertEquals("13****", PhoneMaskUtil.mask("13800"));
        // 12 位（非标准长度）：前 3 后 2 → 123****12
        assertEquals("123****12", PhoneMaskUtil.mask("123456789012"));
        // 13 位（含国际前缀）：前 3 后 2
        assertEquals("861****00", PhoneMaskUtil.mask("8613800138000"));
    }

    @Test
    @DisplayName("带分隔符的号码也必须被遮蔽（不能因为格式不同就放行）")
    void shouldMaskFormattedNumbers() {
        String masked = PhoneMaskUtil.mask("138-0013-8000");
        assertEquals("138****00", masked);
        org.junit.jupiter.api.Assertions.assertFalse(masked.contains("0013"),
            "中间段不得泄漏：" + masked);
    }

    @Test
    @DisplayName("首尾空白应被忽略后再脱敏（避免 ' 13800138000 ' 走非标准分支）")
    void shouldTrimBeforeMasking() {
        assertEquals("138****8000", PhoneMaskUtil.mask("  13800138000  "));
    }

    @Test
    @DisplayName("原始号码不得原样出现在结果中")
    void maskedResultMustDifferFromOriginal() {
        String raw = "13800138000";
        String masked = PhoneMaskUtil.mask(raw);
        org.junit.jupiter.api.Assertions.assertNotEquals(raw, masked);
        org.junit.jupiter.api.Assertions.assertFalse(masked.contains("0013"),
            "中间四位是关键识别段，必须被遮蔽");
    }
}
