package com.jinlin24th.jinlin.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C 端（/user）接口不得暴露 CRM 客户数据的守卫测试。
 * <p>
 * 背景：`BizCustomerController` 曾用 `@RequestMapping("user/bizcustomer")` 把
 * 客户名单（姓名、联系电话、累计消费额、订单数）暴露在 C 端命名空间下。
 * `JwtInterceptor` 只要求"有效的小程序用户 JWT"，而小程序注册是无门槛的微信一键登录，
 * 因此**任何注册用户都能翻走全部 B2B 客户 PII**。
 * 管理端真正的入口是同功能的 `/admin/customer`（AdminCustomerController）。
 * <p>
 * 本测试把这条边界固定下来：`/user/**` 下的控制器不得引用 CRM 客户模型/服务。
 */
class UserNamespaceMustNotExposeCrmDataTest {

    private static final Path CONTROLLER_DIR =
        Path.of("src/main/java/com/jinlin24th/jinlin/controller");

    /** 收集所有映射在 C 端命名空间（/user 或 user）下的控制器文件。 */
    private List<Path> cEndControllers() throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLER_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("@RequestMapping(\"/user") || source.contains("@RequestMapping(\"user")) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    @Test
    @DisplayName("/user 命名空间下不得存在引用 BizCustomer 的控制器")
    void cEndControllersMustNotTouchCrmCustomerData() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path controller : cEndControllers()) {
            String source = Files.readString(controller);
            if (source.contains("BizCustomer")) {
                offenders.add(controller.getFileName().toString());
            }
        }

        assertTrue(offenders.isEmpty(),
            "C 端控制器不得暴露 CRM 客户数据（应放在 /admin/** 并做权限校验），违规文件：" + offenders);
    }

    @Test
    @DisplayName("管理端客户控制器必须仍在，且映射在 /admin 下")
    void adminCustomerControllerMustRemainUnderAdmin() throws IOException {
        Path adminController = CONTROLLER_DIR.resolve("AdminCustomerController.java");
        assertTrue(Files.exists(adminController),
            "删掉 C 端越权入口时不能连带删掉管理端客户管理功能");

        String source = Files.readString(adminController);
        assertTrue(source.contains("@RequestMapping(\"/admin/customer\")"),
            "管理端客户接口必须挂在 /admin 下才能被 AdminJwtInterceptor 保护");
    }

    @Test
    @DisplayName("已知的越权控制器文件不应存在")
    void knownOffendingControllerMustNotExist() {
        assertTrue(!Files.exists(CONTROLLER_DIR.resolve("BizCustomerController.java")),
            "BizCustomerController 曾把客户 PII 暴露给任意小程序用户，不得重新引入");
    }
}
