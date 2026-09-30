package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 佣金导出 CSV 的安全与容量测试。
 * <p>
 * 修复的两个缺陷：
 * <ol>
 *   <li><b>公式注入</b>：昵称/备注等字段由用户可控，若以 {@code =}、{@code +}、{@code -}、
 *       {@code @} 开头，Excel / WPS 打开时会当作公式执行（可外联请求、可执行命令）。
 *       仅做引号/逗号转义是不够的，必须在值前加单引号中和。</li>
 *   <li><b>无上限导出</b>：原实现用 {@code list()} 一次加载全部匹配记录，
 *       财务点一次"导出"就可能把整张表拉进内存。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionCsvExportTest {

    @Mock
    private AppUserService appUserService;
    @Mock
    private DistributionConfigService distributionConfigService;
    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private DistributionMapper distributionMapper;

    private DistributionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new Configuration(), ""), Distribution.class);
        service = new DistributionServiceImpl(appUserService, distributionConfigService, orderMasterService);
        ReflectionTestUtils.setField(service, "baseMapper", distributionMapper);
    }

    /** 构造一条佣金记录，买家昵称可控（模拟恶意昵称）。 */
    private Distribution distribution(Long id, String buyerNickname, String remark) {
        Distribution d = new Distribution();
        d.setId(id);
        d.setOrderNo("JL" + id);
        d.setBuyerUserId(1000L + id);
        d.setBuyerAmount(new BigDecimal("100.00"));
        d.setLevel1UserId(2000L + id);
        d.setLevel1Rate(10);
        d.setLevel1Amount(new BigDecimal("10.00"));
        d.setLevel2Amount(BigDecimal.ZERO);
        d.setStatus(0);
        d.setRemark(remark);
        d.setCreateTime(LocalDateTime.now());
        return d;
    }

    private AppUser user(Long id, String nickname) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setNickname(nickname);
        u.setPhone("13800138000");
        return u;
    }

    private String export(List<Distribution> records, List<AppUser> users) throws Exception {
        when(distributionMapper.selectList(any())).thenReturn(records);
        when(appUserService.listByIds(any())).thenReturn(users);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportCsv(response, null, null, null);
        return response.getContentAsString();
    }

    @Test
    @DisplayName("以 = 开头的昵称必须被中和，不能在 Excel 里当公式执行")
    void formulaWithEqualsMustBeNeutralized() throws Exception {
        String payload = "=cmd|'/c calc'!A1";
        String csv = export(
            List.of(distribution(1L, payload, null)),
            List.of(user(1001L, payload), user(2001L, null)));

        assertFalse(csv.contains("," + payload),
            "危险前缀未被中和，Excel 打开即执行公式；CSV 片段：" + firstDataLine(csv));
        assertTrue(csv.contains("'" + payload) || csv.contains("\"'" + payload),
            "应以单引号前缀中和公式：" + firstDataLine(csv));
    }

    @Test
    @DisplayName("+ - @ 开头的昵称同样必须被中和")
    void otherFormulaPrefixesMustBeNeutralized() throws Exception {
        for (String payload : List.of("+1+1", "-1-1", "@SUM(A1:A9)")) {
            String csv = export(
                List.of(distribution(2L, payload, null)),
                List.of(user(1002L, payload), user(2002L, null)));

            assertTrue(csv.contains("'" + payload) || csv.contains("\"'" + payload),
                "前缀 " + payload.charAt(0) + " 未被中和：" + firstDataLine(csv));
        }
    }

    @Test
    @DisplayName("正常昵称不得被改动（不能为了安全把所有值都加前缀）")
    void normalNicknameMustRemainUntouched() throws Exception {
        String csv = export(
            List.of(distribution(3L, "张三", null)),
            List.of(user(1003L, "张三"), user(2003L, null)));

        assertTrue(csv.contains("张三"), "正常昵称应原样导出");
        assertFalse(csv.contains("'张三"), "正常昵称不应被加前缀");
    }

    @Test
    @DisplayName("逗号、双引号、换行仍需正确转义（不能因公式修复而破坏 CSV 结构）")
    void csvStructureEscapingMustStillWork() throws Exception {
        String nickname = "李,四\"x\"\n新行";
        String csv = export(
            List.of(distribution(4L, nickname, null)),
            List.of(user(1004L, nickname), user(2004L, null)));

        // 该字段必须被双引号包裹，内部双引号翻倍
        assertTrue(csv.contains("\"李,四\"\"x\"\"\n新行\""),
            "含逗号/引号/换行的字段必须按 CSV 规则转义：" + firstDataLine(csv));
    }

    @Test
    @DisplayName("备注字段（用户可控）同样要中和公式")
    void remarkMustAlsoBeNeutralized() throws Exception {
        String csv = export(
            List.of(distribution(5L, "正常昵称", "=HYPERLINK(\"http://evil\")")),
            List.of(user(1005L, "正常昵称"), user(2005L, null)));

        assertFalse(csv.contains(",=HYPERLINK"),
            "备注中的公式未被中和：" + firstDataLine(csv));
    }

    @Test
    @DisplayName("超过导出上限时必须截断，且要在 CSV 里留下显眼的提示行")
    void oversizedExportMustBeTruncatedWithVisibleWarning() throws Exception {
        ReflectionTestUtils.setField(service, "maxExportRows", 5);

        // 模拟库里还有更多数据：查询会多取 1 条用于判断截断（LIMIT 6）
        List<Distribution> records = new ArrayList<>();
        List<AppUser> users = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            records.add(distribution(i, "用户" + i, null));
            users.add(user(1000L + i, "用户" + i));
            users.add(user(2000L + i, "上线" + i));
        }

        String csv = export(records, users);

        // 达到上限 → 必须给出提示行，否则财务会在不知情的情况下少发佣金
        assertTrue(csv.contains("导出已达上限") || csv.contains("请缩小筛选范围"),
            "达到上限时必须在 CSV 中留下可见提示，避免财务按不完整名单打款");
        assertTrue(csv.lines().anyMatch(line -> line.startsWith("#")),
            "提示行应以 # 开头以示非数据行");

        // 数据行必须正好等于上限（探针那一条不能被写进文件）
        long dataRows = csv.lines()
            .filter(line -> line.startsWith("JL"))
            .count();
        assertEquals(5, dataRows, "数据行数应正好等于上限 5，实际 CSV：\n" + csv);
    }

    @Test
    @DisplayName("未达上限时不得出现提示行")
    void noWarningWhenUnderLimit() throws Exception {
        ReflectionTestUtils.setField(service, "maxExportRows", 100);

        String csv = export(
            List.of(distribution(6L, "张三", null)),
            List.of(user(1006L, "张三"), user(2006L, null)));

        assertFalse(csv.contains("导出已达上限"), "未达上限不应出现截断提示");
    }

    private String firstDataLine(String csv) {
        String[] lines = csv.split("\n");
        return lines.length > 1 ? lines[1] : csv;
    }
}
