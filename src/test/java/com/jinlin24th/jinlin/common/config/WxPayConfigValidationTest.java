package com.jinlin24th.jinlin.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信支付配置的 fail-fast 契约测试。
 * <p>
 * 背景：docker-compose 中 {@code WX_PAY_NOTIFY_URL: ${WX_PAY_NOTIFY_URL:-}} 会把未配置的
 * 变量传成空字符串，从而覆盖 Spring 侧的默认值。若不拦截，就会出现"支付成功但商户
 * 收不到回调、订单永远停在待付款"的隐性故障，排查成本极高。
 * <p>
 * 约定：{@code wx.pay.enabled=true} 时配置不全必须**启动即失败**，
 * 而不是运行到第一笔支付才暴露。
 * <p>
 * 直接单测 {@link WxPayConfig#validate()}：它是公开方法，就是本契约的入口。
 * 不用 Spring 上下文是因为 {@code WxPayClient} 构造期会真实加载私钥文件，
 * 引入上下文只会让测试变脆而不增加覆盖。
 */
class WxPayConfigValidationTest {

    private WxPayConfig validConfig() {
        WxPayConfig config = new WxPayConfig();
        config.setMchId("1900000109");
        config.setApiV3Key("0123456789abcdef0123456789abcdef");
        config.setSerialNo("MERCHANT-SERIAL-AAA");
        config.setPlatformPublicKeyId("PLATFORM-ID-BBB");
        config.setPrivateKeyPath("/app/certs/apiclient_key.pem");
        config.setCertificatePath("/app/certs/pub_key.pem");
        config.setNotifyUrl("https://api.example.com/api/payment/notify");
        config.setRefundNotifyUrl("https://api.example.com/api/payment/refund/notify");
        return config;
    }

    @Test
    @DisplayName("配置完整时校验通过（防止校验规则过严导致正常部署起不来）")
    void shouldPassWithCompleteConfig() {
        assertDoesNotThrow(() -> validConfig().validate());
    }

    @Test
    @DisplayName("回调地址为空时必须启动失败（docker-compose 空字符串覆盖默认值的回归）")
    void shouldRejectBlankNotifyUrl() {
        WxPayConfig config = validConfig();
        config.setNotifyUrl(""); // 模拟被空环境变量覆盖

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("wx.pay.notify-url"),
            "错误信息必须指出是哪个配置项，否则线上排查困难：" + ex.getMessage());
    }

    @Test
    @DisplayName("退款回调地址为空时必须启动失败")
    void shouldRejectBlankRefundNotifyUrl() {
        WxPayConfig config = validConfig();
        config.setRefundNotifyUrl(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("wx.pay.refund-notify-url"), ex.getMessage());
    }

    @Test
    @DisplayName("回调地址不是 https 时必须启动失败")
    void shouldRejectNonHttpsNotifyUrl() {
        WxPayConfig config = validConfig();
        config.setNotifyUrl("http://api.example.com/api/payment/notify");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("https"), ex.getMessage());
    }

    @Test
    @DisplayName("模板占位符未替换时必须启动失败")
    void shouldRejectUnreplacedPlaceholders() {
        WxPayConfig config = validConfig();
        config.setMchId("your-mch-id");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validate);
        assertTrue(ex.getMessage().contains("wx.pay.mch-id"), ex.getMessage());
    }

    @Test
    @DisplayName("商户证书序列号与平台公钥 ID 都必须配置（两者混用是历史缺陷）")
    void shouldRequireBothSerialNoAndPlatformPublicKeyId() {
        WxPayConfig missingSerial = validConfig();
        missingSerial.setSerialNo("  ");
        assertThrows(IllegalStateException.class, missingSerial::validate);

        WxPayConfig missingPlatformKey = validConfig();
        missingPlatformKey.setPlatformPublicKeyId(null);
        assertThrows(IllegalStateException.class, missingPlatformKey::validate);
    }

    @Test
    @DisplayName("APIv3 密钥与私钥路径缺失时必须启动失败")
    void shouldRequireApiV3KeyAndPrivateKeyPath() {
        WxPayConfig missingKey = validConfig();
        missingKey.setApiV3Key("");
        assertThrows(IllegalStateException.class, missingKey::validate);

        WxPayConfig missingPath = validConfig();
        missingPath.setPrivateKeyPath(null);
        assertThrows(IllegalStateException.class, missingPath::validate);
    }
}
