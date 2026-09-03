# 微信支付真实上线（A1）设计文档

日期：2026-09-04
状态：已评审通过，待转实现计划
仓库：jinlin24th-mall-project（金霖二十四养私域商城）

## 背景与现状

项目后端已具备完整的微信支付 V3 直连代码链路：

- `WxPayServiceImpl`：JSAPI 预下单、支付/退款回调验签解密、退款、关单、对账单（均为真实 API 调用）
- `WxPayClient`：OkHttp 封装，请求签名 + 响应验签
- `WxPayUtil`：RSA/AES-GCM/签名等底层工具
- 小程序端 `c-uniapp/src/api/payment.ts`：拉起 `uni.requestPayment` 完整实现
- 管理端与用户端订单支付入口已存在

用户资质已具备（认证小程序 + 直连微信支付商户号 + 密钥材料）。本期目标：**修复代码缺陷、补齐部署配置、端到端真实跑通支付与退款**。

## 核心问题（代码缺陷）

`WxPayClient.java:40` 将「商户 API 证书序列号」与「微信支付平台公钥 ID」混用为同一个配置项 `wx.pay.serial-no`。二者取值不同：

- 请求 `Authorization` 头需要商户 API 证书序列号
- 响应验签与回调验签需要平台公钥 ID（响应头 `Wechatpay-Serial` 比对）

线上从未启用真实支付，故缺陷未暴露。

## 设计

### 第 1 节：代码修复（进 git）

1. 新增配置项 `wx.pay.platform-public-key-id`（环境变量 `WX_PAY_PLATFORM_PUBLIC_KEY_ID`），与 `serial-no` 解耦：
   - `application.yml` wx.pay 段补充映射
   - `.env.example` 补充占位变量
   - `WxPayConfig` 增加 `platformPublicKeyId` 字段
2. `WxPayClient` 构造函数第 4 参数改为读 `wx.pay.platform-public-key-id`
3. `WxPayServiceImpl.verifyCallbackSignature()` 增加 serial 一致性校验：回调头 `Wechatpay-Serial` 必须等于配置的平台公钥 ID，再执行 RSA 验签（与 `validateResponse` 行为对齐）
4. 新增上线操作文档 `docs/wechat-pay-deploy.md`

不改动：`WxPayUtil`、下单/回调/退款业务逻辑、小程序端、nginx 路由。

### 第 2 节：证书与服务器部署

- `docker-compose.yml` backend 增加 bind mount：`./certs:/app/certs:ro`
- 服务器 `/www/wwwroot/jinlin24th/certs/` 存放：
  - `apiclient_key.pem`（商户 API 私钥，PKCS8）
  - `pub_key.pem`（微信支付平台公钥）
- `.gitignore` 增加 `certs/` 目录
- 服务器 `.env`（手工编辑，不进 git）：

```env
WX_PAY_ENABLED=true
WX_PAY_MCH_ID=<商户号>
WX_PAY_API_V3_KEY=<APIv3密钥>
WX_PAY_SERIAL_NO=<商户API证书序列号>
WX_PAY_PLATFORM_PUBLIC_KEY_ID=<平台公钥ID>
WX_PAY_PRIVATE_KEY_PATH=/app/certs/apiclient_key.pem
WX_PAY_CERTIFICATE_PATH=/app/certs/pub_key.pem
WX_PAY_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/notify
WX_PAY_REFUND_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/refund/notify
```

- 材料获取（商户平台 → 账户中心 → API安全）：API证书 → `apiclient_key.pem`；开启并下载「微信支付公钥」→ `pub_key.pem` + 公钥ID；设置 APIv3 密钥（32 字符，不可回看）
- 重新部署：

```bash
cd /www/wwwroot/jinlin24th
git pull
docker compose up -d --build backend
docker compose logs -f backend   # 期待日志：微信支付客户端初始化成功
```

### 第 3 节：微信商户平台配置（纯后台操作）

1. 产品中心 → 开发配置 → JSAPI支付：授权目录 `https://api.jinlin24th.cn/`、回调域名 `api.jinlin24th.cn`
2. 产品中心 → AppID账号管理：确认小程序 appid 已关联商户号
3. 账户中心 → 商户信息：确认回调地址与 `.env` 一致
   - 支付：`https://api.jinlin24th.cn/api/payment/notify`
   - 退款：`https://api.jinlin24th.cn/api/payment/refund/notify`
4. 退款接口 IP 白名单：配置服务器公网出口 IP（阿里云 ECS 公网 IP），否则退款接口报错（下单/支付不受影响）

### 第 4 节：验收与边界

验收步骤（真机体验版）：

1. 后端启动日志出现「微信支付客户端初始化成功」
2. 小程序真实 openid 登录 → 下单 → 拉起收银台 → 支付成功
3. 回调日志正常 → 订单 0→10、`payment_record` 记录 transaction_id、佣金生成
4. 管理端退款（先小额 0.01 验证）→ 到账回调 → 订单 60、佣金作废
5. 幂等验证：重复回调不产生双佣金、CAS 防并发
6. 通知域名可达性确认

已知边界（本期不做，文档标注）：

- 订单超时自动取消依赖 RocketMQ，生产未启用时消费不生效（既有问题，不影响支付上线）
- 对账单为原始 CSV 下载，管理端暂无对账 UI
- 微信 2h 支付超时与本地 30min 取消逻辑共存，靠 `closeOrder` 兜底（已实现）

回滚方案：`.env` 置 `WX_PAY_ENABLED=false` → `docker compose up -d backend`，无数据残留风险。

## 非目标

- 不重写为官方 SDK（wechatpay-java）
- 不接入服务商/特约商户模式
- 只做小程序内 JSAPI 支付；不做 H5 支付（浏览器/公众号网页拉起）和 App 支付（微信 OpenSDK）
- 营销广告升级（B1）另期进行
