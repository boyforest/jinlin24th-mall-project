package com.jinlin24th.jinlin.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /**
     * 单页最大条数上限。
     * <p>
     * 为什么必须设置：所有列表接口的 {@code size} 都直接来自请求参数且未做校验。
     * MyBatis-Plus 的 {@code PaginationInnerInterceptor} 在 {@code size < 0}
     * **且未配置 maxLimit** 时会跳过整个分页（不构造 LIMIT），
     * 于是 {@code ?size=-1} 能一次拉走全表——既是性能风险，也是数据批量导出风险。
     * 配置 maxLimit 后，负值与超大值都会被钳制到该上限。
     * <p>
     * 取值依据：管理端表格默认每页 10 条、可选档位到 100；小程序列表按 10 条分页。
     * 100 覆盖现有全部正常用法，同时把越界请求挡在合理范围内。
     */
    private static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：必须同时设置 maxLimit，否则 size=-1 / size=超大值可绕过分页
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
