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
 * 分销全局配置�? */
@Data
@TableName("distribution_config")
public class DistributionConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 一级佣金比例，�?0表示10%
     */
    private Integer level1Rate;

    /**
     * 二级佣金比例，如5表示5%
     */
    private Integer level2Rate;

    /**
     * 最低提现金�?     */
    private BigDecimal minWithdraw;

    /**
     * 订单完成后几天可结算
     */
    private Integer settleDays;

    /**
     * 状态：1-启用分销�?-关闭分销
     */
    private Integer status;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

