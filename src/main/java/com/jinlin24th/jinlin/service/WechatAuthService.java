package com.jinlin24th.jinlin.service;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.jinlin24th.jinlin.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Slf4j
public class WechatAuthService {

    @Autowired
    private WxMaService wxMaService;

    @Autowired
    private Environment environment;

    @Value("${wx.miniapp.appid:}")
    private String appid;

    @Value("${wx.miniapp.secret:}")
    private String secret;

    /**
     * 通过 code 换取 openid 和 session_key
     * @param code 微信登录临时凭证
     * @return openid
     */
    public String getOpenidByCode(String code) {
        if (isWechatConfigBlank()) {
            if (isDevProfile()) {
                log.warn("微信小程序 appid/secret 未配置，dev 环境使用 mock openid 登录");
                return "mock_openid_dev";
            }
            throw BizException.badRequest("微信小程序 appid/secret 未配置，请先配置 WX_MINIAPP_APPID 和 WX_MINIAPP_SECRET");
        }
        try {
            // code2session：微信官方流程，用 code 换取 openid/session_key
            WxMaJscode2SessionResult result = wxMaService.getUserService().getSessionInfo(code);
            log.info("微信登录成功，openid: {}", result.getOpenid());
            return result.getOpenid();
        } catch (WxErrorException e) {
            log.error("微信登录失败，error: {}", e.getError());
            throw BizException.badRequest("微信登录失败：" + e.getError().getErrorMsg());
        }
    }

    private boolean isWechatConfigBlank() {
        return isPlaceholder(appid) || isPlaceholder(secret);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPlaceholder(String value) {
        return isBlank(value)
            || value.contains("your-")
            || value.contains("你的小程序");
    }
}
