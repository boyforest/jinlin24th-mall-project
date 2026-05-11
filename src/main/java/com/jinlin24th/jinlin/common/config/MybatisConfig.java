package com.jinlin24th.jinlin.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.jinlin24th.jinlin.mapper")
public class MybatisConfig {
}

