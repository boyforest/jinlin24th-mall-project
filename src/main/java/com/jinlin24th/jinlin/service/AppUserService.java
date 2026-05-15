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

    IPage<AppUserVO> adminPage(long page, long size, Integer status, Integer isDistributor);

    /**
     * 管理端开启/关闭用户分销资格。
     *
     * @param id 用户ID
     * @param enabled 1-开启，0-关闭
     * @return 更新后的用户信息
     */
    AppUserVO updateDistributor(Long id, Integer enabled);

    /**
     * 获取当前用户已绑定的推荐官。
     */
    AppUserVO getRecommender(Long userId);

    /**
     * C 端绑定推荐官。MVP 规则：用户未绑定时才允许绑定，推荐官必须已开通分销资格。
     */
    AppUserVO bindRecommender(Long userId, Long recommenderUserId);
}
