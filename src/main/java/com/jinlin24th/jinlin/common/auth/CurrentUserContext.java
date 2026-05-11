package com.jinlin24th.jinlin.common.auth;

/**
 * 当前登录用户上下文（ThreadLocal）
 * <p>
 * 作用：在一次请求处理链路内（同一线程），任意位置都能获取当前 userId。
 * 后续如果你换 Spring Security，这里也可以作为过渡层，或直接替换实现。
 */
public class CurrentUserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
