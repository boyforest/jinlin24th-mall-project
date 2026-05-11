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
}
