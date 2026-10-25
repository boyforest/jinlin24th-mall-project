package com.jinlin24th.jinlin.coffee;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.config.WebMvcConfig;
import com.jinlin24th.jinlin.common.exception.GlobalExceptionHandler;
import com.jinlin24th.jinlin.common.log.OperationLogInterceptor;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.controller.*;
import com.jinlin24th.jinlin.service.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@WebAppConfiguration
@ContextConfiguration(classes = CoffeeHttpContractTest.WebConfig.class)
class CoffeeHttpContractTest extends CoffeeDatabaseTestSupport {
    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc
    @Import({WebMvcConfig.class, GlobalExceptionHandler.class})
    static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            // 对齐 Spring Boot 的默认静态资源兜底，未知路径会经过拦截器并返回项目 404。
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }

        @Bean
        JwtUtil jwt() {
            return mock(JwtUtil.class);
        }

        @Bean
        AuthSessionService sessions() {
            return mock(AuthSessionService.class);
        }

        @Bean
        JwtInterceptor users(JwtUtil jwt, AuthSessionService sessions) {
            return new JwtInterceptor(jwt, sessions);
        }

        @Bean
        AdminJwtInterceptor admin(JwtUtil jwt, AuthSessionService sessions) {
            return new AdminJwtInterceptor(jwt, sessions);
        }

        @Bean
        OperationLogInterceptor logs() {
            OperationLogInterceptor log = mock(OperationLogInterceptor.class);
            when(log.preHandle(any(), any(), any())).thenReturn(true);
            return log;
        }

        @Bean
        UserCoffeeController coffeeController(CoffeeCatalogService catalog) {
            return new UserCoffeeController(catalog);
        }

        @Bean
        UserCoffeeCartController cartController(CoffeeCartAppService cart) {
            return new UserCoffeeCartController(cart);
        }

        @Bean
        AdminCoffeeController adminController(CoffeeCatalogService catalog) {
            return new AdminCoffeeController(catalog);
        }

        @Bean
        UserBusinessOrderController orderController(BusinessOrderQueryService orders) {
            return new UserBusinessOrderController(orders);
        }
    }

    @Autowired WebApplicationContext web;
    @Autowired JwtUtil jwt;
    private MockMvc mvc;

    @BeforeEach
    void setupWeb() {
        reset(jwt);
        when(jwt.validateToken("user")).thenReturn(true);
        when(jwt.getTokenType("user")).thenReturn(JwtUtil.TOKEN_TYPE_USER);
        when(jwt.getUserIdFromToken("user")).thenReturn(10L);
        for (String token : List.of("admin", "staff")) {
            when(jwt.validateToken(token)).thenReturn(true);
            when(jwt.getTokenType(token)).thenReturn(JwtUtil.TOKEN_TYPE_ADMIN);
            when(jwt.getSubjectFromToken(token)).thenReturn(token);
            when(jwt.getAdminIdFromToken(token)).thenReturn(token.equals("admin") ? ADMIN : 20L);
        }
        when(admins.getRoleCodes(20L)).thenReturn(List.of("COFFEE_STAFF"));
        mvc = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void onlyExactGetCatalogPathsArePublicAndUnknownOrderingIsUnavailable() throws Exception {
        mvc.perform(get("/user/coffee/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.capabilities.placeOrder").value(false))
                .andExpect(jsonPath("$.data.capabilities.payOnline").value(false));
        mvc.perform(get("/user/coffee/menu").param("storeId", storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].skus[0].priceCent").value(1800));
        for (String path :
                List.of(
                        "/user/coffee/cart/list",
                        "/user/business-order/list",
                        "/admin/coffee/context")) {
            mvc.perform(get(path).param("storeId", storeId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40100));
        }
        mvc.perform(post("/user/coffee/store")).andExpect(status().isMethodNotAllowed());
        mvc.perform(head("/user/coffee/store")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/coffee/store/")).andExpect(status().isUnauthorized());
        mvc.perform(post("/user/coffee/order/create").header("Authorization", "Bearer user"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/user/coffee/pay").header("Authorization", "Bearer user"))
                .andExpect(status().isNotFound());
    }

    @Test
    void strictJsonTypesUnknownFieldsAndParameterBoundsReturn40001() throws Exception {
        String valid =
                "\"storeId\":\""
                        + storeId
                        + "\",\"skuId\":\""
                        + product.getSkus().get(0).getId()
                        + "\"";
        for (String invalid : List.of("-1", "0", "100", "1.5", "1.0", "true", "\"1\"", "null")) {
            mvc.perform(
                            post("/user/coffee/cart")
                                    .header("Authorization", "Bearer user")
                                    .contentType("application/json")
                                    .content("{" + valid + ",\"quantity\":" + invalid + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        for (String extra :
                List.of(
                        "\"userId\":20",
                        "\"unitPriceCent\":1",
                        "\"priceCent\":1",
                        "\"checked\":true")) {
            mvc.perform(
                            post("/user/coffee/cart")
                                    .header("Authorization", "Bearer user")
                                    .contentType("application/json")
                                    .content("{" + valid + ",\"quantity\":1," + extra + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        mvc.perform(get("/user/coffee/menu"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/user/coffee/menu").param("storeId", "9223372036854775808"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/user/business-order/list")
                                .header("Authorization", "Bearer user")
                                .param("size", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/user/business-order/list")
                                .header("Authorization", "Bearer user")
                                .param("page", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartRoutesUseVersionsAndSelectionHasItsOwnMapping() throws Exception {
        String id = cart.add(10L, addDTO(2)).getId();
        mvc.perform(
                        put("/user/coffee/cart/selection")
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content("{\"storeId\":\"" + storeId + "\",\"checked\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(1));
        mvc.perform(
                        put("/user/coffee/cart/" + id)
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content("{\"quantity\":1,\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(90409));
        mvc.perform(
                        put("/user/coffee/cart/" + id)
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(
                        get("/user/business-order/list")
                                .header("Authorization", "Bearer user")
                                .param("businessType", "COFFEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void skuTypesEnumBoundsAndCartVersionsAreValidated() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        for (String price : List.of("0", "-1", "1.5", "9007199254740992", "\"1800\"")) {
            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.valueToTree(productDTO());
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.withArray("skus").get(0))
                    .set("priceCent", mapper.readTree(price));
            mvc.perform(
                            post("/admin/coffee/product")
                                    .param("storeId", storeId)
                                    .header("Authorization", "Bearer admin")
                                    .contentType("application/json")
                                    .content(body.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        String id = cart.add(10L, addDTO(1)).getId();
        for (String version : List.of("-1", "0.5", "9007199254740992", "\"0\"")) {
            mvc.perform(
                            put("/user/coffee/cart/" + id)
                                    .header("Authorization", "Bearer user")
                                    .contentType("application/json")
                                    .content("{\"checked\":0,\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.valueToTree(productDTO());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.withArray("skus").get(0))
                .put("temperature", "WARM");
        mvc.perform(
                        post("/admin/coffee/product")
                                .param("storeId", storeId)
                                .header("Authorization", "Bearer admin")
                                .contentType("application/json")
                                .content(body.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/user/coffee/cart")
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content(
                                        "{\"storeId\":"
                                                + storeId
                                                + ",\"skuId\":\""
                                                + product.getSkus().get(0).getId()
                                                + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderJsonUsesIsoTimeStringIdsAndExplicitNulls() throws Exception {
        jdbc.update(
                "INSERT INTO"
                    + " order_master(order_no,user_id,total_amount,pay_amount,status,receiver_name,receiver_phone,receiver_address,create_time)"
                    + " VALUES('HTTP-ORDER',10,1.23,1.23,0,'测试','测试','测试','2026-10-24 09:00:00')");
        mvc.perform(get("/user/business-order/list").header("Authorization", "Bearer user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").isString())
                .andExpect(
                        jsonPath("$.data.records[0].createdAt").value("2026-10-24T09:00:00+08:00"))
                .andExpect(jsonPath("$.data.records[0].summaryItems").isEmpty())
                .andExpect(jsonPath("$.data.records[0].amounts.payableAmountCent").value(123))
                .andExpect(
                        jsonPath("$.data.records[0].amounts.paidAmountCent")
                                .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.records[0].receiverPhone").doesNotExist());
    }

    @Test
    void adminTokenAloneDoesNotGrantCoffeeAccessOrAllowPaymentSwitches() throws Exception {
        mvc.perform(get("/admin/coffee/context").header("Authorization", "Bearer staff"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(get("/admin/coffee/context").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("coffee:menu:read"));
        mvc.perform(
                        put("/admin/coffee/store/" + storeId)
                                .header("Authorization", "Bearer admin")
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"店\",\"address\":\"地址\",\"openingHours\":\"9-18\",\"businessStatus\":\"OPEN\",\"placeOrder\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
