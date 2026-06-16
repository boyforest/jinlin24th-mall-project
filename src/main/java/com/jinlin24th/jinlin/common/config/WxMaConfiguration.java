package com.jinlin24th.jinlin.common.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WxMaConfiguration {

    @Value("${wx.miniapp.appid}")
    private String appid;

    @Value("${wx.miniapp.secret}")
    private String secret;

    @Bean
    public WxMaService wxMaService() {
        // 微信小程序 SDK 的基本配置：appid/secret
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(appid);
        config.setSecret(secret);

        // 关键修复：不走系统 SOCKS 代理，直接连接微信 API
        // 否则本地开发环境如果设置了 SOCKS 代理但代理不可用，会抛出"Can't connect to SOCKS proxy"
        config.setHttpProxyHost("");
        config.setHttpProxyPort(0);

        // 生成 WxMaService Bean，后续在 WechatAuthService 中注入使用
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}

