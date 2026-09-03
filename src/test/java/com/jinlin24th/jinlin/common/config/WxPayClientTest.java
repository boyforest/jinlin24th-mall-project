package com.jinlin24th.jinlin.common.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WxPayClientTest {

    @TempDir
    static Path tempDir;

    private static Path privateKeyFile;
    private static Path publicKeyFile;

    @Configuration
    @Import(WxPayClient.class)
    @EnableConfigurationProperties(WxPayConfig.class)
    static class TestPayConfig {
    }

    @BeforeAll
    static void generateKeyFiles() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        privateKeyFile = tempDir.resolve("apiclient_key.pem");
        publicKeyFile = tempDir.resolve("pub_key.pem");
        Files.write(privateKeyFile, privatePem.getBytes(StandardCharsets.UTF_8));
        Files.write(publicKeyFile, publicPem.getBytes(StandardCharsets.UTF_8));
    }

    private ApplicationContextRunner payContextRunner(String serialNo, String platformKeyId) {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "wx.pay.enabled=true",
                        "wx.pay.mch-id=1900000109",
                        "wx.pay.api-v3-key=0123456789abcdef0123456789abcdef",
                        "wx.pay.serial-no=" + serialNo,
                        "wx.pay.platform-public-key-id=" + platformKeyId,
                        "wx.pay.private-key-path=" + privateKeyFile,
                        "wx.pay.certificate-path=" + publicKeyFile)
                .withUserConfiguration(TestPayConfig.class);
    }

    @Test
    void constructorShouldBindPlatformPublicKeyIdFromItsOwnProperty() {
        payContextRunner("MERCHANT-SERIAL-AAA", "PLATFORM-ID-BBB").run(context -> {
            WxPayClient client = context.getBean(WxPayClient.class);
            assertEquals("PLATFORM-ID-BBB", client.getWechatPayPublicKeyId());
        });
    }

    @Test
    void merchantSerialNoShouldNotLeakIntoPublicKeyId() {
        payContextRunner("MERCHANT-SERIAL-AAA", "PLATFORM-ID-BBB").run(context -> {
            WxPayClient client = context.getBean(WxPayClient.class);
            assertNotEquals("MERCHANT-SERIAL-AAA", client.getWechatPayPublicKeyId());
        });
    }
}
