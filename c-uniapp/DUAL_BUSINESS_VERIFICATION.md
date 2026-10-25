# 双业务阶段 A · 前端实施与验证记录

日期：2026-10-25（Asia/Shanghai）。分支：`uniapp-dual-business-ui`。基线：`d96f255`（由 `cf295c1` 加入实施方案形成）。

## 交付范围

- 首页无框节气展示及等权双入口，保留真实墨字、节气计算、运营公告、Banner 和弹窗；原搜索、分类、分页、商品详情及购物篮迁至独立商品页。
- 咖啡公开门店、聚合菜单、有效 SKU 规格弹层，游客登录返回保留原页面；以服务端能力判断浏览和加购，不以营业状态代替授权。
- 商品、咖啡两个独立 store、接口和选择状态，共用展示行、业务页签和玻璃浮栏。咖啡数量更新携带版本，冲突重新同步；POST 超时只查询结果，不自动重放。账号失效、退出、切换立即清理账号数据，旧响应不能覆盖新账号。
- 本地仅记住公开门店 ID，支持目录停用后查看已知门店的本人旧购物篮及移出条目；不持久化咖啡购物篮或认证上下文。首次从未访问过门店且目录未配置时，显示明确不可用状态。
- 统一订单只查询 `/user/business-order/list`，由服务端分页；包含关闭、取消、售后和未来待接单状态。展示历史摘要，以 `paidAmountCent` 是否为空区分应付和实付，0 元实付仍正确显示。商品详情路由保留字符串 ID 精度，原付款和收货继续走商品接口。
- 管理后台先获取真实权限上下文，再展示门店、分类、饮品及全部规格。支持首店创建、停售、稳定规格 ID 和现有图片上传；不提供物理删除或开放支付开关。
- 咖啡菜单浮栏、购物篮和购物车均显示“暂未开放”；没有咖啡创建订单或支付 API，也不会进入商品确认页。

未修改 Java、SQL、后端配置、支付密钥、正式 API 域名或共用接口契约。没有引入新框架，也没有生产模拟数据或请求失败降级为示例数据。

## 自动验证

构建使用项目现有 HTTPS 域名 `https://api.jinlin24th.cn`。设置构建环境变量仅用于满足现有构建校验，构建本身不访问该域名。

| 验证 | 结果 |
| --- | --- |
| 修改前小程序 `npm run type-check` | 通过 |
| 修改前小程序 `VITE_API_BASE_URL=https://api.jinlin24th.cn npm run build:mp-weixin` | 通过 |
| 修改后小程序 `npm run type-check` | 通过 |
| 修改后小程序 `npm run build:h5`（指定上述环境变量） | 通过 |
| 修改后小程序 `npm run build:mp-weixin`（指定上述环境变量） | 通过 |
| `npm run test:solar-terms` | 3 项通过 |
| `npm run test:shopping-ux` | 18 个浏览器场景通过 |
| `npm run test:dual-business-ux` | 26 个浏览器场景通过 |
| 管理端 `npm run build` | 通过；仍有现有主包体积提示 |
| 管理端 `npm test` | 32 项通过 |
| `git diff --check` | 通过 |

浏览器回归使用本机 Chrome，全部业务请求只在本地拦截；不会向真实服务发送订单、支付或后台写入。开发测试数据只位于 `tests/`，不进入正式包。原购物回归的首页商品行为已迁移至商品页验证；订单列表用例已更新至统一契约，保留原行为断言。

新增回归覆盖独立金额与结算快照、不可售行、数量与版本更新、防连续提交、冲突、超时、服务端拒绝、游客返回、账号切换迟到响应、错误与空状态、门店能力、目录停用后的旧篮处理、订单分页与筛选竞态、真实支付依据、长 ID、后台直接 URL 权限、只读权限、首店创建与稳定规格保存。响应中的最大数量和版本边界仍由服务端校验，前端不能替代后端验证。

## 复现浏览器回归

从仓库根目录分别启动两个本地服务：

```sh
VITE_API_BASE_URL=http://127.0.0.1:7878 npm --prefix c-uniapp run dev:h5 -- --host 127.0.0.1 --port 5174
npm --prefix admin-web run dev -- --host 127.0.0.1 --port 5175
```

另一个终端运行：

```sh
# Playwright 未安装在项目内时，将 PLAYWRIGHT_MODULE 指向已有安装。
npm --prefix c-uniapp run test:shopping-ux
npm --prefix c-uniapp run test:dual-business-ux
```

可用 `UX_BASE_URL`、`UX_ADMIN_BASE_URL` 覆盖本地前端地址，用 `UX_SCREENSHOT_DIR` 指定截图目录。`UX_BROWSER_CHANNEL` 默认 `chrome`。

## 截图

本机截图目录：

`/Users/boyforest/.codex/visualizations/2026/10/25/01a1726f-d1b4-7d62-b02e-7a12006be831/dual-business/`

| 文件 | 内容 |
| --- | --- |
| `dual-home.png` | 首页无框节气与双入口 |
| `dual-goods-cart.png` | 商品购物车与独立金额 |
| `dual-coffee-cart-disabled.png` | 咖啡购物车、不可售条目与首发禁用状态 |
| `dual-coffee-spec.png` | 游客登录返回后保留的规格弹层 |
| `dual-coffee-basket-disabled.png` | 咖啡购物篮弹层及禁用按钮 |
| `dual-coffee-menu-fallback.png` | 咖啡菜单及不使用背景模糊的浮栏回退 |
| `dual-orders-goods.png` | 商品订单分类与历史摘要 |
| `dual-orders-coffee-empty.png` | 阶段 A 咖啡订单真实空状态的契约测试 |
| `dual-admin-coffee-menu.png` | 现有管理后台中的咖啡维护页 |

截图中的门店、商品和订单是明确的测试夹具，不代表生产记录。以上路径只用于本机验收产物，不是正式页面的运行时资源依赖。

## 尚待真实联调与真机验收

本次检查时 `http://127.0.0.1:7878/user/coffee/store` 无法连接，且此前端分支的基线未包含新增后端接口实现。因此未宣称完成真实数据库持久化、真实管理员权限校验或前后端全链路联调。

后端阶段 A 可用后，须验证公开菜单、同 SKU 合并、并发版本、99 数量上限、停售后旧购物篮、跨账号拒绝、后台保存后菜单刷新、历史订单摘要和全局分页。所有授权和可售校验必须在服务端独立执行。

微信开发者工具以及 iOS／Android 真机尚未执行：需检查原生 TabBar 与子页导航、胶囊及返回、玻璃回退、减少动效、弹层和底部安全区。H5 回归及小程序编译成功不能代替该项验收。此分支尚未合并或发布。
