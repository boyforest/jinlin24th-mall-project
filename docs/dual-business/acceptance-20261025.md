# 双业务阶段 A · 联合验收记录

日期：2026-10-25（Asia/Shanghai）。验收基线：`main cf295c1`；前端交付 `uniapp-dual-business-ui 7ab9a92`，后端交付 `uniapp-coffee-backend c34b509`。两个分支先在独立工作目录、`uniapp-dual-business-acceptance` 分支集成，验收通过后再合入 main。

## 结论与范围

阶段 A 的代码审查、自动化检查、存量数据库迁移演练及本地真实前后端联调通过，可合并代码。此结论不等于已发布服务、小程序已上传审核，或已完成微信开发者工具及 iOS／Android 真机验收。

咖啡仍只支持浏览与独立加购，未提供创建咖啡订单、支付、退款、接单或叫号接口。商品继续沿用原有交易链路。没有连接或修改生产数据库。

## 验收中发现并修复的问题

真实服务联调时，统一订单页的“全部”请求返回 HTTP 400。原因是 `uni.request` 把对象中值为 undefined 的可选参数序列化为空字符串，后端正确拒绝空的业务／状态筛选。此前模拟接口未校验这些参数，未能暴露问题。

修复提交：`7c2ab3c fix: 省略统一订单查询中的未选筛选参数`。

- `c-uniapp/src/api/businessOrder.ts` 在发请求前移除值为 undefined 的查询项，保持未选择的筛选参数真正缺省。
- `c-uniapp/tests/dual-business-ux.cjs` 模拟服务同步拒绝空筛选值，现有订单分页、状态切换、竞态与详情跳转场景共同覆盖该问题。
- 修复后重新执行小程序类型检查、H5／微信小程序构建、双业务及原购物浏览器回归；真实服务的历史商品订单与咖啡空分类均可正常显示。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 集成后 `mvn test` | 230 项，失败／错误／跳过均为 0 |
| `bash scripts/test-coffee-mysql.sh` | 通过：一次性 MySQL 8 的存量迁移、重复执行、新安装、显式演示数据及咖啡数据库／HTTP 测试 |
| `mvn -DskipTests clean package` | 通过；交付 JAR 不含本地 `application-dev.yml` |
| 小程序 `npm run type-check` | 通过 |
| 小程序 H5／mp-weixin 构建 | 通过；生产构建使用现有 HTTPS API 域名 |
| `npm run test:solar-terms` | 3 项通过 |
| `npm run test:dual-business-ux` | 26 个浏览器场景通过；包含修复后的空筛选校验 |
| `npm run test:shopping-ux` | 18 个浏览器场景通过 |
| 管理后台 `npm run build`／`npm test` | 构建通过，32 项测试通过；仍有主包体积提示 |
| 本地真实服务联合验收 | 通过，详情见下文 |
| `git diff --check` | 通过 |

原有配置安全测试要求被忽略的本地 dev profile，验收时只生成无业务连接／凭证的最小调试开关文件，测试结束移除，再 clean package；未降低测试断言或将本地 profile 打入 JAR。

## 真实前后端联调

运行集成分支打包的 Spring Boot JAR、独立临时 MySQL 8 和 Redis，配合实际 H5 与 React 页面。仅使用本地临时账号、签名 token 和 Redis 会话，不连接微信登录／支付服务，不拦截或伪造业务 API 响应。

1. 真正验证 JWT、Redis 会话与 SUPER_ADMIN 权限；普通管理员被拒绝，匿名不能读咖啡购物车。
2. 从实际 React 页面建立首个门店、分类；管理接口建立饮品和 SKU，管理页面刷新后可见。
3. 实际 Vue 咖啡页面读到同一菜单，选择规格加购，记录写入数据库；另一用户的购物车为空，跨账号修改返回 404。
4. 实际购物车页面修改数量成功，旧版本更新返回 409；咖啡结算按钮保持禁用。
5. 管理接口调价后购物车刷新显示新金额，重载页面后数量保留。
6. 实际订单页读到预置的历史商品订单快照与正确应付金额；咖啡分类返回真实空页。
7. 咖啡创建订单路径不可用；移出购物车后数据库查询为空；整个联调未出现页面 JavaScript 异常。

本机验收截图与原始结果保存在：

`/Users/boyforest/.codex/visualizations/2026/10/25/01a16cb4-05bf-7200-9245-86c65f30882a-acceptance/`

其中 `jinlin-acceptance-integration.json` 为真实服务结果，`real-coffee-cart.png`、`real-coffee-orders-empty.png` 为真实服务截图；其他 `dual-*.png` 来自明确拦截接口的浏览器回归夹具，不能当作生产数据。测试数据库、缓存和服务在验收结束后销毁，截图不作为运行时资源。

## 数据兼容与发布要求

- 新迁移只增加五张 `coffee_*` 表，没有删除／重建旧业务表。存量升级演练中的历史商品订单及金额在迁移与重复迁移后保留。
- 对比基线，原 `OrderServiceImpl`、`WxPayServiceImpl` 未改；原商品支付、库存及分销的自动化回归通过。此次没有执行真实微信交易。
- 正式更新前备份数据库，在目标库执行 `sql/migrations/20261024_coffee_catalog_cart.sql`，不要重新初始化数据库或删除数据卷。
- 后端先部署，目录默认关闭；授权管理员维护真实门店／菜单，启用 `COFFEE_CATALOG_ENABLED` 后完成环境验收，再发布前端。开目录不会开放咖啡下单支付。
- 部署与回退使用 [后端部署说明](backend-deployment.md)。仍需在微信开发者工具和真机检查原生导航、返回、玻璃回退、弹层、安全区以及目标服务器连通性。

## 合并处理

前后端独立功能提交及本次验收修复保留在 Git 历史中。main 原有 `.gitignore` 改动及未跟踪 `docs/tools/` 不纳入功能提交；恢复的四份方案应与已有文档提交一致，合并后由 Git 正式跟踪。

本轮授权范围是验收与合并到本地 main；没有自动推送远端、执行生产迁移、部署服务或上传小程序。
