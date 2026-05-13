package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.AdminLoginDTO;
import com.jinlin24th.jinlin.pojo.entity.SysAdmin;
import com.jinlin24th.jinlin.pojo.vo.AdminLoginVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 系统管理员服务。
 */
public interface SysAdminService extends IService<SysAdmin> {

    /**
     * 管理端账号密码登录。
     *
     * @param dto 登录参数
     * @param request 当前请求，用于记录登录 IP
     * @return 登录 token 和权限信息
     */
    AdminLoginVO login(AdminLoginDTO dto, HttpServletRequest request);

    /**
     * 查询管理员角色编码列表。
     */
    List<String> getRoleCodes(Long adminId);

    /**
     * 查询管理员权限编码列表。
     */
    List<String> getPermissionCodes(Long adminId);
}
