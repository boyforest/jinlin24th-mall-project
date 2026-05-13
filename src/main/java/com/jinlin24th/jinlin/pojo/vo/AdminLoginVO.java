package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端登录响应。
 * <p>
 * token/username 保持不变，兼容当前 admin-web；角色和权限用于后续菜单与按钮级权限控制。
 */
@Data
public class AdminLoginVO implements Serializable {
    private Long adminId;
    private String token;
    private String username;
    private String realName;
    private List<String> roles;
    private List<String> permissions;
}
