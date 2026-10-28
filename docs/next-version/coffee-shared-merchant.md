# 咖啡顾客下单与首版单商户收款

日期：2026-10-28。分支：`uniapp-coffee-checkout`，基线 `3acc733`。本轮按用户确认，商品和咖啡先使用同一个商户号，后续再考虑咖啡独立收款。

用户已验证过商城下单、支付及回调，但当前咖啡版本尚需审核后取得相应支付接口权限。本轮完成代码接入及本地验证，不发起真实扣款，不提前打开生产开关，不上传审核或部署。

## 收款与业务边界

- 复用商城的 `requestMiniAppPayment` 调用微信付款；后端沿用现有 `WxPayUtil` 的签名、应答验签和通知解密，不再写一套支付协议。
- `COFFEE_PAY_*` 继续表示咖啡渠道的配置空间，**不要求与商品商户号不同**。`.env.example` 使用 Compose 变量引用，让它指向现有 `WX_PAY_*` 及 `WX_MINIAPP_APPID`。非 Compose 部署须显式注入相同值，Spring 不直接加载根目录 `.env`。
- 微信所有资金结算到当前商户对应的结算账户；管理端可以分别统计业务流水，但不表示银行账户分别结算，也没有自动分账。
- 咖啡仍使用 `/api/coffee/payment/notify`、`/api/coffee/refund/notify`。商品继续使用原回调。不得把两套回调地址互换。
- 商户绑定、经营主体、经营范围及小程序类目仍须符合实际审核要求；共用技术配置不代替平台授权。

## 顾客流程

1. 菜单页和购物车读取门店能力。仅在 `placeOrder` 与 `payOnline` 为真、咖啡篮同步成功且有选中可用饮品时进入结算；开关关闭仍可浏览和加购。
2. 结算页重新读取门店、菜单和购物篮，展示下单门店、到店自取、最新商品名称/规格、数量、单价、应付合计和最多255字符备注。不沿用商品收货地址和推荐官结算表单。
3. 创建请求仅发送门店、菜单版本、规格/数量、备注和请求键；金额由后端计算。提交前将请求快照与固定请求键按用户保存到本地。重复点击锁定，超时不自动发起另一个订单。
4. 提交结果不确定时保留原快照；刷新或重新进入也能由用户明确点击“查询原提交结果”，使用同一请求键返回原订单。确定的参数/菜单冲突则重新核对后再提交。已知订单ID时直接转订单详情。
5. 创建成功后仅以原购物篮条目的版本号取消勾选，不删除条目或覆盖其他设备的新修改。服务器拒绝版本过期的更新时保留该条目；原篮内容保留以方便后续调整。
6. 核对应付金额后调用现有微信支付；取消、失败或渠道结果不确定都进入同一订单详情，不再新建订单。若创建后的金额不同，先进入详情供顾客核对，不自动付款。
7. 咖啡订单详情读取服务端支付/退款/制作事实。微信客户端返回成功不把本地状态直接改为已付款；仅服务端确认付款、且未进入退款时展示有效的大号取餐号。
8. 未付款订单可以继续支付或确认取消。已付款后沿用人工接单 → 待制作 → 制作中 → 待取餐 → 已取餐。顾客退款由门店处理，详情展示退款中/成功/待核查，不提供未经约定的自助退款。
9. 页面可见时每5秒刷新进行中订单，隐藏/卸载停止；接口失败保留真实错误和重试入口，不伪装空订单。统一订单列表可进入咖啡详情。

本轮不新增订阅授权弹窗；既有服务端提醒任务继续保留。当前以订单状态页和店员叫号完成取餐体验，不承诺顾客已获得微信弹出提醒。

## 文件与数据变更

| 内容 | 文件 |
| --- | --- |
| 咖啡交易API类型与调用 | `c-uniapp/src/api/coffeeOrder.ts`（新增） |
| 结算与取餐详情 | `c-uniapp/src/pages/coffee-checkout/index.vue`、`coffee-order/index.vue`（新增） |
| 入口与路由 | `c-uniapp/src/pages.json`、`pages/coffee/index.vue`、`pages/cart/index.vue`、`pages/orders/index.vue` |
| 门店关闭原因文案 | `c-uniapp/src/utils/coffee.ts` |
| 共用商户示例与配置说明 | `.env.example`、`docker-compose.yml`；咖啡配置校验文案不再强制“独立商户” |
| 验证 | `c-uniapp/tests/coffee-checkout-ux.cjs`（新增）、原双业务回归更新开关场景、`CoffeeChannelContractTest`补共用商户支付/退款场景 |

**不新增或修改数据库结构，不重置任何原数据。** 新页面直接使用已合并的咖啡交易接口、数据表和状态机；服务器若尚未升级到咖啡工作台版本，仍须先执行[原部署交接](backend-deployment.md)中的增量迁移并备份。切勿因为本轮没有新SQL而跳过上轮尚未执行的迁移。

## 部署配置与开放顺序

先在安全的部署环境填写原商城配置，凭据不放入Git：

```dotenv
# 真实值在服务器环境中配置，不照抄占位值
WX_MINIAPP_APPID=your-miniapp-id
WX_PAY_MCH_ID=your-merchant-id
WX_PAY_API_V3_KEY=your-32-byte-api-v3-key
WX_PAY_SERIAL_NO=your-merchant-certificate-serial
WX_PAY_PLATFORM_PUBLIC_KEY_ID=your-wechat-platform-public-key-id
WX_PAY_PRIVATE_KEY_PATH=/app/certs/your-merchant-private-key.pem
WX_PAY_CERTIFICATE_PATH=/app/certs/your-wechat-platform-public-key.pem
APP_PUBLIC_URL=https://your-api-domain.example.com
```

`.env.example` 的咖啡字段引用这些同一商户凭据，咖啡公钥路径对应现有商品客户端读取的 `WX_PAY_CERTIFICATE_PATH`（该客户端实际读取微信平台公钥，不是商户私钥）。两个回调由 `APP_PUBLIC_URL` 拼接；该变量填写不带末尾斜杠的可公网访问HTTPS地址。

1. 先保持 `COFFEE_ORDERING_ENABLED=false`、`COFFEE_PAYMENT_ENABLED=false`。部署后端、核对现有迁移、设置真实门店菜单，并确认小程序AppID与商户的绑定和授权。
2. 在授权的测试/体验环境启用咖啡渠道与下单，验证门店能力、微信调起、付款回调、延迟到账、取消竞争、拒单退款、重复通知和跨重启恢复。此步骤需要真实权限；本地契约夹具通过不能替代。
3. 核对商品支付/退款仍走原回调，同商户号下两套业务记录不会串单，金额与商户账单一致。进行iOS/Android真机测试，并使用本次build包完成开发者工具检查。
4. 按实际微信审核流程提交版本；所需权限未取得时，保持正式下单关闭。正式开放前确认正在运行的服务支持新接口，且商户权限、回调和退款均已验收。
5. 开放时配置 `COFFEE_CATALOG_ENABLED=true`、`COFFEE_PAYMENT_ENABLED=true`、`COFFEE_ORDERING_ENABLED=true`，维护门店营业状态；仅在运营实际准备就绪的时段开放接单。

若停止接新单，只关闭 `COFFEE_ORDERING_ENABLED`；已有交易时保留渠道启用、回调、退款及恢复服务，不能同时关闭支付渠道让历史交易无人处理。生产使用 `prod` profile，数据库密码从 `${DB_PASSWORD}` 环境变量读取，发布JAR不包含本地 `application-dev.yml`。

## 后续独立商户

未来可以显式替换 `COFFEE_PAY_*`，保留商品配置，但必须先停止新咖啡交易、处理旧商户的未付款/待查询/待退款订单，并核对原资金记录。当前服务会校验支付记录保存的商户/AppID与当前配置一致；不能带着旧商户未结订单直接切换，也不宣称现版本已支持多商户同时恢复。确需共存时再扩展按支付快照选择渠道的能力。

## 验证记录

最终执行结果见同目录的 `coffee-checkout-verification.md`。真实支付权限、商户验收、原生/真机验证必须分别记录，不因本地H5模拟返回成功而标记线上闭环通过。
