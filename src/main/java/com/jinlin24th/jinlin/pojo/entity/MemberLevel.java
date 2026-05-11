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
 * 会员等级�? */
@Data
@TableName("member_level")
public class MemberLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 等级名称，如：青铜、白银、黄�?     */
    private String name;

    /**
     * 升级所需最低累计积�?     */
    private Integer minPoints;

    /**
     * 折扣率，1.00=不打折，0.90=9�?     */
    private BigDecimal discount;

    /**
     * 等级图标
     */
    private String icon;

    /**
     * 排序，越小越靠前
     */
    private Integer sort;

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

