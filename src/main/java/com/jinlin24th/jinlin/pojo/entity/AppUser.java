package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小程序用户实体类
 * 对应数据库表：app_user
 */
@Data
@TableName("app_user")
public class AppUser {

    /**
     * 用户ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 性别：0-未知，1-男，2-女
     */
    private Integer gender;

    /**
     * 微信openid
     */
    private String openid;

    /**
     * 微信unionid
     */
    private String unionid;

    /**
     * 手机�?     */
    private String phone;

    /**
     * 手机绑定时间
     */
    private LocalDateTime phoneBindTime;

    /**
     * 会员等级ID
     */
    private Long memberLevelId;

    /**
     * 上级推荐人ID（分销用）
     */
    private Long parentUserId;

    /**
     * 当前积分
     */
    private Integer points;

    /**
     * 累计积分
     */
    private Integer totalPoints;

    /**
     * 累计消费金额
     */
    private java.math.BigDecimal totalAmount;

    /**
     * 订单�?     */
    private Integer orderCount;

    /**
     * 是否分销商：0-否，1-是
     */
    private Integer isDistributor;

    /**
     * 分销资格开启时间
     */
    private LocalDateTime distributorEnabledTime;

    /**
     * 分销资格关闭时间
     */
    private LocalDateTime distributorDisabledTime;

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

    /**
     * 最后登录时�?     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastLoginTime;
}
