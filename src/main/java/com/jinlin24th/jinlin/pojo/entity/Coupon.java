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
 * 优惠券表
 */
@Data
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券名�?     */
    private String name;

    /**
     * 类型�?-满减券，2-折扣券，3-固定金额�?     */
    private Integer type;

    /**
     * 使用门槛金额
     */
    private BigDecimal minAmount;

    /**
     * 优惠�?     */
    private BigDecimal discountValue;

    /**
     * 发放总量
     */
    private Integer stock;

    /**
     * 已领取数�?     */
    private Integer receivedCount;

    /**
     * 已使用数�?     */
    private Integer usedCount;

    /**
     * 生效时间
     */
    private LocalDateTime startTime;

    /**
     * 过期时间
     */
    private LocalDateTime endTime;

    /**
     * 限会员等级，NULL=不限
     */
    private Long memberLevelId;

    /**
     * 状态：1-启用�?-禁用
     */
    private Integer status;

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

