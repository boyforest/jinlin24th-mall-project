package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员修改密码请求。
 * <p>
 * 用于首次登录强制改密场景及后台自主改密。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminPasswordDTO implements Serializable {
    private String oldPassword;
    private String newPassword;
}
