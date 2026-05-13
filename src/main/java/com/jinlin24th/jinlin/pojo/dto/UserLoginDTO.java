package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {
    private String code;
    private String encryptedData;
    private String nickname;
    private String avatarUrl;
    private String iv;
    /**
     * 邀请人用户ID。首次登录创建用户时，如果邀请人存在且具备分销资格，则绑定为 parentUserId。
     */
    private Long inviterUserId;
}
