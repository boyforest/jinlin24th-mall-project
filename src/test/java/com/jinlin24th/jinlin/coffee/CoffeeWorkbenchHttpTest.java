package com.jinlin24th.jinlin.coffee;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.config.WebMvcConfig;
import com.jinlin24th.jinlin.common.exception.*;
import com.jinlin24th.jinlin.common.log.OperationLogInterceptor;
import com.jinlin24th.jinlin.common.rate.LoginRateLimitService;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import com.jinlin24th.jinlin.controller.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.*;

@WebAppConfiguration
@ContextConfiguration(classes = CoffeeWorkbenchHttpTest.WebConfig.class)
class CoffeeWorkbenchHttpTest extends WorkbenchDatabaseTestSupport {
    @Configuration
    @EnableWebMvc
    @Import({
        WebMvcConfig.class,
        AdminJwtInterceptor.class,
        JwtInterceptor.class,
        OperationLogInterceptor.class,
        GlobalExceptionHandler.class,
        AdminAccountController.class,
        AdminAuthController.class,
        AdminCoffeeController.class,
        AdminCoffeeOrderController.class,
        UserCoffeeOrderController.class,
        CoffeePaymentNotifyController.class
    })
    static class WebConfig
            implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override
        public void addResourceHandlers(
                org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
                        registry) {
            registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        }

        @Bean
        LoginRateLimitService rates() {
            return mock(LoginRateLimitService.class);
        }
    }

    @Autowired WebApplicationContext web;
    @Autowired JwtUtil jwt;
    MockMvc mvc;

    @BeforeEach
    void webSetup() {
        reset(jwt);
        mvc = MockMvcBuilders.webAppContextSetup(web).build();
        for (String token : List.of("super", "coffee", "goods", "restricted")) {
            when(jwt.validateToken(token)).thenReturn(true);
            when(jwt.getTokenType(token)).thenReturn(JwtUtil.TOKEN_TYPE_ADMIN);
            when(jwt.getSubjectFromToken(token))
                    .thenReturn(token.equals("restricted") ? "coffee" : token);
            when(jwt.getAdminIdFromToken(token))
                    .thenReturn(
                            token.equals("super")
                                    ? 1L
                                    : token.equals("goods") ? goodsAdmin : coffeeAdmin);
            when(jwt.getJtiFromToken(token)).thenReturn("session-" + token);
        }
        when(jwt.getTokenScope("restricted")).thenReturn(JwtUtil.TOKEN_SCOPE_PASSWORD_CHANGE);
        when(jwt.validateToken("user")).thenReturn(true);
        when(jwt.getTokenType("user")).thenReturn(JwtUtil.TOKEN_TYPE_USER);
        when(jwt.getUserIdFromToken("user")).thenReturn(10L);
        when(jwt.getJtiFromToken("user")).thenReturn("user-session");
    }

    @AfterEach
    void clear() {
        CurrentUserContext.clear();
    }

    @Test
    void realContextAccountEndpointsAndModuleBoundary() throws Exception {
        mvc.perform(get("/admin/auth/context").header("Authorization", "Bearer coffee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminId").isString())
                .andExpect(jsonPath("$.data.defaultRoute").value("/coffee/workbench"));
        for (String path :
                List.of(
                        "/admin/accounts/list",
                        "/admin/user/list",
                        "/admin/distribution/list",
                        "/admin/payment/bill",
                        "/admin/marketing/activity/list"))
            mvc.perform(get(path).header("Authorization", "Bearer coffee"))
                    .andExpect(status().isForbidden());
        mvc.perform(get("/admin/coffee/context").header("Authorization", "Bearer goods"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/accounts/list").header("Authorization", "Bearer super"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
        mvc.perform(
                        get("/admin/coffee/menu")
                                .param("storeId", Long.toString(store + 100))
                                .header("Authorization", "Bearer coffee"))
                .andExpect(status().isNotFound());
    }

    @Test
    void temporaryPasswordTokenOnlyAllowsExistingPasswordFlow() throws Exception {
        mvc.perform(get("/admin/auth/context").header("Authorization", "Bearer restricted"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/logout").header("Authorization", "Bearer restricted"))
                .andExpect(status().isForbidden());
        when(jwt.generateJti()).thenReturn("changed-session");
        when(jwt.generateAdminToken(anyLong(), anyString(), anyString()))
                .thenReturn("changed-token");
        mvc.perform(
                        put("/admin/password")
                                .header("Authorization", "Bearer restricted")
                                .contentType("application/json")
                                .content(
                                        "{\"oldPassword\":\"temporary-password\",\"newPassword\":\"changed-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePwd").value(false));
    }

    @Test
    void accountAccessAndPasswordResetRevokeOldSession() throws Exception {
        Map<String, String> revoked = new HashMap<>();
        doAnswer(
                        inv -> {
                            revoked.put(inv.getArgument(0), "revoked");
                            return null;
                        })
                .when(sessions)
                .onLogoutAdmin(anyString());
        doAnswer(
                        inv -> {
                            if (revoked.containsKey(inv.getArgument(0)))
                                throw BizException.unauthorized("旧会话已撤销");
                            return null;
                        })
                .when(sessions)
                .validateAdmin(anyString(), anyString());
        mvc.perform(
                        post("/admin/accounts/" + coffeeAdmin + "/reset-password")
                                .header("Authorization", "Bearer super")
                                .contentType("application/json")
                                .content("{\"temporaryPassword\":\"another-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/auth/context").header("Authorization", "Bearer coffee"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderingEndpointsEnforceAuthTypesVersionsAndUnknownFields() throws Exception {
        mvc.perform(post("/user/coffee/orders").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        var req = request("http-create");
        String body = json.writeValueAsString(req);
        mvc.perform(
                        post("/user/coffee/orders")
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.amounts.payableAmountCent").value(3600))
                .andExpect(jsonPath("$.data.events").doesNotExist());
        String numeric = body.replace("\"storeId\":\"" + store + "\"", "\"storeId\":" + store);
        mvc.perform(
                        post("/user/coffee/orders")
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content(numeric))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/user/coffee/orders")
                                .header("Authorization", "Bearer user")
                                .contentType("application/json")
                                .content(
                                        body.substring(0, body.length() - 1)
                                                + ",\"payableAmountCent\":1}"))
                .andExpect(status().isBadRequest());
        var o = pay(create());
        mvc.perform(
                        post("/admin/coffee/orders/" + o.getId() + "/accept")
                                .header("Authorization", "Bearer coffee")
                                .contentType("application/json")
                                .content("{\"expectedVersion\":\"1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/admin/coffee/orders/" + o.getId() + "/accept")
                                .header("Authorization", "Bearer coffee")
                                .contentType("application/json")
                                .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void newAccountRequiresOneRealStoreAndDoesNotReturnCredentials() throws Exception {
        mvc.perform(
                        post("/admin/accounts")
                                .header("Authorization", "Bearer super")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\"new-coffee\",\"temporaryPassword\":\"new-password\",\"roleCode\":\"COFFEE_ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/admin/accounts")
                                .header("Authorization", "Bearer super")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\"new-coffee\",\"temporaryPassword\":\"new-password\",\"roleCode\":\"COFFEE_ADMIN\",\"storeId\":\""
                                                + store
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePwd").value(true))
                .andExpect(jsonPath("$.data.temporaryPassword").doesNotExist());
    }
}
