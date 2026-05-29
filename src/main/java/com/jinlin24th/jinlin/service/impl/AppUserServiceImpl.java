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
import java.util.ArrayList;
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
        Map<Long, String> levelNameById = loadLevelNameMap(List.of(user));
        Map<Long, AppUser> parentMap = loadParentUserMap(List.of(user));
        return toVO(user, levelNameById.get(user.getMemberLevelId()), parentMap.get(user.getParentUserId()));
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
            user.setIsDistributor(0);
            user.setParentUserId(resolveValidInviterId(dto.getInviterUserId()));
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
    public AppUserVO updateDistributor(Long id, Integer enabled) {
        // 管理端开关分销资格：只接受 0/1，顺手记录开启/关闭时间，便于后续审计。
        if (id == null || enabled == null || (enabled != 0 && enabled != 1)) {
            return null;
        }
        AppUser user = getById(id);
        if (user == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
            .set(AppUser::getIsDistributor, enabled)
            .set(enabled == 1, AppUser::getDistributorEnabledTime, now)
            .set(enabled == 0, AppUser::getDistributorDisabledTime, now)
            .eq(AppUser::getId, id)
            .update();
        return getUserInfo(id);
    }

    @Override
    public AppUserVO getRecommender(Long userId) {
        AppUser user = getById(userId);
        if (user == null || user.getParentUserId() == null) {
            return null;
        }
        AppUser recommender = lambdaQuery()
            .eq(AppUser::getId, user.getParentUserId())
            .eq(AppUser::getStatus, 1)
            .eq(AppUser::getDeleted, 0)
            .one();
        return recommender == null ? null : toVO(recommender, null, null);
    }

    @Override
    public AppUserVO bindRecommender(Long userId, Long recommenderUserId) {
        if (userId == null || recommenderUserId == null || recommenderUserId <= 0 || userId.equals(recommenderUserId)) {
            return null;
        }
        AppUser user = getById(userId);
        if (user == null || user.getParentUserId() != null) {
            return null;
        }
        Long validRecommenderId = resolveValidInviterId(recommenderUserId);
        if (validRecommenderId == null) {
            return null;
        }
        lambdaUpdate()
            .set(AppUser::getParentUserId, validRecommenderId)
            .eq(AppUser::getId, userId)
            .isNull(AppUser::getParentUserId)
            .update();
        return getRecommender(userId);
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
            .map(u -> toVO(u, levelNameById.get(u.getMemberLevelId()), null))
            .collect(Collectors.toList());
    }

    @Override
    public IPage<AppUserVO> adminPage(long page, long size, Integer status, Integer isDistributor, String keyword) {
        // 管理端分页：支持按 status / isDistributor / 昵称手机号模糊筛选
        Page<AppUser> p = new Page<>(page, size);
        IPage<AppUser> entityPage = lambdaQuery()
            .eq(status != null, AppUser::getStatus, status)
            .eq(isDistributor != null, AppUser::getIsDistributor, isDistributor)
            .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                .like(AppUser::getNickname, keyword)
                .or()
                .like(AppUser::getPhone, keyword))
            .orderByDesc(AppUser::getId)
            .page(p);

        List<AppUser> records = entityPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        }

        Map<Long, String> levelNameById = loadLevelNameMap(records);
        Map<Long, AppUser> parentMap = loadParentUserMap(records);

        Page<AppUserVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records.stream()
            .map(u -> toVO(u, levelNameById.get(u.getMemberLevelId()), parentMap.get(u.getParentUserId())))
            .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 校验邀请人是否具备分销资格。
     * <p>
     * 只有邀请人存在、未被逻辑删除、状态正常且 isDistributor=1 时，才允许绑定 parentUserId。
     */
    private Long resolveValidInviterId(Long inviterUserId) {
        if (inviterUserId == null || inviterUserId <= 0) {
            return null;
        }
        AppUser inviter = lambdaQuery()
            .eq(AppUser::getId, inviterUserId)
            .eq(AppUser::getStatus, 1)
            .eq(AppUser::getDeleted, 0)
            .eq(AppUser::getIsDistributor, 1)
            .one();
        return inviter == null ? null : inviter.getId();
    }

    private Map<Long, String> loadLevelNameMap(List<AppUser> users) {
        Set<Long> memberLevelIds = users.stream()
            .map(AppUser::getMemberLevelId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .collect(Collectors.toSet());

        return memberLevelIds.isEmpty()
            ? Collections.emptyMap()
            : memberLevelMapper.selectBatchIds(memberLevelIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MemberLevel::getId, MemberLevel::getName, (a, b) -> a));
    }

    private Map<Long, AppUser> loadParentUserMap(List<AppUser> users) {
        Set<Long> parentIds = users.stream()
            .map(AppUser::getParentUserId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return listByIds(new ArrayList<>(parentIds)).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(AppUser::getId, parent -> parent, (a, b) -> a));
    }

    private AppUserVO toVO(AppUser user, String memberLevelName, AppUser parentUser) {
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
        vo.setParentUserId(user.getParentUserId());
        vo.setParentUserNickname(parentUser == null ? null : parentUser.getNickname());
        vo.setParentUserPhone(parentUser == null ? null : parentUser.getPhone());
        vo.setStatus(user.getStatus());
        vo.setIsDistributor(user.getIsDistributor());
        vo.setDistributorEnabledTime(user.getDistributorEnabledTime());
        vo.setDistributorDisabledTime(user.getDistributorDisabledTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

