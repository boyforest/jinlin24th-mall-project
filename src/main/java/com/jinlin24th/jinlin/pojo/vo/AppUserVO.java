package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AppUserVO implements Serializable {
    private Long id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
    private Long memberLevelId;
    private String memberLevelName;
    private Integer points;
    private java.math.BigDecimal totalAmount;
    private Long parentUserId;
    private String parentUserNickname;
    private String parentUserPhone;
    private Integer status;
    private Integer isDistributor;
    private LocalDateTime distributorEnabledTime;
    private LocalDateTime distributorDisabledTime;
    private LocalDateTime createTime;
    private String token;
}
