package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.vo.CoffeeCartVO;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntFunction;

/** 通过真实代理事务和数据库连接并发执行，默认 H2，亦在一次性 MySQL 8 容器运行。 */
class CoffeeCartConcurrencyTest extends CoffeeDatabaseTestSupport {
    @Test
    void concurrentAddsCreateOneRowWithoutLostQuantity() throws Exception {
        List<Boolean> results =
                concurrent(
                        24,
                        i -> {
                            cart.add(10L, addDTO(1));
                            return true;
                        });
        assertEquals(24, results.stream().filter(Boolean::booleanValue).count());
        List<CoffeeCartVO> rows = cart.list(10L, Long.valueOf(storeId));
        assertEquals(1, rows.size());
        assertEquals(24, rows.get(0).getQuantity());
        assertEquals(23, rows.get(0).getVersion());
    }

    @Test
    void overflowAddsAreRejectedAtomically() throws Exception {
        cart.add(10L, addDTO(90));
        List<Boolean> results =
                concurrent(
                        20,
                        i -> {
                            try {
                                cart.add(10L, addDTO(1));
                                return true;
                            } catch (BizException e) {
                                assertEquals(BizCode.PARAM_INVALID, e.getBizCode());
                                return false;
                            }
                        });
        assertEquals(9, results.stream().filter(Boolean::booleanValue).count());
        CoffeeCartVO row = cart.list(10L, Long.valueOf(storeId)).get(0);
        assertEquals(99, row.getQuantity());
        assertEquals(9, row.getVersion());
    }

    @Test
    void concurrentAbsoluteUpdatesPermitOnlyOneWriterOfAVersion() throws Exception {
        CoffeeCartVO row = cart.add(10L, addDTO(1));
        List<Boolean> results =
                concurrent(
                        12,
                        i -> {
                            try {
                                cart.update(
                                        10L, Long.valueOf(row.getId()), updateDTO(0, i + 2, null));
                                return true;
                            } catch (BizException e) {
                                assertEquals(BizCode.COFFEE_CART_VERSION_CONFLICT, e.getBizCode());
                                return false;
                            }
                        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, cart.list(10L, Long.valueOf(storeId)).get(0).getVersion());
    }

    @Test
    void concurrentFirstStoreCreationHonorsUniquePrimarySlot() throws Exception {
        for (String table :
                List.of(
                        "coffee_cart",
                        "coffee_sku",
                        "coffee_product",
                        "coffee_category",
                        "coffee_store")) {
            jdbc.update("DELETE FROM " + table);
        }
        List<Boolean> results =
                concurrent(
                        4,
                        i -> {
                            try {
                                catalog.saveStore(ADMIN, null, storeDTO());
                                return true;
                            } catch (BizException e) {
                                assertEquals(BizCode.COFFEE_STORE_ALREADY_EXISTS, e.getBizCode());
                                return false;
                            }
                        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coffee_store", Integer.class));
    }

    private <T> List<T> concurrent(int count, IntFunction<T> operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(15, TimeUnit.SECONDS));
                                    return operation.apply(index);
                                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            List<T> result = new ArrayList<>();
            for (Future<T> future : futures) {
                result.add(future.get(30, TimeUnit.SECONDS));
            }
            return result;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
