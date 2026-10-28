package com.jinlin24th.jinlin.service;

import com.fasterxml.jackson.databind.*;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.WxPayUtil;
import com.jinlin24th.jinlin.pojo.vo.WxPayParamsVO;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import okhttp3.*;

import org.springframework.stereotype.Service;

import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 咖啡交易渠道；可配置与商品相同的商户，仅复用无状态签名工具。 */
@Service
@RequiredArgsConstructor
public class CoffeePayClient {
    private final CoffeePayConfig config;
    private final ObjectMapper json;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private final OkHttpClient http =
            new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build();

    @PostConstruct
    public void init() {
        if (config.isPaymentEnabled()) {
            privateKey = WxPayUtil.loadPrivateKeyFromPath(config.getPrivateKeyPath());
            publicKey = WxPayUtil.loadPublicKeyFromPath(config.getPlatformPublicKeyPath());
        }
    }

    public JsonNode request(String method, String uri, Object body) {
        available();
        try {
            String text = body == null ? "" : json.writeValueAsString(body);
            Request.Builder request =
                    new Request.Builder()
                            .url("https://api.mch.weixin.qq.com" + uri)
                            .header("Accept", "application/json")
                            .header("Wechatpay-Serial", config.getPlatformPublicKeyId())
                            .header(
                                    "Authorization",
                                    WxPayUtil.buildAuthorization(
                                            config.getMchId(),
                                            config.getSerialNo(),
                                            privateKey,
                                            method,
                                            uri,
                                            text));
            request.method(
                    method,
                    method.equals("GET")
                            ? null
                            : RequestBody.create(
                                    text, MediaType.get("application/json; charset=utf-8")));
            try (Response response = http.newCall(request.build()).execute()) {
                String result = WxPayUtil.extractBody(response);
                // 错误应答同样验签，不能把未验证的 ORDER_NOT_EXIST 当作关单依据。
                WxPayUtil.validateResponse(
                        config.getPlatformPublicKeyId(), publicKey, response.headers(), result);
                if (!response.isSuccessful())
                    throw new WxPayUtil.ApiException(response.code(), result, response.headers());
                return result.isBlank() ? json.createObjectNode() : json.readTree(result);
            }
        } catch (WxPayUtil.ApiException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE);
        }
    }

    public JsonNode notification(Headers headers, String body) {
        available();
        try {
            String plain =
                    WxPayUtil.parseNotification(
                                    config.getApiV3Key(),
                                    config.getPlatformPublicKeyId(),
                                    publicKey,
                                    headers,
                                    body)
                            .getPlaintext();
            return json.readTree(plain);
        } catch (Exception e) {
            throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
        }
    }

    public WxPayParamsVO params(String prepayId) {
        available();
        var v = new WxPayParamsVO();
        v.setAppId(config.getAppId());
        v.setTimeStamp(Long.toString(Instant.now().getEpochSecond()));
        v.setNonceStr(WxPayUtil.createNonce(32));
        v.setPackageValue("prepay_id=" + prepayId);
        v.setPrepayId(prepayId);
        v.setSignType("RSA");
        v.setPaySign(
                WxPayUtil.sign(
                        v.getAppId()
                                + "\n"
                                + v.getTimeStamp()
                                + "\n"
                                + v.getNonceStr()
                                + "\n"
                                + v.getPackageValue()
                                + "\n",
                        "SHA256withRSA",
                        privateKey));
        return v;
    }

    private void available() {
        if (!config.isPaymentEnabled()) throw BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE);
    }
}
