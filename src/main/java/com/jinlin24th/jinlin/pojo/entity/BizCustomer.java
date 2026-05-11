package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家客户�? */
@Data
@TableName("biz_customer")
public class BizCustomer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户名称/公司�?     */
    private String name;

    /**
     * 联系人姓�?     */
    private String contactName;

    /**
     * 联系人电�?     */
    private String contactPhone;

    /**
     * 来源�?-小程序注册，2-销售录入，3-转介�?     */
    private Integer source;

    /**
     * 客户等级�?-普通，2-重要�?-VIP
     */
    private Integer level;

    /**
     * 绑定的销售ID
     */
    private Long adminId;

    /**
     * 累计消费金额
     */
    private BigDecimal totalAmount;

    /**
     * 累计订单�?     */
    private Integer orderCount;

    /**
     * 标签，逗号分隔
     */
    private String tags;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态：1-正常�?-禁用
     */
    private Integer status;

    /**
     * 逻辑删除�?-未删除，1-已删�?     */
    @TableLogic
    private Integer deleted;

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

