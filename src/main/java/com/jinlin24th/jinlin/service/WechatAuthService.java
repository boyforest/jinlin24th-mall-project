package com.jinlin24th.jinlin.service;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.jinlin24th.jinlin.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WechatAuthService {

    @Autowired
    private WxMaService wxMaService;

    /**
     * 通过 code 换取 openid 和 session_key
     * @param code 微信登录临时凭证
     * @return openid
     */
    public String getOpenidByCode(String code) {
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
}
