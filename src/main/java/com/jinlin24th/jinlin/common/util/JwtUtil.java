
package com.jinlin24th.jinlin.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
/**
 * 主要职责：
 * 1) 生成 token（包含 userId、过期时间）
 * 2) 解析 token（取 userId / jti）
 * 3) 校验 token（签名 + 过期 + 格式）
 * <p>项目中的“登录态增强”会用到 jti：
 * - 登录时生成 jti，并写入 Redis
 * - 请求时解析 jti，并与 Redis 中的 jti 对比（实现登出/踢下线/单点登录）
 */
public class JwtUtil {

    public static final String TOKEN_TYPE_USER = "USER";
    public static final String TOKEN_TYPE_ADMIN = "ADMIN";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMillis) {
        this.signingKey = buildSigningKey(secret);
        this.expirationMillis = expirationMillis;
    }

    private static SecretKey buildSigningKey(String secret) {
        // 1) Base64 编码密钥（推荐，强度更容易达标）
        // 注意：HMAC-SHA 的密钥长度必须足够（建议至少 32 字节），否则 jjwt 会直接抛异常。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 不能为空");
        }

        byte[] base64KeyBytes = null;
        try {
            base64KeyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException ignored) {
        }

        byte[] keyBytes = base64KeyBytes != null && base64KeyBytes.length >= 32
                ? base64KeyBytes : secret.getBytes(StandardCharsets.UTF_8);

        try {
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret 强度不足：请使用至少 32 字节的密钥（建议 Base64 编码）", e);
        }
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bearer", 0, "Bearer".length())
                && (normalized.length() == "Bearer".length() || Character.isWhitespace(normalized.charAt("Bearer".length())))) {
            throw new IllegalArgumentException("token 不应包含 Bearer 前缀");
        }
        return normalized;
    }

    private Claims parseClaims(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null || normalizedToken.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }

        // 解析并校验签名/过期时间，失败会抛 JwtException
        // 说明：这里做的是“语法 + 签名 + 过期”的综合校验
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(normalizedToken)
                .getPayload();
    }

    /**
     * 生成 Token
     * @param userId 用户ID
     * @return JWT Token
     */
    public String generateToken(Long userId) {
        // 兼容旧调用：内部自动生成 jti
        return generateToken(userId, generateJti());
    }

    public String generateAdminToken(String adminName) {
        return generateAdminToken(adminName, generateJti());
    }

    /**
     * 生成 token 唯一标识（jti）
     * <p>
     * 说明：用于“登录态增强”。每次登录生成新的 jti，并写入 Redis，旧 token 会因 jti 不匹配而失效。
     */
    public String generateJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 Token（指定 jti）
     *
     * @param userId 用户ID
     * @param jti token 唯一标识
     * @return JWT Token
     */
    public String generateToken(Long userId, String jti) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti 不能为空");
        }

        Map<String, Object> claims = new HashMap<>();
        // 业务自定义字段（注意：JWT payload 是明文可解码的，不要放密码/隐私信息）
        claims.put("userId", userId);
        claims.put("tokenType", TOKEN_TYPE_USER);
        //todo
        return buildToken(userId.toString(), claims, jti);
    }

    public String generateAdminToken(String adminName, String jti) {
        if (adminName == null || adminName.isBlank()) {
            throw new IllegalArgumentException("adminName 不能为空");
        }
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti 不能为空");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", TOKEN_TYPE_ADMIN);
        return buildToken(adminName.trim(), claims, jti);
    }

    private String buildToken(String subject, Map<String, Object> claims, String jti) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .id(jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 从 Token 中获取用户ID
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("token subject 不是合法的 userId", e);
        }
    }

    /**
     * 从 token 中解析 jti（token 唯一标识）
     */
    public String getJtiFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getId();
    }

    public String getTokenType(String token) {
        Claims claims = parseClaims(token);
        return claims.get("tokenType", String.class);
    }

    public String getSubjectFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * 验证 Token 是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
