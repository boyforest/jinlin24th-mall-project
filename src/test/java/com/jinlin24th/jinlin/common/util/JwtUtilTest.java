package com.jinlin24th.jinlin.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    @Test
    void shouldGenerateAndParseToken() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef", 60_000);
        String token = jwtUtil.generateToken(123L);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals(123L, jwtUtil.getUserIdFromToken(token));
        assertEquals(JwtUtil.TOKEN_TYPE_USER, jwtUtil.getTokenType(token));
    }

    @Test
    void shouldRejectBearerPrefix() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef", 60_000);
        String token = jwtUtil.generateToken(123L);
        assertFalse(jwtUtil.validateToken("Bearer " + token));
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.getUserIdFromToken("Bearer " + token));
    }

    @Test
    void shouldRejectInvalidToken() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef", 60_000);
        assertFalse(jwtUtil.validateToken("abc"));
    }

    @Test
    void shouldGenerateAdminToken() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef", 60_000);
        String token = jwtUtil.generateAdminToken(1L, "admin");
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("admin", jwtUtil.getSubjectFromToken(token));
        assertEquals(JwtUtil.TOKEN_TYPE_ADMIN, jwtUtil.getTokenType(token));
        assertEquals(1L, jwtUtil.getAdminIdFromToken(token));
    }

    @Test
    void shouldFailFastWhenSecretIsBlank() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil(" ", 60_000));
    }
}
