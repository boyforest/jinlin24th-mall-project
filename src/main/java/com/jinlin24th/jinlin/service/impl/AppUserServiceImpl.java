package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.mapper.AppUserMapper;
import com.jinlin24th.jinlin.mapper.MemberLevelMapper;
import com.jinlin24th.jinlin.pojo.dto.UserLoginDTO;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.MemberLevel;
import com.jinlin24th.jinlin.pojo.vo.AppUserVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.WechatAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {

    @Autowired
    private MemberLevelMapper memberLevelMapper;

    @Autowired
    private WechatAuthService wechatAuthService;

    @Override
    public AppUserVO getUserInfo(Long id) {
        // 用户信息查询：补充会员等级名称（member_level 表）
        AppUser user = getById(id);
        if (user == null) {
            return null;
        }
        String memberLevelName = null;
        Long memberLevelId = user.getMemberLevelId();
        if (memberLevelId != null && memberLevelId > 0) {
            MemberLevel level = memberLevelMapper.selectById(memberLevelId);
            if (level != null) {
                memberLevelName = level.getName();
            }
        }
        return toVO(user, memberLevelName);
    }

    @Override
    public AppUserVO login(UserLoginDTO dto) {
        // 小程序登录
        // 1) 前端传 code（wx.login 得到）
        // 2) 后端 code2session 换取 openid（WechatAuthService）
        // 3) 按 openid 查用户，不存在则创建，存在则更新基础资料与最后登录时间
        String code = dto == null ? null : dto.getCode();
        if (code == null || code.isBlank()) {
            return null;
        }

        String openid = wechatAuthService.getOpenidByCode(code);
        //“：：”指向数据库字段
        AppUser user = lambdaQuery().eq(AppUser::getOpenid, openid).one();
        if (user == null) {
            // 首次登录：创建用户
            user = new AppUser();
            user.setOpenid(openid);
            user.setNickname(dto.getNickname());
            user.setAvatar(dto.getAvatarUrl());
            user.setGender(0);
            user.setStatus(1);
            user.setLastLoginTime(LocalDateTime.now());
            save(user);
        } else {
            // 非首次：按需更新昵称/头像 + 更新最后登录时间
            boolean changed = false;
            if (dto.getNickname() != null && !dto.getNickname().isBlank() && !Objects.equals(dto.getNickname(), user.getNickname())) {
                user.setNickname(dto.getNickname());
                changed = true;
            }
            if (dto.getAvatarUrl() != null && !dto.getAvatarUrl().isBlank() && !Objects.equals(dto.getAvatarUrl(), user.getAvatar())) {
                user.setAvatar(dto.getAvatarUrl());
                changed = true;
            }
            user.setLastLoginTime(LocalDateTime.now());
            changed = true;
            if (changed) {
                updateById(user);
            }
        }
        return getUserInfo(user.getId());
    }

    @Override
    public AppUserVO updateStatus(Long id, Integer status) {
        // 管理端修改用户状态：例如禁用/启用
        lambdaUpdate().set(AppUser::getStatus, status).eq(AppUser::getId, id).update();
        return getUserInfo(id);
    }

    @Override
    public List<AppUserVO> getUserList() {
        // 管理端列表（不分页）：批量查会员等级，避免 N+1
        List<AppUser> users = list();
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> memberLevelIds = users.stream()
            .map(AppUser::getMemberLevelId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .collect(Collectors.toSet());

        Map<Long, String> levelNameById = memberLevelIds.isEmpty()
            ? Collections.emptyMap()
            : memberLevelMapper.selectBatchIds(memberLevelIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MemberLevel::getId, MemberLevel::getName, (a, b) -> a));

        return users.stream()
            .map(u -> toVO(u, levelNameById.get(u.getMemberLevelId())))
            .collect(Collectors.toList());
    }

    @Override
    public IPage<AppUserVO> adminPage(long page, long size, Integer status) {
        // 管理端分页：支持按 status 筛选
        Page<AppUser> p = new Page<>(page, size);
        IPage<AppUser> entityPage = lambdaQuery()
            .eq(status != null, AppUser::getStatus, status)
            .orderByDesc(AppUser::getId)
            .page(p);

        List<AppUser> records = entityPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        }

        Set<Long> memberLevelIds = records.stream()
            .map(AppUser::getMemberLevelId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .collect(Collectors.toSet());

        Map<Long, String> levelNameById = memberLevelIds.isEmpty()
            ? Collections.emptyMap()
            : memberLevelMapper.selectBatchIds(memberLevelIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MemberLevel::getId, MemberLevel::getName, (a, b) -> a));

        Page<AppUserVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records.stream()
            .map(u -> toVO(u, levelNameById.get(u.getMemberLevelId())))
            .collect(Collectors.toList()));
        return voPage;
    }

    private AppUserVO toVO(AppUser user, String memberLevelName) {
        // 统一 VO 转换，避免 Controller/Service 到处手写映射
        AppUserVO vo = new AppUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setPhone(user.getPhone());
        vo.setMemberLevelId(user.getMemberLevelId());
        vo.setMemberLevelName(memberLevelName);
        vo.setPoints(user.getPoints());
        vo.setTotalAmount(user.getTotalAmount());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

