# 后端实施文档

日期：2026-10-26。必须先读 [README](README.md)、[讨论稿 V4](coffee-workbench-rbac-discussion.md)、[共用契约](implementation-contract.md)。本文件负责后端、数据库升级与部署说明，不修改前端，不部署生产。

## 1. 必须调用 stop-that-shit

开始时调用 `$stop-that-shit change -- 按本文件与共用契约完成后端实施`，并读取 [SKILL.md](/Users/boyforest/.codex/skills/stop-that-shit/SKILL.md)。沿用 Spring Boot、MyBatis-Plus、MySQL、Redis 和已有微信 SDK；不改微服务、不引入新的权限框架或消息基础设施。

只实现产品稿和契约要求的能力。权限检查、支付验签、退款幂等和数据保留是必要工作；少写代码不能省略它们。现有库能解决的问题不另建通用组件；完成验证后结束，不擅自增加客户端、统计平台、自动拒单或核销集成。

## 2. 工作区与已有文件修改

独立工作目录，分支 `uniapp-coffee-workbench-backend`。只修改 `src/`、`sql/`、必要配置/脚本和后端文档。前端按契约消费；路径/字段确需调整，先更新本目录共用契约并交接，不静默改名。

下列文件实际已存在，路径相对仓库根目录：

| 文件 | 修改方法 |
| --- | --- |
| `src/main/java/com/jinlin24th/jinlin/common/auth/AdminJwtInterceptor.java` | 保留 token 类型、受限改密和会话检查，补启用账号、实时角色权限；接入业务授权 |
| `src/main/java/com/jinlin24th/jinlin/common/auth/AuthSessionService.java` | 复用管理员会话撤销，支撑停用、重置密码、授权变更后的立即失效 |
| `src/main/java/com/jinlin24th/jinlin/common/auth/CoffeeAdminAccessService.java` | 从只认超管扩展到权限+绑定门店校验，不能移除数据范围保护 |
| `src/main/java/com/jinlin24th/jinlin/common/config/WebMvcConfig.java` | 保留用户与公开目录边界；管理端权限检查在认证之后、业务操作之前 |
| `src/main/java/com/jinlin24th/jinlin/service/impl/SysAdminServiceImpl.java` | 复用登录/角色/权限读取及改密，不把角色缓存写进 token 作为唯一依据 |
| `src/main/java/com/jinlin24th/jinlin/controller/AdminAuthController.java` | 增加真实上下文；保留登录、自助改密、退出和现有账号选项兼容 |
| `src/main/java/com/jinlin24th/jinlin/controller/AdminCoffeeController.java` | 按原接口结构补门店与模块权限，首店创建仅超管 |
| `src/main/java/com/jinlin24th/jinlin/service/impl/CoffeeCatalogServiceImpl.java` | 上下文返回授权店；菜单/店务按资源实际归属校验；下单开关不因菜单配置自动打开 |
| `src/main/java/com/jinlin24th/jinlin/common/log/OperationLogInterceptor.java` | 必要时补敏感字段过滤，复用已有日志实体与存储；不记录密码/token |
| `src/main/java/com/jinlin24th/jinlin/common/config/WxMaConfiguration.java` | 原则上复用 WxMaService，不重复实现 access_token 管理 |
| `src/main/resources/application.yml` 及 prod/dev 示例 | 增加独立咖啡配置和默认关闭的点单开关；只写环境变量引用 |
| `docker-compose.yml`、`.env.example` | 仅转发必要咖啡支付/通知配置，不改已有商品商户值 |
| `sql/jinlin24th_init.sql` | 只同步新安装所需结构；存量升级另走增量 SQL |

原商品后台各控制器、`DistributionController.java` 和 `WxPayController.java` 要纳入权限覆盖，不能只保护新接口。优先集中权限映射/注解，按既有路由逐项核对；只改必要的授权，不重写原商品交易服务。

## 3. 建议新增文件与职责

以下为待新增文件名建议，均位于 `src/main/java/com/jinlin24th/jinlin/`；按实际复用可合并简单类，不要求机械增加每个文件。

| 文件/组 | 职责 |
| --- | --- |
| `common/auth/AdminPermissionService.java` | 固定角色权限解析、门店范围、现有后台端点授权；可配最小注解/拦截器，不建权限平台 |
| `controller/AdminAccountController.java`、`service/AdminAccountService.java` 及实现 | 超管账号查询、创建、资料、授权、启停用和密码重置 |
| `pojo/entity/SysAdminCoffeeStore.java`、对应 Mapper | 账号与实际咖啡门店绑定 |
| `controller/AdminCoffeeOrderController.java`、`service/CoffeeOrderService.java` 及实现 | 工作台、订单、人工接单/拒单、制作取餐动作及操作记录 |
| `controller/UserCoffeeOrderController.java` | 仅后端交易API：本人创建、查询、发起支付、取消未付款单 |
| `controller/CoffeePaymentNotifyController.java` | 咖啡专用支付/退款回调 |
| `common/config/CoffeePayConfig.java`、`service/CoffeePaymentService.java` 及实现 | 独立咖啡支付配置、渠道调用、查单、关单和全额退款 |
| `service/CoffeeLedgerService.java` 及实现 | 支付/退款发生时间口径统计，不从制作状态猜收入 |
| `service/CoffeeNotificationService.java` 及必要任务执行类 | 制作完成任务持久化与实际微信模板发送 |
| 咖啡订单/明细/支付/退款/事件/取餐序号/通知实体与 Mapper | 按下文表结构承载对应事实，不滥用商品表 |
| 对应 DTO/VO | 严格校验输入，返回契约字段，不暴露实体中的凭证与个人信息 |

不复制一整套商品商城服务。咖啡支付可复用通用签名与HTTP工具，但配置实例、商户、订单记录和回调必须独立。

## 4. RBAC 和超管账号页配套接口

1. 完成契约中的三角色和上下文；按已有权限表增量写入角色/授权，使用编码查询真实 ID，不能假设新角色 ID。
2. 将原商品、支付管理、导出、客户/跟进、账号选项等后台接口完整归类；共享上传有明确范围；未归类接口不能给普通管理员默认放行。
3. 咖啡详情、动作、列表和流水使用服务端门店绑定范围，不能信任 query/storeId；越界资源不泄露存在性。
4. 超管账号接口所有操作再次检查超管角色，不能由客户端提交“我是超管”或任意权限数组授权。
5. 创建/重置密码 BCrypt 保存，临时密码至少8位并要求首次改密。用户名不改，账号不物理删除。
6. 停用/重置/变更角色或门店撤销已有会话，服务端认证也检查账号有效；防止账号停用后旧 token 继续操作。
7. 最后一个启用超管保护需要事务锁/等效数据库串行化，验证两个并发降级请求不能移除所有超管。
8. 账号操作写既有 `sys_operation_log` 或复用服务的安全结构化记录。现有拦截器在 MQ 未开启时会跳过写库，不能把它当作已完成可靠审计；新增账号操作使用最小同步落库路径，不为此强制部署 MQ，不复制密码请求体。

## 5. 数据结构与存量升级

建议增加下面八张表，具体列定义由实现时写入迁移并同步契约需要的字段；不得无目的拆更多表。

| 新表 | 必要事实与约束 |
| --- | --- |
| `sys_admin_coffee_store` | admin_id/store_id 唯一绑定，首版每咖啡账号一个门店；外键和索引 |
| `coffee_order` | 用户、门店、业务订单号、创建幂等键/参数摘要、三个状态维度、金额快照、取餐日期号码、版本、各阶段时间；用户+request_key唯一，订单号唯一 |
| `coffee_order_item` | 订单归属、SKU/名称/规格/图片/单价/数量/行金额快照，不复用商品订单明细 |
| `coffee_payment` | 咖啡订单、商户标识、渠道支付单号、实付金额与成功时间、有效期、查关单状态；稳定商户订单号和渠道单号唯一 |
| `coffee_refund` | 唯一整单退款意图、稳定退款号、原因/操作者、渠道状态及成功时间；一单不重复全额退款 |
| `coffee_order_event` | 履约状态变更、操作者、时间和原因；与有效状态更新同事务保存 |
| `coffee_pickup_sequence` | 门店+北京时间日期唯一序号，用于并发取餐号生成；号码不回收 |
| `coffee_notification_task` | 订单+制作完成事件+模板类型唯一任务、发送状态/时间/安全错误原因，避免重启丢任务 |

保留已有五张 coffee_* 目录/购物车表及所有商品、分销、支付、账号数据；不把原商品订单表改成混用表，不重置已有 admin 密码或角色。已有旧角色先盘点，无法明确迁移的账号列出交接，不擅自提权；未处理账号不能作为生产分权迁移已完成。

新增 `sql/migrations/20261026_coffee_workbench_rbac.sql`，优先新增表和按编码幂等写角色/权限。数据库用整数分，单价/数量/总额做范围与溢出检查，ID、版本满足JS安全约定。幂等建表不会自动修复同名结构漂移，DDL也不能承诺整体事务回滚。

本地联调和生产迁移均先备份，再执行增量脚本，核对旧表记录与代表性历史订单金额。不可执行初始化SQL覆盖已有库、删除数据卷或运行演示种子替代迁移。回退先关闭新下单、回退应用，保留新表和已有咖啡交易数据以处理收退款。

## 6. 咖啡交易与人工履约

按共用契约实现，不给普通管理员绕过支付确认的“造已支付单”接口。

- 创建校验门店、目录、SKU组合、数量和菜单版本，服务端定价并保存完整快照；同用户同 requestKey 去重，不能同键不同参数覆盖。
- 取餐号并发生成，门店+日期+号唯一；已取消和拒单不复用。先保证支付确认后工作台有号可查，不依赖前端生成。
- 验证咖啡商户与原商品商户隔离，回调校验签名、商户、AppID、币种、订单号及金额；客户端支付成功回调不能代替到账确认。
- 正确处理重复/乱序回调、支付与关闭竞争、退款与接单/制作竞争。已付款但订单失效不能静默变成未付款，应持久化异常并走可追踪退款处理。
- 人工 accept、reject 与各制作动作带 expectedVersion 做数据库条件更新，成功才写事件。接单后仍需单独开始制作；按整单完成、整单取走。
- 拒单与退款先可靠保存退款意图，再事务外调用渠道，不能长时间持有数据库锁等网络。超时先用稳定单号查询，不换新退款号盲目重发；重启后能恢复未完成退款处理。
- 全额退款金额从成功支付记录读取，不接受客户端金额；受理、处理中、失败、最终成功分别记录。退款失败不恢复成可继续制作的普通订单。
- 门店暂停只挡新建；回调、查询、退款和已支付单处理继续可用。未支付到期用既有有效期规则，咖啡专用任务不能误取消商品单。已付款待接单没有确认的自动取消时限。

渠道查单/补偿及任务执行选择一套最小可恢复机制，复用现有调度能力即可。不引入额外队列平台、不同时做两套未确认的支付模型；测试替身只在测试配置中使用。

## 7. 流水、提醒与首页

流水从真实支付成功/退款成功记录按北京时间统计，允许净收款为负；不含商品商户，不把订单创建或制作完成当作收款，不声称等于银行到账。

ready 动作与唯一通知任务同事务提交；事务外通过已配置的 WxMaService 调用实际可用模板。未配置、未授权、确定失败、结果未知分别记录，不把日志打印当发送成功。网络结果未知不盲目重复发送，通知失败不阻塞待取餐与确认取走。本轮不新增订阅授权UI，未具备真实用户授权时明确记录联调边界。

首页纯展示复用现有广告表/查询，新增位置无需新API/改表。不扩展营销平台；只补现有管理接口权限，保留公告/弹窗及既有有效期排序语义。首页公共运营暂只超管可改。

## 8. 测试、交付与上线边界

联调前阅读[小程序历史问题与交付必查项](miniprogram-regression.md)。上轮统一订单接口出现过404，须核对前端实际 API 地址与该环境的后端部署版本，验证现有 `/user/business-order/list` 及本轮新增接口真实可用；请求失败不能伪装空列表。后端测试通过不代表小程序原生编译/渲染通过，不擅自修改前端构建配置。

- 在隔离测试库验证账号全链路、三角色直接请求、门店越权、旧会话撤销和并发最后超管保护。
- 验证订单金额/快照/幂等创建、并发取餐号、支付回调验签与商户/金额不符、回调重放和乱序、支付关单竞争、接单拒单竞争、整单制作取餐、退款幂等/超时恢复/失败、通知任务重启恢复及跨日流水。
- 验证跨日未完成队列、分页不会漏单，广告有效期/排序保留，原商品交易/库存/分销/支付回归继续通过。
- 复用现有 `src/test/java/com/jinlin24th/jinlin/coffee/` 模式；按真实需要增加账号权限、交易状态、支付退款测试。MySQL事务和约束必须在一次性MySQL验证，不能只依靠H2或mock。
- 执行 `./mvnw test`、`./mvnw -DskipTests clean package`，以及现有 `scripts/test-coffee-mysql.sh`；必要时扩展脚本覆盖新增迁移与MySQL测试，不另建重复测试系统。适配已有阶段A断言，未改的兼容行为仍保留。
- 生成最小本地测试配置运行后，打发布包前暂时移出 `application-dev.yml` 并 clean；检查最终JAR没有本地密码配置。不要读取/输出真实密钥来证明配置存在。
- 新增本目录 `backend-verification.md` 和 `backend-deployment.md`，记录新表、增量迁移、测试结果、接口变更、未验证项及发布回退。
- 部署说明必须写：生产启用prod，DB密码从 `${DB_PASSWORD}` 读取；本地临时明文配置不得提交/打包；备份后增量迁移；原商品商户不覆盖；咖啡下单开关默认关闭。

不连接生产做试验，不主动发起真实扣款、退款或向真实顾客推送测试消息。用隔离环境/测试替身验证自动化，再如实标注尚待真实微信链路验收。交付只提交本端文件，不自动合并、推送或部署。
