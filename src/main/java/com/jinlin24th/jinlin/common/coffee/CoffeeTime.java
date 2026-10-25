package com.jinlin24th.jinlin.common.coffee;

import java.time.*;

public final class CoffeeTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private CoffeeTime() {}

    public static LocalDateTime now() {
        // 与现有 MySQL DATETIME 秒精度一致，避免初始待查时间被数据库四舍五入到未来。
        return LocalDateTime.now(ZONE).withNano(0);
    }

    public static String text(LocalDateTime time) {
        return time == null ? null : time.atZone(ZONE).toOffsetDateTime().toString();
    }

    public static LocalDateTime channel(String time) {
        return OffsetDateTime.parse(time).atZoneSameInstant(ZONE).toLocalDateTime().withNano(0);
    }
}
