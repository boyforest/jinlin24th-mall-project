package com.jinlin24th.jinlin.pojo.vo;

import java.util.List;

public record BusinessOrderPageVO(
        List<BusinessOrderSummaryVO> records, long total, long size, long current, long pages) {}
