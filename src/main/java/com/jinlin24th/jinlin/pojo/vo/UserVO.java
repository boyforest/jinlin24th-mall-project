package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserVO implements Serializable {
    private Long id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
    private Long memberLevelId;
    private String memberLevelName;
    private Integer points;
    private Integer totalPoints;
    private BigDecimal totalAmount;
    private Integer orderCount;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}

