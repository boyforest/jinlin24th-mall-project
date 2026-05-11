package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跟进记录�? */
@Data
@TableName("follow_record")
public class FollowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 跟进人ID
     */
    private Long adminId;

    /**
     * 跟进内容
     */
    private String content;

    /**
     * 下次跟进时间
     */
    private LocalDateTime nextTime;

    /**
     * 方式�?-电话�?-微信�?-上门�?-其他
     */
    private Integer type;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
