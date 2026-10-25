package com.jinlin24th.jinlin.coffee;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** 计数实际准备执行的 SELECT，防止新增列表退化为逐订单/逐购物车查询。 */
@Intercepts(
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}))
public class CoffeeQueryCounter implements Interceptor {
    private static final ThreadLocal<List<String>> QUERIES =
            ThreadLocal.withInitial(ArrayList::new);

    static void reset() {
        QUERIES.get().clear();
    }

    static int count() {
        return QUERIES.get().size();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String sql =
                ((StatementHandler) invocation.getTarget()).getBoundSql().getSql().stripLeading();
        if (sql.regionMatches(true, 0, "SELECT", 0, 6)) {
            QUERIES.get().add(sql);
        }
        return invocation.proceed();
    }
}
