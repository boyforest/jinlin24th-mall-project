package com.jinlin24th.jinlin.common.config;

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
     * 是否沙箱环境
     */
    private Boolean sandbox = false;
}
