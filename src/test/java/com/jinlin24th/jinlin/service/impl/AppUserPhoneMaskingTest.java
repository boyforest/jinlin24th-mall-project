package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.AppUserMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.vo.AppUserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 推荐官手机号在 C 端的脱敏测试。
 * <p>
 * 修复的缺陷：小程序把推荐官的**完整手机号**展示给下游用户，有两条泄漏路径：
 * <ol>
 *   <li>{@code /user/appUser/me} 的 {@code parentUserPhone} 字段；</li>
 *   <li>{@code /user/appUser/recommender} 的 {@code phone} 字段——
 *       该接口把推荐官当作 VO 主体返回，所以完整号码落在了 {@code phone} 上。</li>
 * </ol>
 * 这两处都是"别人的手机号"，下游用户无权获得完整值。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppUserPhoneMaskingTest {

    private static final Long USER_ID = 1L;
    private static final Long RECOMMENDER_ID = 2L;
    private static final String RECOMMENDER_PHONE = "13800138000";

    @Mock
    private AppUserMapper appUserMapper;

    private AppUserServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppUser.class);
        service = new AppUserServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", appUserMapper);
    }

    private AppUser user(Long id, String nickname, String phone, Long parentUserId) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setNickname(nickname);
        u.setPhone(phone);
        u.setParentUserId(parentUserId);
        u.setStatus(1);
        u.setDeleted(0);
        u.setPoints(0);
        return u;
    }

    /**
     * 按实际查询方法打桩：
     * - getById → selectById
     * - loadParentUserMap → listByIds → selectBatchIds
     * - getRecommender / resolveValidInviterId → lambdaQuery().one() → selectOne
     */
    private void stubUsers() {
        AppUser buyer = user(USER_ID, "买家", "13900139000", RECOMMENDER_ID);
        AppUser recommender = user(RECOMMENDER_ID, "推荐官", RECOMMENDER_PHONE, null);
        when(appUserMapper.selectById(USER_ID)).thenReturn(buyer);
        when(appUserMapper.selectById(RECOMMENDER_ID)).thenReturn(recommender);
        when(appUserMapper.selectBatchIds(anyCollection())).thenReturn(java.util.List.of(recommender));
        when(appUserMapper.selectOne(any())).thenReturn(recommender);
    }

    private void stubRecommenderPhone(String phone) {
        AppUser buyer = user(USER_ID, "买家", "13900139000", RECOMMENDER_ID);
        AppUser recommender = user(RECOMMENDER_ID, "推荐官", phone, null);
        when(appUserMapper.selectById(USER_ID)).thenReturn(buyer);
        when(appUserMapper.selectBatchIds(anyCollection())).thenReturn(java.util.List.of(recommender));
        when(appUserMapper.selectOne(any())).thenReturn(recommender);
    }

    @Test
    @DisplayName("/me 的 parentUserPhone 必须是脱敏值（推荐官是别人）")
    void parentUserPhoneMustBeMasked() {
        stubUsers();

        AppUserVO vo = service.getUserInfo(USER_ID);

        assertNotNull(vo);
        assertEquals("138****8000", vo.getParentUserPhone(),
            "下游用户不应看到推荐官的完整手机号");
    }

    @Test
    @DisplayName("/me 的自己的 phone 保持完整（那是用户本人的号码）")
    void ownPhoneMustStayUnmasked() {
        stubUsers();

        AppUserVO vo = service.getUserInfo(USER_ID);

        assertEquals("13900139000", vo.getPhone(),
            "脱敏只针对他人手机号，不能把用户自己的号码也改掉");
    }

    @Test
    @DisplayName("/recommender 返回的 phone 必须脱敏（该接口把推荐官当主体）")
    void recommenderEndpointPhoneMustBeMasked() {
        stubUsers();

        AppUserVO vo = service.getRecommender(USER_ID);

        assertNotNull(vo);
        assertEquals("138****8000", vo.getPhone(),
            "这是第二条泄漏路径：推荐官作为 VO 主体，完整号码落在 phone 字段");
    }

    @Test
    @DisplayName("没有推荐官时返回 null，不抛异常")
    void shouldReturnNullWhenNoRecommender() {
        AppUser noParent = user(USER_ID, "买家", "13900139000", null);
        when(appUserMapper.selectById(USER_ID)).thenReturn(noParent);

        assertNull(service.getRecommender(USER_ID));
    }

    @Test
    @DisplayName("推荐官手机号为空时不得因脱敏而抛异常")
    void shouldTolerateBlankRecommenderPhone() {
        stubRecommenderPhone(null);

        AppUserVO vo = service.getRecommender(USER_ID);

        assertNotNull(vo);
        assertNull(vo.getPhone());
    }

    @Test
    @DisplayName("再次脱敏不得让结果变得更少遮蔽（幂等）")
    void maskingMustBeIdempotent() {
        stubRecommenderPhone("138****8000");

        AppUserVO vo = service.getRecommender(USER_ID);

        assertNotNull(vo);
        // "138****8000" 恰好也是 11 字符，因此走标准分支、结果不变。
        // 关键不变量：脱敏值再脱敏一次不会反而暴露更多信息。
        assertEquals("138****8000", vo.getPhone());
        org.junit.jupiter.api.Assertions.assertFalse(vo.getPhone().contains("0013"),
            "中间的识别段必须保持被遮蔽");
    }
}
