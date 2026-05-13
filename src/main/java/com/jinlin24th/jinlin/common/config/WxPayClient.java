package com.jinlin24th.jinlin.common.config;

import com.jinlin24th.jinlin.common.constant.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

/**
 * 微信支付HTTP客户端
 * 封装了请求签名、发送、应答验签的完整流程
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayClient {

    private static final String HOST = "https://api.mch.weixin.qq.com";

    private final String mchid;
    private final String certificateSerialNo;
    private final PrivateKey privateKey;
    private final String wechatPayPublicKeyId;
    private final PublicKey wechatPayPublicKey;

    public WxPayClient(@Value("${wx.pay.mch-id:}") String mchid,
                       @Value("${wx.pay.serial-no:}") String certificateSerialNo,
                       @Value("${wx.pay.private-key-path:}") String privateKeyPath,
                       @Value("${wx.pay.serial-no:}") String wechatPayPublicKeyId,
                       @Value("${wx.pay.certificate-path:}") String wechatPayPublicKeyPath) {
        this.mchid = mchid;
        this.certificateSerialNo = certificateSerialNo;
        this.privateKey = com.jinlin24th.jinlin.common.util.WxPayUtil.loadPrivateKeyFromPath(privateKeyPath);
        this.wechatPayPublicKeyId = wechatPayPublicKeyId;
        this.wechatPayPublicKey = com.jinlin24th.jinlin.common.util.WxPayUtil.loadPublicKeyFromPath(wechatPayPublicKeyPath);

        log.info("微信支付客户端初始化成功,商户号: {}", mchid);
    }

    /**
     * 发送GET请求，返回已验签的应答Body
     */
    public String sendGet(String uri) {
        return sendRequest("GET", uri, null);
    }

    /**
     * 发送POST请求，返回已验签的应答Body
     */
    public String sendPost(String uri, String reqBody) {
        return sendRequest("POST", uri, reqBody);
    }

    /**
     * 使用公钥加密敏感信息
     */
    public String encrypt(String plainText) {
        return com.jinlin24th.jinlin.common.util.WxPayUtil.encrypt(this.wechatPayPublicKey, plainText);
    }

    private String sendRequest(String method, String uri, String reqBody) {
        Request.Builder reqBuilder = new Request.Builder().url(HOST + uri);
        reqBuilder.addHeader("Accept", "application/json");
        reqBuilder.addHeader("Wechatpay-Serial", wechatPayPublicKeyId);
        reqBuilder.addHeader("Authorization", com.jinlin24th.jinlin.common.util.WxPayUtil.buildAuthorization(
                mchid, certificateSerialNo, privateKey, method, uri, reqBody));

        if (reqBody != null) {
            reqBuilder.addHeader("Content-Type", "application/json");
            RequestBody body = RequestBody.create(
                    reqBody,
                    MediaType.parse("application/json; charset=utf-8"));
            reqBuilder.method(method, body);
        } else {
            reqBuilder.method(method, null);
        }

        // 配置超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try (Response httpResponse = client.newCall(reqBuilder.build()).execute()) {
            String respBody = com.jinlin24th.jinlin.common.util.WxPayUtil.extractBody(httpResponse);
            if (httpResponse.code() >= HttpStatus.OK && httpResponse.code() < HttpStatus.MULTIPLE_CHOICES) {
                com.jinlin24th.jinlin.common.util.WxPayUtil.validateResponse(wechatPayPublicKeyId, wechatPayPublicKey,
                        httpResponse.headers(), respBody);
                return respBody;
            } else {
                throw new com.jinlin24th.jinlin.common.util.WxPayUtil.ApiException(httpResponse.code(), respBody, httpResponse.headers());
            }
        } catch (IOException e) {
            log.error("发送HTTP请求失败: {}", uri, e);
            throw new UncheckedIOException("发送请求到 " + uri + " 失败.", e);
        }
    }
}
