package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.UserLoginDTO;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.vo.AppUserVO;

import java.util.List;

public interface AppUserService extends IService<AppUser> {

    AppUserVO getUserInfo(Long id);

    AppUserVO login(UserLoginDTO dto);

    AppUserVO updateStatus(Long id, Integer status);

    List<AppUserVO> getUserList();

    IPage<AppUserVO> adminPage(long page, long size, Integer status);
}
