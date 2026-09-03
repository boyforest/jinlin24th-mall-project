package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.config.WxPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WxPayServiceImplTest {

    private static final String PLATFORM_KEY_ID = "PUBKEY_ID_8888";

    @Mock
    private WxPayClient wxPayClient;

    private WxPayServiceImpl service;
    private KeyPair keyPair;
    private KeyPair wrongKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = new WxPayServiceImpl();
        ReflectionTestUtils.setField(service, "wxPayClient", wxPayClient);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        wrongKeyPair = generator.generateKeyPair();
        when(wxPayClient.getWechatPayPublicKey()).thenReturn(keyPair.getPublic());
        when(wxPayClient.getWechatPayPublicKeyId()).thenReturn(PLATFORM_KEY_ID);
    }

    @Test
    void validSignatureWithMatchingSerialShouldPass() throws Exception {
        boolean result = invokeVerify(buildCallback("2001", true, 0, keyPair.getPrivate()));
        assertTrue(result);
    }

    @Test
    void validSignatureButSerialMismatchShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback("2001", false, 0, keyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void tamperedSignatureShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback(PLATFORM_KEY_ID, true, 0, wrongKeyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void expiredTimestampShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback(PLATFORM_KEY_ID, true, 400, keyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void missingParamsShouldFail() throws Exception {
        boolean nullResult = invokeVerify(null, null, null, null, null);
        assertFalse(nullResult);
    }

    private String[] buildCallback(String serial, boolean serialMatches, long secondsAgo,
                                   java.security.PrivateKey signKey) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond() - secondsAgo);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String body = "{\"dummy\":\"ok\"}";
        String message = ts + "\n" + nonce + "\n" + body + "\n";
        String signature = com.jinlin24th.jinlin.common.util.WxPayUtil.sign(
                message, "SHA256withRSA", signKey);
        String effectiveSerial = serialMatches ? PLATFORM_KEY_ID : serial;
        return new String[]{ts, nonce, signature, effectiveSerial, body};
    }

    private boolean invokeVerify(String[] parts) throws Exception {
        return invokeVerify(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    private boolean invokeVerify(String timestamp, String nonce, String signature,
                                 String serial, String body) throws Exception {
        Method method = WxPayServiceImpl.class.getDeclaredMethod(
                "verifyCallbackSignature", String.class, String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, timestamp, nonce, signature, serial, body);
    }
}
