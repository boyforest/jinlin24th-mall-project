package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录表
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付流水号
     */
    private String paymentNo;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 实付金额
     */
    private BigDecimal payAmount;

    /**
     * 支付类型：1-微信支付
     */
    private Integer payType;

    /**
     * 交易类型：JSAPI、NATIVE、APP等
     */
    private String tradeType;

    /**
     * 微信支付订单号
     */
    private String transactionId;

    /**
     * 微信预支付ID
     */
    private String prepayId;

    /**
     * 支付状态：0-待支付，1-支付成功，2-支付失败，3-已退款，4-退款失败
     */
    private Integer status;

    /**
     * 支付成功时间
     */
    private LocalDateTime payTime;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款状态：0-未退款，1-退款中，2-退款成功，3-退款失败
     */
    private Integer refundStatus;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 退款订单号
     */
    private String refundId;

    /**
     * 支付/退款原因
     */
    private String reason;

    /**
     * 回调通知时间
     */
    private LocalDateTime notifyTime;

    /**
     * 扩展信息(JSON格式)
     */
    private String extendInfo;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
