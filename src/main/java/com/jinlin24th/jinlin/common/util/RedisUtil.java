package com.jinlin24th.jinlin.common.util;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
/**
 * Redis 工具类（基于 StringRedisTemplate）
 * <p>
 * 设计取舍说明：
 * 1) key 非法（null/空串）属于“代码错误”，因此直接抛 IllegalArgumentException（更早暴露问题）
 * 2) Redis IO 异常属于“外部依赖异常”，默认只记录日志并返回 false/null（让上层决定是否降级）
 *    - 如果某个场景是“关键路径”（例如登录态增强写 jti），建议上层收到 false 后直接抛 BizException
 * 3) 统一使用 Duration 作为 TTL 参数：语义清晰、不容易写错单位
 */
public class RedisUtil {

    /**
     * 全站 Redis key 统一前缀，新增业务 key 都应以该前缀开始。
     */
    public static final String KEY_PREFIX = "ecommerce:";

    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRE_SCRIPT = new DefaultRedisScript<>(
        """
        local current = redis.call('incrby', KEYS[1], ARGV[1])
        if current == tonumber(ARGV[1]) then
            redis.call('pexpire', KEYS[1], ARGV[2])
        end
        return current
        """,
        Long.class
    );

    private final StringRedisTemplate redis;

    public RedisUtil(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ---------- Key ----------
    // expire 过期时间
    /**
     * 设置 key 的过期时间
     *
     * @param key redis key
     * @param ttl 过期时间（必须 > 0）
     * @return 是否设置成功
     */
    public boolean expire(String key, Duration ttl) {
        String k = requireKey(key); // requirekey（必填）检查key不能为空
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须 > 0");
        }
        try {
            return Boolean.TRUE.equals(redis.expire(k, ttl));
        } catch (DataAccessException e) {
            log.warn("Redis expire 失败, key={}, ttl={}", k, ttl, e);
            return false;
        }
    }

    // 获取剩余 ttl
    /**
     * 获取 key 剩余过期时间（秒）
     *
     * @return -1 永久有效；-2 key 不存在（Redis 语义）
     */
    public long ttlSeconds(String key) {
        String k = requireKey(key);
        Long v = redis.getExpire(k, TimeUnit.SECONDS);
        return v == null ? -2 : v;
    }

    // 判断 key 是否存在
    /**
     * 判断 key 是否存在
     */
    public boolean exists(String key) {
        String k = requireKey(key);
        Boolean ok = redis.hasKey(k);
        return Boolean.TRUE.equals(ok);
    }

    // 批量删除
    /**
     * 批量删除 Redis 键
     *
     * @param keys 要删除的键名数组，支持可变参数
     * @return 实际删除的键数量，如果输入为空或删除失败则返回 0
     */
    public long delete(String... keys) {
        if (keys == null || keys.length == 0) return 0;
        List<String> ks = Arrays.stream(keys).filter(StringUtils::hasText).distinct().toList();
        if (ks.isEmpty()) return 0;
        try {
            Long c = redis.delete(ks);
            return c == null ? 0 : c;
        } catch (DataAccessException e) {
            log.warn("Redis delete 失败, keys={}", ks, e);
            return 0;
        }
    }

    /**
     * 严格删除：Redis 异常直接抛出，适合登出、踢下线等关键路径。
     */
    public long deleteRequiredForAuth(String... keys) {
        if (keys == null || keys.length == 0) return 0;
        List<String> ks = Arrays.stream(keys).filter(StringUtils::hasText).distinct().toList();
        if (ks.isEmpty()) return 0;
        try {
            Long c = redis.delete(ks);
            return c == null ? 0 : c;
        } catch (DataAccessException e) {
            log.error("Redis auth delete 失败, keys={}", ks, e);
            throw new RedisConnectionFailureException("Redis 登录态删除失败", e);
        }
    }

    // ---------- String ----------
    // GET
    /**
     * 获取 String 值
     *
     * @return 不存在返回 null；异常也返回 null（并记录日志）
     */
    public String get(String key) {
        String k = requireKey(key);
        try {
            return redis.opsForValue().get(k);
        } catch (DataAccessException e) {
            log.warn("Redis get 失败, key={}", k, e);
            return null;
        }
    }

    /**
     * 严格读取：Redis 异常直接抛出，适合登录态、风控等关键路径。
     *
     * @return key 不存在时返回 null
     */
    public String getRequiredForAuth(String key) {
        String k = requireKey(key);
        try {
            return redis.opsForValue().get(k);
        } catch (DataAccessException e) {
            log.error("Redis auth get 失败, key={}", k, e);
            throw new RedisConnectionFailureException("Redis 登录态读取失败", e);
        }
    }

    public boolean set(String key, String value, Duration ttl) {
        String k = requireKey(key);
        requireTtl(ttl);
        try {
            redis.opsForValue().set(k, value, ttl);
            return true;
        } catch (DataAccessException e) {
            log.warn("Redis set 失败, key={}, ttl={}", k, ttl, e);
            return false;
        }
    }

    public void setRequired(String key, String value, Duration ttl) {
        String k = requireKey(key);
        requireTtl(ttl);
        try {
            redis.opsForValue().set(k, value, ttl);
        } catch (DataAccessException e) {
            log.error("Redis setRequired 失败, key={}, ttl={}", k, ttl, e);
            throw new RedisConnectionFailureException("Redis 写入失败", e);
        }
    }

    /**
     * 写入 JSON 对象缓存。
     *
     * @param key Redis key，必须使用统一前缀
     * @param value 任意可 JSON 序列化对象
     * @param ttl 过期时间，必须大于 0
     * @return 是否写入成功
     */
    public boolean setObject(String key, Object value, Duration ttl) {
        return set(key, JSON.toJSONString(value), ttl);
    }

    /**
     * 读取 JSON 对象缓存。
     *
     * @param key Redis key
     * @param clazz 目标类型
     * @return 缓存不存在或解析失败时返回 null
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.warn("Redis JSON 对象解析失败，将删除脏缓存, key={}", key, e);
            delete(key);
            return null;
        }
    }

    /** SETNX：幂等/简单锁/单点登录标记 */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        String k = requireKey(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须 > 0");
        }
        Boolean ok = redis.opsForValue().setIfAbsent(k, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    public boolean setIfAbsentRequired(String key, String value, Duration ttl) {
        String k = requireKey(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须 > 0");
        }
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(k, value, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException e) {
            log.error("Redis setIfAbsentRequired 失败, key={}, ttl={}", k, ttl, e);
            throw new RedisConnectionFailureException("Redis 幂等标记写入失败", e);
        }
    }

    /**
     * 递增（常用于限流计数器）
     */
    public long incr(String key, long delta) {
        String k = requireKey(key);
        try {
            Long v = redis.opsForValue().increment(k, delta);
            return v == null ? 0 : v;
        } catch (DataAccessException e) {
            log.warn("Redis incr 失败, key={}, delta={}", k, delta, e);
            return 0;
        }
    }

    /**
     * 自减操作，适合计数器回退等场景。
     */
    public long decr(String key, long delta) {
        String k = requireKey(key);
        if (delta <= 0) {
            throw new IllegalArgumentException("delta 必须 > 0");
        }
        try {
            Long v = redis.opsForValue().decrement(k, delta);
            return v == null ? 0 : v;
        } catch (DataAccessException e) {
            log.warn("Redis decr 失败, key={}, delta={}", k, delta, e);
            return 0;
        }
    }

    /**
     * 原子递增并设置过期时间。
     * <p>
     * 用 Lua 保证第一次计数和 TTL 设置在 Redis 内一次完成，避免 incr 成功但 expire 丢失。
     */
    public long incrementWithExpire(String key, long delta, Duration ttl) {
        String k = requireKey(key);
        if (delta <= 0) {
            throw new IllegalArgumentException("delta 必须 > 0");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须 > 0");
        }
        try {
            Long v = redis.execute(
                INCREMENT_WITH_EXPIRE_SCRIPT,
                Collections.singletonList(k),
                String.valueOf(delta),
                String.valueOf(ttl.toMillis())
            );
            return v == null ? 0 : v;
        } catch (DataAccessException e) {
            log.error("Redis incrementWithExpire 失败, key={}, ttl={}", k, ttl, e);
            throw new RedisConnectionFailureException("Redis 限流计数失败", e);
        }
    }

    // ---------- Hash ----------
    /**
     * 获取 Hash 的所有 field-value
     */
    public Map<Object, Object> hGetAll(String key) {
        String k = requireKey(key);
        try {
            return redis.opsForHash().entries(k);
        } catch (DataAccessException e) {
            log.warn("Redis hGetAll 失败, key={}", k, e);
            return Collections.emptyMap();
        }
    }

    private static String requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key 不能为空");
        }
        String normalized = key.trim();
        if (!normalized.startsWith(KEY_PREFIX)) {
            log.debug("Redis key 未使用统一前缀: key={}", normalized);
        }
        return normalized;
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须 > 0，禁止永久缓存");
        }
    }
}
