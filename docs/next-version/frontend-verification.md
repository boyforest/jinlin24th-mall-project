# 本轮前端实施与验证记录

日期：2026-10-26。状态：前端实现与自动化验证完成；真实后端联调、完整原生验收和真机验收尚未完成。**本记录不代表全链路验收通过，不进入上传或生产发布。**

## 分支和范围

- 分支：`uniapp-coffee-workbench-ui`。
- 独立目录：`/Users/boyforest/Downloads/com.jinlin24th.www-coffee-workbench-ui`。
- 共同基线：`7418e4e`。包含 `63e46ef` 的 WXSS 修复。
- `979c393`：首页单张主推、推荐养护与后台位置说明。
- `eb1aba0`：服务端权限上下文、账号管理、咖啡布局/菜单/工作台/订单与流水，及针对性单元测试。
- 后续提交：分页加载不触发存量新单提示；本记录与浏览器回归。完整 SHA 以本分支 `git log` 为准。
- 使用了项目要求的 [stop-that-shit 技能](/Users/boyforest/.codex/skills/stop-that-shit/SKILL.md)，复用 React/Ant Design、uni-app、Axios 及已有 Playwright；未增加框架或测试平台依赖。
- 仅修改前端及前端验证材料。未修改 Java、SQL、支付配置、共用契约；未合并、推送、部署或上传。原主工作区已有修改保留。

## 已实现

服务端 `/admin/auth/context` 决定菜单、路由授权与登录落点。首次改密后重新读取上下文，安全 redirect 还须具有访问权限；未知旧角色不升级权限。刷新、深链接及返回前台重新确认授权。401 退出、403 保留无权限状态。本地上下文只作展示缓存，ID 保持字符串。商品页面不请求咖啡上下文。

超管 `/system/accounts` 使用真实角色与门店选项，支持服务端筛选分页、创建、名称修改、单角色/门店授权、启停用和临时密码重置。停用、改授权及重置说明会话撤销后果，最后超管错误如实展示；没有账号删除、密码查看或权限勾选编辑器。关闭弹窗清理临时密码，不缓存密码。

咖啡后台三个入口为制作工作台、菜单管理、订单与流水。门店从上下文取得，未绑定时不猜门店 ID，首店仅超管可建。菜单复用原完整保存接口，以 SKU `available` 进行售罄/恢复。暂停接单说明已有支付与有效待支付订单仍需处理。

工作台按 WAIT_ACCEPT、WAIT_MAKE、MAKING、READY 展示四列，平板使用页签。卡片直接展示整单快照规格、杯数、备注、取餐号、跨日日期和阶段等待时间。接单与开始制作分开；动作由服务端 allowedActions 与上下文权限共同控制。拒单/退款核对号码和固定全额，渠道确认前不显示退款成功，取走须确认。写入忙锁、409 重新读取、超时查询而不自动重放。详情展示实际事件、操作者与通知状态。

可见页每轮请求完成后等待 5 秒再刷新；隐藏、卸载及退出停止排新轮询。分页展示剩余并继续查询跨日同列订单，加载旧页和断线重连不触发存量新单提示。失败保留旧卡片并标为可能过期，显示最近成功同步。声音需主动开启，始终保留视觉提示。

订单查询默认今天，提供全部日期、跨日待接单、全部退款中/失败入口；全部日期显式传创建日期范围，避免服务端默认今天截断。流水使用独立日期范围、整数分与上海时区，净额允许为负，并注明非银行结算或利润。

首页保留节气、绿色视觉、双业务入口、公告及弹窗。`home_banner` 只取有效列表第一条；`home_recommend` 只取前六条，两列，单数正常宽度靠左，零条隐藏标题与网格。四个位置独立请求/失败处理，复用现有排序、有效期和跳转语义。tabBar 页面正确使用 switchTab。顾客咖啡下单、支付及订阅授权入口保持原样关闭。

## 命令与结果

运行目录为上述独立工作目录，Node 22.22.2，Chrome headless，浏览器时区 Asia/Shanghai。

| 命令 | 结果 |
| --- | --- |
| `npm --prefix admin-web test` | 36 项通过 |
| `npm --prefix admin-web run build` | 通过；保留现有大 chunk 提示，未借此扩展打包改造 |
| `npm --prefix c-uniapp run type-check` | 通过 |
| `VITE_API_BASE_URL=https://api.jinlin24th.cn npm --prefix c-uniapp run build:h5` | 通过 |
| `VITE_API_BASE_URL=https://api.jinlin24th.cn npm --prefix c-uniapp run build:mp-weixin` | 通过，仅生成本地产物 |
| `npm --prefix c-uniapp run test:dual-business-ux` | 完整 27 场景通过 |
| `npm --prefix c-uniapp run test:shopping-ux` | 完整 18 场景通过 |
| `npm --prefix admin-web run test:coffee-workbench-ux` | 完整 17 场景通过；补充隐藏页轮询停止、分页不响存量提示后对应场景再次通过 |
| `npm --prefix c-uniapp run test:home-marketing-ux` | 12 场景通过 |
| `git diff --check` | 通过 |

生产模式的 H5 构建也要求显式 API 地址，首次不带变量被配置校验拒绝后，按上表补齐并通过。未绕过配置校验。

浏览器复现环境：

```sh
VITE_API_BASE_URL=http://127.0.0.1:7878 npm --prefix c-uniapp run dev:h5 -- --host 127.0.0.1 --port 5184
npm --prefix admin-web run dev -- --host 127.0.0.1 --port 5185
export UX_BASE_URL=http://127.0.0.1:5184
export UX_ADMIN_BASE_URL=http://127.0.0.1:5185
export PLAYWRIGHT_MODULE=/Users/boyforest/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright
export UX_SCREENSHOT_DIR=/tmp/coffee-workbench-ui-evidence
```

上述浏览器测试全部拦截本地 API，使用与契约一致的夹具；不发生真实登录、建号、支付或退款。生产代码不包含夹具成功降级。

覆盖：三角色与未知角色深链接/刷新、伪造本地权限、首次改密、无门店授权、账号创建/授权/重置、最后超管拒绝停用、401/403、整单接单到取走、拒单原因与全额、409 多人冲突、超时后查结果、退款失败可查询、负净流水、跨日分页、断线重连、隐藏/卸载停止轮询、SKU 售罄恢复、公告与弹窗、推荐 0/1/2/3/5/6/7 项及接口有效期排序夹具。

既有回归修正两处异步等待：购物测试等 rpx resize 布局落定再测量（原 main 同样可复现）；订单详情测试等待真实响应而非固定 100ms。未修改结算或顾客订单业务代码。

## 截图

以下六张均为**本地契约夹具**，不是生产数据，不含临时密码：

- [超管账号页](frontend-evidence/admin-accounts-fixture.png)
- [宽屏四列工作台](frontend-evidence/coffee-workbench-wide-fixture.png)
- [平板工作台页签](frontend-evidence/coffee-workbench-tablet-fixture.png)
- [订单与整单退款确认](frontend-evidence/coffee-orders-refund-fixture.png)
- [首页六项推荐](frontend-evidence/home-recommend-6-fixture.png)
- [首页三项推荐（单数末行靠左）](frontend-evidence/home-recommend-3-fixture.png)

## 微信开发者工具与真机

- 实际打开：`/Users/boyforest/Downloads/com.jinlin24th.www-coffee-workbench-ui/c-uniapp/dist/build/mp-weixin/`，不是根配置指向的旧 `dist/dev/mp-weixin/`。
- 包类型：build；首页源码与 `979c393` 一致，后续提交只涉及后台与测试材料。
- 开发者工具：Stable `2.01.2510290`；实际基础库 `3.17.4`；模拟设备 iPhone 12/13 (Pro)，390×844。
- 源码与生成的首页 WXSS 已检查，未引入通配选择器；原 App/商品页的 `63e46ef` 修复保留。
- 首次打开出现工具内部 `TypeError [ERR_INVALID_ARG_TYPE]: path ... undefined`，栈位于工具 `SummerCompiler.getAllPageAndComponent`。核对完整目录和生成时间，清理一次编译缓存后关闭并重新打开该项目，首页正常渲染。未修改 dist 或升级工具/基础库来绕过。
- [实际开发者工具首页截图](frontend-evidence/native-home-no-marketing-devtools.png)：节气、顺时养护、双入口和四项 tabBar 可见。截图未展示运营内容；不能据此判断接口有效列表为空还是请求失败。
- 工具自动化真实调用已返回 `navigateTo:ok` 并核对商品页和咖啡页路径，返回首页成功，购物车 `switchTab:ok`。游客进入统一订单会到登录页，未冒用用户会话继续。
- 后续自动化原生推荐布局探测出现 `Page.getElements timeout`，部分导航返回 `Uncaught [object Object]`，未将这些探测记为通过。测试用 request mock 已移除并关闭本测试项目，未持久化到源码/包。
- **待验收**：原生 0/单数/六项推荐与有效广告跳转、两种购物车的实际点击和返回、登录后统一订单完整路径、原生控制台/网络完整检查；iOS/Android 真机本轮未验证。未连接设备，不生成预览上传或上传版本。

## 真实后端联调

未完成。当前 `127.0.0.1:7878` 的 Java 服务工作目录是原主工作区；本轮后端独立工作区仍在开发，尚未取得可用的隔离 HTTP 服务地址及测试账号。已询问使用位置，但未取得具体联调配置。未向未知数据来源的服务执行账号、订单或退款写入。

后端工作区存在其自己的测试执行结果，但这些不能替代本前端的真实 HTTP 联调，本记录不将其计算为前端通过证据。实现已兼容后端契约补充的工作台继续加载规则：进行中状态、不传日期、同列跨日排序；未改动共用契约。

后续须在隔离测试服务完成：超管真实创建/授权账号、各角色登录与首次改密、账号撤销会话、实际门店菜单、测试交易支付确认后的工作台推进及拒单退款/流水核对。没有进行真实微信支付、退款或订阅消息验收。
