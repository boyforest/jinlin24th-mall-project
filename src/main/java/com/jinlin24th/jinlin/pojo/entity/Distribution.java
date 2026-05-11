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
 * 分销佣金记录�? */
@Data
@TableName("distribution")
public class Distribution {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 下单人ID
     */
    private Long buyerUserId;

    /**
     * 订单金额
     */
    private BigDecimal buyerAmount;

    /**
     * 一级上级ID
     */
    private Long level1UserId;

    /**
     * 一级佣金比例（快照�?     */
    private Integer level1Rate;

    /**
     * 一级佣金金�?     */
    private BigDecimal level1Amount;

    /**
     * 二级上级ID
     */
    private Long level2UserId;

    /**
     * 二级佣金比例（快照）
     */
    private Integer level2Rate;

    /**
     * 二级佣金金额
     */
    private BigDecimal level2Amount;

    /**
     * 状态：0-待结算，1-可结算，2-已结算，3-已退�?     */
    private Integer status;

    /**
     * 结算时间
     */
    private LocalDateTime settleTime;

    /**
     * 备注
     */
    private String remark;

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

