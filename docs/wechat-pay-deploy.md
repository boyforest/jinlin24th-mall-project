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
