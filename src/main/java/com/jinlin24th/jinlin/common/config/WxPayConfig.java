package com.jinlin24th.jinlin.common.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wx.pay")
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayConfig {

    /**
     * 服务商商户号
     */
    private String mchId;

    /**
     * 服务商APIv3密钥
     */
    private String apiV3Key;

    /**
     * 服务商证书序列号
     */
    private String serialNo;

    /**
     * 微信支付平台公钥 ID（用于响应/回调验签，与商户API证书序列号不同）
     */
    private String platformPublicKeyId;

    /**
     * 商户API私钥路径
     */
    private String privateKeyPath;

    /**
     * 微信支付平台证书路径
     */
    private String certificatePath;

    /**
     * API基础URL
     */
    private String apiBaseUrl = "https://api.mch.weixin.qq.com";

    /**
     * 通知回调地址
     */
    private String notifyUrl;

    /**
     * 退款回调地址
     */
    private String refundNotifyUrl;

    /**
     * 启动期配置校验。
     * <p>
     * 只在 {@code wx.pay.enabled=true} 时生效（本类本身已带该条件注解）。
     * 目的是把"配置漏填"从"运行时静默失败"变成"启动即失败"：
     * docker-compose 里 {@code WX_PAY_NOTIFY_URL: ${WX_PAY_NOTIFY_URL:-}} 会把未配置的变量
     * 传成空字符串，从而覆盖 Spring 侧的默认值——若不在启动时拦截，就会出现
     * "支付成功但商户收不到回调、订单永远停在待付款"的隐性故障。
     */
    @PostConstruct
    public void validate() {
        requireHttps("wx.pay.notify-url", notifyUrl);
        requireHttps("wx.pay.refund-notify-url", refundNotifyUrl);
        requireText("wx.pay.mch-id", mchId);
        requireText("wx.pay.api-v3-key", apiV3Key);
        requireText("wx.pay.serial-no", serialNo);
        requireText("wx.pay.platform-public-key-id", platformPublicKeyId);
        requireText("wx.pay.private-key-path", privateKeyPath);
    }

    private static void requireText(String key, String value) {
        if (value == null || value.isBlank() || value.contains("your-")) {
            throw new IllegalStateException(
                "微信支付已启用（wx.pay.enabled=true），但 " + key + " 未正确配置，请检查服务器 .env");
        }
    }

    private static void requireHttps(String key, String value) {
        requireText(key, value);
        if (!value.startsWith("https://")) {
            throw new IllegalStateException(
                "微信支付回调地址必须是 https，当前 " + key + "=" + value);
        }
    }
}
