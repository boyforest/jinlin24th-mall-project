package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.WxPayUtil;
import com.jinlin24th.jinlin.service.CoffeePayClient;

import okhttp3.Headers;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.*;

class CoffeeCallbackSignatureTest {
    @TempDir Path directory;
    KeyPair keys;
    CoffeePayClient client;
    String body;
    String timestamp;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        var config = new CoffeePayConfig();
        config.setPaymentEnabled(true);
        config.setPlatformPublicKeyId("coffee-key");
        config.setApiV3Key("12345678901234567890123456789012");
        Path privateFile = directory.resolve("private.pem"),
                publicFile = directory.resolve("public.pem");
        Files.writeString(
                privateFile, Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()));
        Files.writeString(
                publicFile, Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        config.setPrivateKeyPath(privateFile.toString());
        config.setPlatformPublicKeyPath(publicFile.toString());
        client = new CoffeePayClient(config, new ObjectMapper());
        client.init();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(config.getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, "123456789012".getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
        String encrypted =
                Base64.getEncoder()
                        .encodeToString(
                                cipher.doFinal(
                                        "{\"mchid\":\"coffee-merchant\",\"trade_state\":\"SUCCESS\"}"
                                                .getBytes(StandardCharsets.UTF_8)));
        body =
                new ObjectMapper()
                        .writeValueAsString(
                                Map.of(
                                        "resource",
                                        Map.of(
                                                "algorithm",
                                                "AEAD_AES_256_GCM",
                                                "ciphertext",
                                                encrypted,
                                                "associated_data",
                                                "transaction",
                                                "nonce",
                                                "123456789012",
                                                "original_type",
                                                "transaction")));
        timestamp = Long.toString(Instant.now().getEpochSecond());
    }

    Headers headers(String serial, String timestamp, String signature) {
        return new Headers.Builder()
                .add("Wechatpay-Serial", serial)
                .add("Wechatpay-Timestamp", timestamp)
                .add("Wechatpay-Nonce", "nonce")
                .add("Wechatpay-Signature", signature)
                .build();
    }

    String signature(String timestamp, String text) {
        return WxPayUtil.sign(
                timestamp + "\nnonce\n" + text + "\n", "SHA256withRSA", keys.getPrivate());
    }

    void invalid(Headers headers, String text) {
        assertEquals(
                BizCode.COFFEE_CHANNEL_INVALID,
                assertThrows(BizException.class, () -> client.notification(headers, text))
                        .getBizCode());
    }

    @Test
    void signedResourceIsVerifiedAndDecrypted() {
        assertEquals(
                "coffee-merchant",
                client.notification(
                                headers("coffee-key", timestamp, signature(timestamp, body)), body)
                        .path("mchid")
                        .asText());
    }

    @Test
    void wrongSignatureAndAlteredBodyAreRejected() {
        invalid(headers("coffee-key", timestamp, "bad-base64"), body);
        invalid(headers("coffee-key", timestamp, signature(timestamp, body)), body + " ");
    }

    @Test
    void goodsKeyAndExpiredSignatureAreRejected() {
        invalid(headers("goods-key", timestamp, signature(timestamp, body)), body);
        String old = Long.toString(Instant.now().minusSeconds(3600).getEpochSecond());
        invalid(headers("coffee-key", old, signature(old, body)), body);
    }

    @Test
    void invalidEncryptedPayloadAndMissingHeadersAreRejected() {
        String bad = body.replace("AEAD_AES_256_GCM", "AES_CBC");
        invalid(headers("coffee-key", timestamp, signature(timestamp, bad)), bad);
        invalid(new Headers.Builder().build(), body);
    }
}
