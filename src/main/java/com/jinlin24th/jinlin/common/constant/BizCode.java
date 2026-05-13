package com.jinlin24th.jinlin.common.constant;

import lombok.Getter;

@Getter
public enum BizCode {
    /*
     * 约定：
     * - HTTP 状态码只表达传输层语义，由 httpStatus 决定。
     * - code 是前后端约定的业务码，成功固定为 0。
     * - 通用错误使用 4xxxx / 5xxxx。
     * - 业务域错误按模块分段：1商品、2用户/客户、3订单、4仓储、5优惠券、6跟进、7分销、8购物车。
     */
    SUCCESS(0, HttpStatus.OK, "success"),

    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数错误"),
    PARAM_INVALID(40001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    METHOD_NOT_ALLOWED(40005, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(40015, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    RATE_LIMITED(40029, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),

    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权限"),

    SYSTEM_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后再试"),
    AUTH_SESSION_WRITE_FAILED(50010, HttpStatus.INTERNAL_SERVER_ERROR, "登录态写入失败（Redis）"),
    REDIS_ERROR(50011, HttpStatus.INTERNAL_SERVER_ERROR, "缓存服务异常"),
    WECHAT_PAY_ERROR(50020, HttpStatus.INTERNAL_SERVER_ERROR, "微信支付服务异常"),

    PRODUCT_NOT_FOUND(10404, HttpStatus.NOT_FOUND, "商品不存在"),
    PRODUCT_CATEGORY_NOT_FOUND(10405, HttpStatus.NOT_FOUND, "分类不存在"),
    SKU_NOT_FOUND(10406, HttpStatus.NOT_FOUND, "SKU不存在"),

    USER_NOT_FOUND(20404, HttpStatus.NOT_FOUND, "用户不存在"),
    CUSTOMER_NOT_FOUND(20405, HttpStatus.NOT_FOUND, "客户不存在"),
    BIZ_CUSTOMER_NOT_FOUND(20406, HttpStatus.NOT_FOUND, "不存在此商户，请重新输入"),

    ORDER_NOT_FOUND(30404, HttpStatus.NOT_FOUND, "订单不存在"),
    ORDER_CREATE_FAILED(30400, HttpStatus.BAD_REQUEST, "创建订单失败"),

    WAREHOUSE_NOT_FOUND(40404, HttpStatus.NOT_FOUND, "仓库不存在"),
    INVENTORY_NOT_FOUND(40405, HttpStatus.NOT_FOUND, "库存记录不存在"),
    INVENTORY_LOG_NOT_FOUND(40406, HttpStatus.NOT_FOUND, "库存流水不存在"),

    COUPON_NOT_FOUND(50404, HttpStatus.NOT_FOUND, "优惠券不存在"),
    FOLLOW_RECORD_NOT_FOUND(60404, HttpStatus.NOT_FOUND, "跟进记录不存在"),
    DISTRIBUTION_NOT_FOUND(70404, HttpStatus.NOT_FOUND, "分销记录不存在"),
    CART_RECORD_NOT_FOUND(80404, HttpStatus.NOT_FOUND, "购物车记录不存在"),

    CART_ADD_FAILED(80400, HttpStatus.BAD_REQUEST, "加入购物车失败"),
    SALES_REQUIRED(20400, HttpStatus.BAD_REQUEST, "请指定销售人员"),
    DISTRIBUTION_SETTLE_FAILED(70400, HttpStatus.BAD_REQUEST, "结算失败");

    private final int code;
    private final int httpStatus;
    private final String message;

    BizCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
