package com.jinlin24th.jinlin.common.config;

import jakarta.annotation.PostConstruct;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.coffee")
public class CoffeePayConfig {
    private boolean orderingEnabled;
    private boolean paymentEnabled;
    private String mchId;
    private String appId;
    private String apiV3Key;
    private String serialNo;
    private String platformPublicKeyId;
    private String privateKeyPath;
    private String platformPublicKeyPath;
    private String notifyUrl;
    private String refundNotifyUrl;
    private String readyTemplateId;
    private String readyPickupField = "character_string1";
    private String readyStoreField = "thing2";
    private String readyTimeField = "time3";

    @PostConstruct
    public void validate() {
        if (orderingEnabled && !paymentEnabled) throw new IllegalStateException("咖啡点单启用时必须配置独立支付");
        if (!paymentEnabled) return;
        for (String value :
                new String[] {
                    mchId,
                    appId,
                    apiV3Key,
                    serialNo,
                    platformPublicKeyId,
                    privateKeyPath,
                    platformPublicKeyPath,
                    notifyUrl,
                    refundNotifyUrl
                })
            if (value == null || value.isBlank() || value.contains("your-"))
                throw new IllegalStateException("独立咖啡支付配置不完整");
        if (apiV3Key.length() != 32
                || !notifyUrl.startsWith("https://")
                || !refundNotifyUrl.startsWith("https://"))
            throw new IllegalStateException("咖啡密钥或回调地址配置无效");
    }
}
