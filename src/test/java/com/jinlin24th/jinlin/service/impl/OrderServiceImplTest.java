package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.service.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 订单用户快照加载：无推荐官（推荐官ID为 null）时不应抛 NPE。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private AppUserService appUserService;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
    }

    @Test
    void loadOrderUsersShouldTolerateNullRecommenderIds() throws Exception {
        OrderMaster order = new OrderMaster();
        order.setUserId(1L);
        order.setRecommenderUserId(null);
        order.setLevel2RecommenderUserId(null);

        AppUser buyer = new AppUser();
        buyer.setId(1L);
        buyer.setNickname("buyer");
        when(appUserService.listByIds(anyList())).thenReturn(List.of(buyer));

        Map<Long, AppUser> users = invokeLoadOrderUsers(List.of(order));

        assertEquals(1, users.size());
        assertTrue(users.containsKey(1L));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, AppUser> invokeLoadOrderUsers(List<OrderMaster> orders) throws Exception {
        Method method = OrderServiceImpl.class.getDeclaredMethod("loadOrderUsers", List.class);
        method.setAccessible(true);
        return (Map<Long, AppUser>) method.invoke(service, orders);
    }
}
