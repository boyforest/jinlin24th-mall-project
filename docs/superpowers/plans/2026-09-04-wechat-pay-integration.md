# 微信支付真实上线实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复微信支付平台公钥 ID 与商户证书序列号混用的缺陷，补齐部署配置与上线手册，使小程序 JSAPI 真实支付/退款端到端可用。

**Architecture:** 不改业务链路，仅解耦验签标识配置：`wx.pay.serial-no`（请求 Authorization 头）与新增 `wx.pay.platform-public-key-id`（响应/回调验签）各自独立；WxPayClient 构造函数与 WxPayServiceImpl 回调验签改为使用独立配置；证书文件通过 docker bind mount 挂载进 backend 容器。

**Tech Stack:** Java 17 / Spring Boot 3.2 / Maven（`./mvnw`）/ JUnit5 + Mockito（纯单元测试，无 Spring 上下文）/ Docker Compose

**前置阅读：** `docs/superpowers/specs/2026-09-04-wechat-pay-integration-design.md`

---

### Task 1: verifyCallbackSignature 增加平台公钥 ID 校验（TDD）

**Files:**
- Create: `src/test/java/com/jinlin24th/jinlin/service/impl/WxPayServiceImplTest.java`
- Modify: `src/main/java/com/jinlin24th/jinlin/service/impl/WxPayServiceImpl.java`（`verifyCallbackSignature`，第 475-505 行附近）

现状缺陷：回调只验签、不比对响应头 `Wechatpay-Serial` 是否等于本地配置的平台公钥 ID（设计文档第 1 节第 3 条）。

- [ ] **Step 1: 创建测试文件（先写会失败的测试）**

创建 `src/test/java/com/jinlin24th/jinlin/service/impl/WxPayServiceImplTest.java`，完整内容如下：

```java
package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.config.WxPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WxPayServiceImplTest {

    private static final String PLATFORM_KEY_ID = "PUBKEY_ID_8888";

    @Mock
    private WxPayClient wxPayClient;

    private WxPayServiceImpl service;
    private KeyPair keyPair;
    private KeyPair wrongKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = new WxPayServiceImpl();
        ReflectionTestUtils.setField(service, "wxPayClient", wxPayClient);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        wrongKeyPair = generator.generateKeyPair();
        when(wxPayClient.getWechatPayPublicKey()).thenReturn(keyPair.getPublic());
        when(wxPayClient.getWechatPayPublicKeyId()).thenReturn(PLATFORM_KEY_ID);
    }

    @Test
    void validSignatureWithMatchingSerialShouldPass() throws Exception {
        boolean result = invokeVerify(buildCallback("2001", true, 0, keyPair.getPrivate()));
        assertTrue(result);
    }

    @Test
    void validSignatureButSerialMismatchShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback("2001", false, 0, keyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void tamperedSignatureShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback(PLATFORM_KEY_ID, true, 0, wrongKeyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void expiredTimestampShouldFail() throws Exception {
        boolean result = invokeVerify(buildCallback(PLATFORM_KEY_ID, true, 400, keyPair.getPrivate()));
        assertFalse(result);
    }

    @Test
    void missingParamsShouldFail() throws Exception {
        boolean nullResult = invokeVerify(null, null, null, null, null);
        assertFalse(nullResult);
    }

    private String[] buildCallback(String serial, boolean serialMatches, long secondsAgo,
                                   java.security.PrivateKey signKey) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond() - secondsAgo);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String body = "{\"dummy\":\"ok\"}";
        String message = ts + "\n" + nonce + "\n" + body + "\n";
        String signature = com.jinlin24th.jinlin.common.util.WxPayUtil.sign(
                message, "SHA256withRSA", signKey);
        String effectiveSerial = serialMatches ? PLATFORM_KEY_ID : serial;
        return new String[]{ts, nonce, signature, effectiveSerial, body};
    }

    private boolean invokeVerify(String[] parts) throws Exception {
        return invokeVerify(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    private boolean invokeVerify(String timestamp, String nonce, String signature,
                                 String serial, String body) throws Exception {
        Method method = WxPayServiceImpl.class.getDeclaredMethod(
                "verifyCallbackSignature", String.class, String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, timestamp, nonce, signature, serial, body);
    }
}
```

- [ ] **Step 2: 运行测试，确认 `validSignatureButSerialMismatchShouldFail` 失败**

Run: `./mvnw test -Dtest=WxPayServiceImplTest -q`
Expected: 4 个通过、`validSignatureButSerialMismatchShouldFail` FAIL（当前实现只验签不比对 serial，返回 true）。

- [ ] **Step 3: 实现修复**

修改 `WxPayServiceImpl.java`：

1. 在 import 区（第 34 行 `import java.util.Map;` 之后）增加：

```java
import java.util.Objects;
```

2. 将 `verifyCallbackSignature` 方法体（当前 475-505 行）替换为：

```java
    private boolean verifyCallbackSignature(String timestamp, String nonce,
                                             String signature, String serial,
                                             String body) {
        if (timestamp == null || nonce == null || signature == null || body == null) {
            log.warn("回调验签参数缺失: timestamp={}, nonce={}, signature={}, bodyLen={}",
                    timestamp, nonce, signature, body != null ? body.length() : 0);
            return false;
        }
        // 回调头 Wechatpay-Serial 必须等于本地配置的平台公钥 ID，防止用错误公钥验签
        if (!Objects.equals(serial, wxPayClient.getWechatPayPublicKeyId())) {
            log.warn("回调序列号不匹配: headerSerial={}, localPublicKeyId={}",
                    serial, wxPayClient.getWechatPayPublicKeyId());
            return false;
        }
        try {
            // 检查时间戳是否在5分钟内
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - ts) > 300) {
                log.warn("回调时间戳已过期: timestamp={}, now={}, diff={}s", timestamp, now, Math.abs(now - ts));
                return false;
            }

            // 构建验签消息串: timestamp\nnonce\nbody\n
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";

            PublicKey pubKey = wxPayClient.getWechatPayPublicKey();
            boolean valid = WxPayUtil.verify(message, signature, "SHA256withRSA", pubKey);
            if (!valid) {
                log.warn("回调签名不匹配: serial={}", serial);
            }
            return valid;
        } catch (NumberFormatException e) {
            log.warn("回调时间戳格式异常: timestamp={}", timestamp, e);
            return false;
        }
    }
```

注意保持原有注释与逻辑，仅新增 serial 校验块并把其放在时间戳校验之前。

- [ ] **Step 4: 运行测试确认全部通过**

Run: `./mvnw test -Dtest=WxPayServiceImplTest -q`
Expected: 5 个测试全 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/jinlin24th/jinlin/service/impl/WxPayServiceImpl.java \
        src/test/java/com/jinlin24th/jinlin/service/impl/WxPayServiceImplTest.java
git commit -m "fix: 微信支付回调验签前校验平台公钥ID，防错误公钥验签"
```

---

### Task 2: WxPayClient 平台公钥 ID 与商户证书序列号解耦（TDD）

**Files:**
- Create: `src/test/java/com/jinlin24th/jinlin/common/config/WxPayClientTest.java`
- Modify: `src/main/java/com/jinlin24th/jinlin/common/config/WxPayClient.java`（构造函数第 40 行 `@Value`）

现状缺陷：`WxPayClient.java:40` 将 `wx.pay.serial-no` 同时注入商户证书序列号和平台公钥 ID 两个参数。

- [ ] **Step 1: 创建测试文件（先写会失败的测试）**

创建 `src/test/java/com/jinlin24th/jinlin/common/config/WxPayClientTest.java`，完整内容如下：

```java
package com.jinlin24th.jinlin.common.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WxPayClientTest {

    @TempDir
    static Path tempDir;

    private static Path privateKeyFile;
    private static Path publicKeyFile;

    @Configuration
    @Import(WxPayClient.class)
    @EnableConfigurationProperties(WxPayConfig.class)
    static class TestPayConfig {
    }

    @BeforeAll
    static void generateKeyFiles() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        privateKeyFile = tempDir.resolve("apiclient_key.pem");
        publicKeyFile = tempDir.resolve("pub_key.pem");
        Files.write(privateKeyFile, privatePem.getBytes(StandardCharsets.UTF_8));
        Files.write(publicKeyFile, publicPem.getBytes(StandardCharsets.UTF_8));
    }

    private ApplicationContextRunner payContextRunner(String serialNo, String platformKeyId) {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "wx.pay.enabled=true",
                        "wx.pay.mch-id=1900000109",
                        "wx.pay.api-v3-key=0123456789abcdef0123456789abcdef",
                        "wx.pay.serial-no=" + serialNo,
                        "wx.pay.platform-public-key-id=" + platformKeyId,
                        "wx.pay.private-key-path=" + privateKeyFile,
                        "wx.pay.certificate-path=" + publicKeyFile)
                .withUserConfiguration(TestPayConfig.class);
    }

    @Test
    void constructorShouldBindPlatformPublicKeyIdFromItsOwnProperty() {
        payContextRunner("MERCHANT-SERIAL-AAA", "PLATFORM-ID-BBB").run(context -> {
            WxPayClient client = context.getBean(WxPayClient.class);
            assertEquals("PLATFORM-ID-BBB", client.getWechatPayPublicKeyId());
        });
    }

    @Test
    void merchantSerialNoShouldNotLeakIntoPublicKeyId() {
        payContextRunner("MERCHANT-SERIAL-AAA", "PLATFORM-ID-BBB").run(context -> {
            WxPayClient client = context.getBean(WxPayClient.class);
            org.junit.jupiter.api.Assertions.assertNotEquals("MERCHANT-SERIAL-AAA",
                    client.getWechatPayPublicKeyId());
        });
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw test -Dtest=WxPayClientTest -q`
Expected: `constructorShouldBindPlatformPublicKeyIdFromItsOwnProperty` FAIL（当前 4 号参数读的是 `wx.pay.serial-no`，得到 `MERCHANT-SERIAL-AAA`）。

- [ ] **Step 3: 实现修复**

修改 `WxPayClient.java` 构造函数参数注解（第 40 行）：

将：

```java
                       @Value("${wx.pay.serial-no:}") String wechatPayPublicKeyId,
```

改为：

```java
                       @Value("${wx.pay.platform-public-key-id:}") String wechatPayPublicKeyId,
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./mvnw test -Dtest=WxPayClientTest -q`
Expected: 2 个测试全 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/jinlin24th/jinlin/common/config/WxPayClient.java \
        src/test/java/com/jinlin24th/jinlin/common/config/WxPayClientTest.java
git commit -m "fix: WxPayClient 平台公钥ID与商户证书序列号配置解耦"
```

---

### Task 3: 新增配置项（WxPayConfig / application.yml / .env.example）

**Files:**
- Modify: `src/main/java/com/jinlin24th/jinlin/common/config/WxPayConfig.java`
- Modify: `src/main/resources/application.yml`（wx.pay 段）
- Modify: `.env.example`

- [ ] **Step 1: WxPayConfig 增加字段**

在 `WxPayConfig.java` 中、`private String serialNo;` 字段之后增加：

```java
    /**
     * 微信支付平台公钥 ID（用于响应/回调验签，与商户API证书序列号不同）
     */
    private String platformPublicKeyId;
```

- [ ] **Step 2: application.yml 增加映射**

`src/main/resources/application.yml` 第 53 行 `serial-no: ${WX_PAY_SERIAL_NO:}` 之后增加：

```yaml
    platform-public-key-id: ${WX_PAY_PLATFORM_PUBLIC_KEY_ID:}
```

- [ ] **Step 3: .env.example 增加变量**

`.env.example` 第 34 行 `WX_PAY_SERIAL_NO=` 之后增加：

```env
WX_PAY_PLATFORM_PUBLIC_KEY_ID=
```

- [ ] **Step 4: 编译验证**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS（无报错即通过）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/jinlin24th/jinlin/common/config/WxPayConfig.java \
        src/main/resources/application.yml .env.example
git commit -m "feat: 新增 wx.pay.platform-public-key-id 独立配置项"
```

---

### Task 4: docker-compose 挂载证书目录 + .gitignore

**Files:**
- Modify: `docker-compose.yml`（backend volumes）
- Modify: `.gitignore`

- [ ] **Step 1: backend 增加 bind mount**

`docker-compose.yml` 第 92-94 行 backend volumes 增加一行：

```yaml
    volumes:
      - uploads:/app/uploads
      - logs:/app/logs
      - ./certs:/app/certs:ro
```

（服务器在项目根目录新建 `certs/`，放置 `apiclient_key.pem` 与 `pub_key.pem`）

- [ ] **Step 2: .gitignore 忽略 certs 目录**

在 `.gitignore` 第 79-81 行 secrets 段追加：

```gitignore
# 微信支付证书目录（服务器本地放置，禁止提交）
certs/
```

- [ ] **Step 3: 提交**

```bash
git add docker-compose.yml .gitignore
git commit -m "feat: backend 容器挂载微信支付证书目录"
```

---

### Task 5: 上线操作手册 docs/wechat-pay-deploy.md

**Files:**
- Create: `docs/wechat-pay-deploy.md`

- [ ] **Step 1: 编写上线手册**

创建 `docs/wechat-pay-deploy.md`，完整内容如下：

```markdown
# 微信支付真实上线操作手册

对应设计文档：`docs/superpowers/specs/2026-09-04-wechat-pay-integration-design.md`。
前提：已具备认证小程序 + 直连微信支付商户号。

## 1. 材料清单（商户平台 https://pay.weixin.qq.com → 账户中心 → API安全）

| 材料 | 获取方式 | 用途 |
| --- | --- | --- |
| 商户号 mch_id | 账户中心 → 商户信息 | 接口鉴权 |
| 商户API证书序列号 | API安全 → API证书 → 查看序列号 | 请求 Authorization 头 serial_no |
| 商户API私钥 `apiclient_key.pem` | API安全 → API证书 → 下载 | 请求签名 |
| 微信支付公钥 `pub_key.pem` | API安全 → 微信支付公钥 → 下载 | 响应/回调验签 |
| 平台公钥ID | API安全 → 微信支付公钥 → 查看公钥ID | 回调验签 serial 比对 |
| APIv3密钥 | API安全 → APIv3密钥（32字符，设置后不可查看） | 回调数据解密 |

> 注意：商户证书序列号 与 平台公钥ID 是两个不同的值，切勿混填。

## 2. 服务器操作

1. 放置证书（不上 git）：

```bash
cd /www/wwwroot/jinlin24th
mkdir -p certs
# 上传 apiclient_key.pem、pub_key.pem 到 certs/
chmod 600 certs/*.pem
```

2. 更新 `.env`（追加/修改）：

```env
WX_PAY_ENABLED=true
WX_PAY_MCH_ID=你的商户号
WX_PAY_API_V3_KEY=你的APIv3密钥
WX_PAY_SERIAL_NO=商户API证书序列号
WX_PAY_PLATFORM_PUBLIC_KEY_ID=平台公钥ID
WX_PAY_PRIVATE_KEY_PATH=/app/certs/apiclient_key.pem
WX_PAY_CERTIFICATE_PATH=/app/certs/pub_key.pem
WX_PAY_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/notify
WX_PAY_REFUND_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/refund/notify
```

3. 拉取代码并重建 backend：

```bash
cd /www/wwwroot/jinlin24th
git pull
docker compose up -d --build backend
docker compose logs -f backend
```

启动日志出现 `微信支付客户端初始化成功,商户号: xxx` 即配置就绪。

## 3. 微信商户平台配置（纯后台）

1. 产品中心 → 开发配置 → JSAPI支付：支付授权目录 `https://api.jinlin24th.cn/`、回调域名 `api.jinlin24th.cn`
2. 产品中心 → AppID账号管理：确认小程序 appid 与商户号「已关联」
3. 账户中心 → 商户信息 → 安全设置：回调地址与 `.env` 一致
   - 支付通知：`https://api.jinlin24th.cn/api/payment/notify`
   - 退款通知：`https://api.jinlin24th.cn/api/payment/refund/notify`
4. API安全 → 支付API 配置：设置 APIv3 密钥（若未设置）
5. 退款接口 IP 白名单：填入服务器公网出口 IP（阿里云 ECS 公网 IP），否则退款报错

## 4. 端到端验收（小程序体验版 + 体验成员真机）

1. 后端日志确认启动成功（见上）
2. 小程序真实 openid 登录 → 加购 → 下单 → 拉起微信收银台 → 支付成功
3. 后端日志出现「收到支付回调通知」且无验签失败；订单 0→10（待发货），`payment_record` 记录 transaction_id
4. 若有推荐官关系：`distribution` 表生成佣金记录（支付成功自动）
5. 管理端对该订单发起退款（先用 0.01 验证）→ 回调后订单 60、佣金作废
6. 重复回调验证：微信自动重试不产生双佣金、订单不被重复更新

回滚：`.env` 置 `WX_PAY_ENABLED=false` → `docker compose up -d backend`，回到支付关闭状态。

## 5. 已知边界

- 订单超时自动取消依赖 RocketMQ；生产未启用时该消费不生效（不影响支付）
- 微信支付 2 小时超时 vs 本地 30 分钟自动关单并存，关单靠 `/admin/payment/close` 与 `closeOrder` 兜底
- 对账单为原始 CSV 下载，暂无对账 UI
```

- [ ] **Step 2: 提交**

```bash
git add docs/wechat-pay-deploy.md
git commit -m "docs: 微信支付真实上线操作手册"
```

---

### Task 6: 全量回归 + 打包验证

**Files:** 无新增

- [ ] **Step 1: 运行全部测试**

Run: `./mvnw test -q`
Expected: BUILD SUCCESS（含既有 JwtUtilTest、OperationLogConsumerTest、SmsMessageProducerTest、OrderTimeoutCancelConsumerTest、DistributionServiceImplTest 与本计划新增 2 个测试类）。

- [ ] **Step 2: 打包**

Run: `./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS，生成 `target/*.jar`。

- [ ] **Step 3: 确认推送点干净**

Run: `git status -s`
Expected: 无未提交改动（既有 untracked 文件 `admin-web/src/public class Myarraylist<E>{.java` 不属于本计划，忽略）。

- [ ] **Step 4: 提交（若上一步有遗漏改动）**

```bash
git add -A
git commit -m "chore: 支付上线回归"
```

---

### Task 7: 服务器上线验收（需要真实商户凭据，由项目负责人执行）

**Files:** 无代码改动；按 Task 5 手册执行

- [ ] **Step 1: 服务器准备证书与 .env**（手册第 2 节）
- [ ] **Step 2: 部署并确认启动日志**（手册第 2.3 步）
- [ ] **Step 3: 商户平台配置确认**（手册第 3 节）
- [ ] **Step 4: 真机端到端验收**（手册第 4 节，0.01 元小额测试退款）
- [ ] **Step 5: 验收通过后告知开发者，标记本计划完成；失败则收集 backend 日志回传**

---
