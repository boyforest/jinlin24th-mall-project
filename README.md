# 金霖二十四养 C 端 uni-app

这是面向终端客户的 C 端小程序/H5 Demo，当前重点是和 Spring Boot 后端完成核心链路联调。

## 已接入能力

- 商品首页：分类筛选、商品列表、下拉刷新。
- 商品详情：商品信息、SKU 列表、加入购物车、立即下单。
- 用户登录：`wx.login` 获取 code，调用 `/user/appUser/login`，保存 JWT。
- 邀请绑定：启动参数 `inviterUserId` 会缓存，并在首次登录时传给后端。
- 购物车：查询、改数量、删除、去结算。
- 订单：创建订单、订单列表、订单详情。
- 我的：用户资料、会员等级、积分、退出登录。

## 启动

```bash
npm install
npm run dev:h5
```

微信小程序开发：

```bash
npm run dev:mp-weixin
```

然后用微信开发者工具打开 `c-uniapp` 目录。`project.config.json` 已配置：

```json
{
  "miniprogramRoot": "dist/dev/mp-weixin/"
}
```

注意：不要直接把 `src` 当原生小程序打开。uni-app 源码目录没有 `app.json`，`app.json` 会在运行 `npm run dev:mp-weixin` 后生成到 `dist/dev/mp-weixin`。

## 后端地址

默认后端地址是：

```text
http://localhost:7878
```

可通过环境变量覆盖：

```bash
VITE_API_BASE_URL=http://你的后端地址 npm run dev:h5
```

注意：微信开发者工具和真机通常不能用 `localhost` 访问电脑后端，需要改成电脑局域网 IP，并在小程序后台配置合法域名；本地开发可临时关闭域名校验。

本地联调时，在微信开发者工具里打开：

```text
详情 -> 本地设置 -> 勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”
```

如果要在手机真机预览，请把后端地址改成电脑局域网 IP，例如：

```bash
VITE_API_BASE_URL=http://172.24.132.15:7878 npm run dev:mp-weixin
```

真机预览不能使用 `localhost`，因为手机里的 `localhost` 指的是手机自己，不是你的电脑。

## 目录说明

- `src/api`：C 端接口模块，和后端 `/user/**` 接口对应。
- `src/stores/auth.ts`：Pinia 登录态。
- `src/utils/storage.ts`：token、用户 ID、邀请人 ID 本地缓存。
- `src/utils/auth.ts`：页面登录守卫和金额格式化。
- `src/pages`：页面组件。

## 下一步建议

- 补收货地址管理。
- 订单创建页从购物车选择多件商品，而不是只传一个 SKU。
- 接入微信手机号能力或短信验证码绑定手机号。
- 接入微信支付预下单和支付结果轮询。
