package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

/**
 * 微信小程序拉起支付所需参数。
 */
@Data
public class WxPayParamsVO {

    private String appId;

    private String timeStamp;

    private String nonceStr;

    private String packageValue;

    private String signType;

    private String paySign;

    private String prepayId;
}
