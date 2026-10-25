# 咖啡工作台后端升级与部署

日期：2026-10-26。分支 `uniapp-coffee-workbench-backend`，共同文档基线 `7418e4e`。本文是操作交接，尚未执行生产部署、生产迁移或真实渠道交易。

## 升级顺序

1. 备份数据库、当前后端包和环境变量。确认旧商品订单、管理员账号和 BCrypt 哈希可恢复。
2. 在原环境执行下方只读盘点。旧角色、旧权限关联、旧账号密码原样保留；未知角色不批量改成商品管理员或超管。
3. 已有阶段 A 数据库执行 `sql/migrations/20261026_coffee_workbench_rbac.sql`。尚未安装阶段 A 的库先执行 `20261024_coffee_catalog_cart.sql`。不要对存量库重新执行整个初始化文件。
4. 迁移新增八张表：`sys_admin_coffee_store`，以及 `coffee_order/order_item/payment/refund/order_event/pickup_sequence/notification_task`。按编码查找真实角色/权限 ID，幂等补充固定角色和权限，不覆盖旧授权。新安装的 `sql/jinlin24th_init.sql` 已同步，部署 SOURCE 入口继续使用该文件。
5. 使用 `SPRING_PROFILES_ACTIVE=prod`，数据库密码仍为 `${DB_PASSWORD}` 注入。替换后端包后先用超管确认上下文、账号维护和原商品接口；再创建绑定真实门店的咖啡管理员。首次创建/重置的账号必须通过原有改密接口取得完整 token。
6. 保持 `COFFEE_ORDERING_ENABLED=false`。只在后续顾客入口、商户付款/退款和消息接入验收完成后再考虑开放。

盘点 SQL（不查询密码、哈希或 token）：

```sql
SELECT a.id,a.username,a.status,a.must_change_pwd,
       GROUP_CONCAT(r.role_code ORDER BY r.sort,r.id) AS assigned_roles,
       GROUP_CONCAT(CASE WHEN r.status=1 THEN r.role_code END) AS enabled_roles
FROM sys_admin a
LEFT JOIN sys_admin_role ar ON ar.admin_id=a.id
LEFT JOIN sys_role r ON r.id=ar.role_id
WHERE a.deleted=0
GROUP BY a.id,a.username,a.status,a.must_change_pwd;
SELECT r.role_code,p.permission_code
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id=r.id
LEFT JOIN sys_permission p ON p.id=rp.permission_id
ORDER BY r.role_code,p.permission_code;
```

固定角色由服务端执行固定权限集合。`GOODS_ADMIN` 无共享首页编辑权；`COFFEE_ADMIN` 只能操作绑定门店；其他旧角色继续按数据库中已有有效角色的权限处理。账号列表如实列出存储的角色，包括停用的旧角色；鉴权上下文只使用启用角色。授权编辑才把目标改为首版单角色。

## 配置

完整环境变量见根目录 `.env.example`，Compose 已转发。不修改原 `WX_PAY_*` 商品商户配置，不把咖啡数据导入商品订单/库存/佣金表。

| 配置 | 用途 |
| --- | --- |
| `COFFEE_CATALOG_ENABLED` | 既有目录/购物车开放开关 |
| `COFFEE_ORDERING_ENABLED=false` | 新建订单、新发起支付；关闭不影响已有履约、查询、回调、退款 |
| `COFFEE_PAYMENT_ENABLED=false` | 独立渠道客户端；正式存在待处理渠道单时须保持启用，仅用 ordering 开关停止新交易 |
| `COFFEE_PAY_MCH_ID/APP_ID/API_V3_KEY/SERIAL_NO` | 独立咖啡商户、小程序、32 字节 APIv3 密钥、商户证书序列号 |
| `COFFEE_PAY_PRIVATE_KEY_PATH` | 容器内商户私钥路径，使用部署现有密钥挂载方式 |
| `COFFEE_PAY_PLATFORM_PUBLIC_KEY_ID/PATH` | 微信平台公钥 ID 和公钥文件路径，不能填商户证书序列号 |
| `COFFEE_PAY_NOTIFY_URL` | HTTPS `/api/coffee/payment/notify` |
| `COFFEE_PAY_REFUND_NOTIFY_URL` | HTTPS `/api/coffee/refund/notify` |
| `COFFEE_READY_TEMPLATE_ID` | 取餐订阅模板；未配置时持久化为 SKIPPED |
| `COFFEE_READY_PICKUP_FIELD/STORE_FIELD/TIME_FIELD` | 实际模板字段名，默认 `character_string1/thing2/time3`，部署前对照模板 |

渠道启用时校验必填配置并读取密钥；不从商品客户端取配置。每笔支付保存商户和 AppID 快照；处理历史单时校验当前配置仍匹配。已有未结单时不能直接切换商户身份；密钥轮换应继续属于原商户。支付有效期继续使用 `app.order.timeout-cancel-seconds` / `ORDER_TIMEOUT_CANCEL_SECONDS`。

## 恢复及处理口径

- 每 15 秒按持久化记录扫描到期未付款单、待查询支付、待确认退款和 PENDING 提醒；每类一批最多 50 条。已有应用调度承担恢复，没有新增 MQ 或其他平台。进程重启后继续扫描；多实例通过数据库锁和下一次查询时间占位。
- 未付款过期会登记取消并关单；已支付待接单不会自动拒单或退款。取消与付款竞争按可信渠道结果处理，失效订单收到收款会留存支付事实并登记全额退款。
- 退款意图先提交再访问渠道。每单只有一个完整退款意图和稳定退款号；先查原退款号，明确不存在时才以同一号码申请。申请受理/PROCESSING 仍为 PENDING，成功只认验签回调或验签查询的 SUCCESS。网络不确定保持 PENDING，按原单号恢复。
- 渠道 CLOSED/ABNORMAL 记 FAILED，订单保持冻结，不自动恢复制作或生成另一个退款号。需要操作员在商户侧核查；后续可信 SUCCESS 仍可确认。重复原请求键可查询同一个意图，不意味着可发起第二笔退款。
- READY 与唯一提醒任务同事务提交。发送前把任务提交为 UNKNOWN，再事务外调用现有 WxMaService；成功受理为 SENT，缺模板/缺收件人/微信明确无授权为 SKIPPED，明确失败为 FAILED，网络不确定或发送中崩溃为 UNKNOWN。UNKNOWN 不盲目重发，需要核查微信侧请求结果。消息失败不会回滚 READY 或阻挡取餐。SENT 不代表顾客已看到横幅。
- 登录会话使用数据库规范账号名，避免 MySQL 不区分大小写时产生大小写别名会话；旧 token 的账号名必须与当前账号一致。账号停用、授权变更、重置密码复用 Redis 撤销旧会话；Redis 撤销失败则账号变更事务失败。账号成功变更审计与数据同步写入 `sys_operation_log`，不依赖 MQ；只记录操作者、目标 ID、动作，不记录密码/哈希/token。最后启用超管的变更使用全局角色行锁和 READ_COMMITTED 下的最新计数。

## 上线前后与回退

先按 [验证记录](backend-verification.md) 重跑测试，在授权的测试商户环境验收付款、取消竞争、全额退款、回调、恢复及真实订阅授权。本轮没有顾客支付页面或授权弹窗，不能把后台接口通过当成微信真机交易闭环通过。

上线后检查账号权限、绑定门店、四列未完成订单和净流水；核对 PENDING/FAILED 退款及 UNKNOWN 提醒，不手动修改交易成功字段。流水按北京时间的支付成功时间和退款成功时间独立归属；净额可为负，不是结算或利润。

回退首先关闭 `COFFEE_ORDERING_ENABLED`。已有咖啡交易时保留能处理原回调、退款与恢复的新后端，允许前端入口先回退。不要删除八张新表、已分配取餐号、退款号或事件数据；不能直接替换成不认识咖啡回调的旧包并遗留待退款资金。
