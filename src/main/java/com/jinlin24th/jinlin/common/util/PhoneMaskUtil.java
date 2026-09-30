package com.jinlin24th.jinlin.common.util;

/**
 * 手机号脱敏工具。
 * <p>
 * 使用场景：C 端把"别人的手机号"（如推荐官）展示给当前用户时，必须脱敏。
 * 完整号码属于个人信息，明文暴露会变成可批量收集的资源（竞品挖人、电话骚扰）。
 * <p>
 * 设计原则：**宁可多遮**。只有标准 11 位手机号才保留前 3 后 4；
 * 其它长度一律最多保留前 3 后 2，极短输入整体遮蔽。
 * 这样即便遇到带分隔符、带国际前缀等非预期输入，也不会泄漏可用的号码。
 */
public final class PhoneMaskUtil {

    private static final String STARS = "****";

    private PhoneMaskUtil() {
    }

    /**
     * 脱敏手机号。
     *
     * @param phone 原始号码，可为 null
     * @return 脱敏后的字符串；入参为 null/空白时原样返回
     */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        int length = value.length();

        // 标准中国大陆手机号：138****8000
        if (length == 11) {
            return value.substring(0, 3) + STARS + value.substring(7);
        }
        // 过短：整体遮蔽，不保留任何数字
        if (length <= 4) {
            return STARS;
        }
        // 其它长度（含带分隔符、国际前缀等）：最多保留前 3 后 2
        if (length >= 6) {
            return value.substring(0, 3) + STARS + value.substring(length - 2);
        }
        // 5 位：只保留前 2
        return value.substring(0, 2) + STARS;
    }
}
