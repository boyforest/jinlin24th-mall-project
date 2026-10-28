# 本轮共用契约 · v1

日期：2026-10-26。依据讨论稿 V4，供两个实施对话共同遵守。下列标为新增的接口均待实现。旧接口未明确修改的字段和行为继续兼容。

## 1. 通用约定

- 沿用项目 `Result`：成功 HTTP 200、`code=0`、`message`、`data`；失败保留真实 HTTP 状态及业务码。
- 新接口 ID 为十进制字符串；金额为整数分，字段后缀 `Cent`；版本为非负安全整数；时间 ISO 8601 含时区，业务日期按 `Asia/Shanghai`。
- 分页沿用 `records,total,size,current,pages`，默认 page=1、size=20，size 上限 100。集合返回数组，可空字段明确为 null。
- 管理接口使用管理员 Bearer token。身份、权限和门店归属从服务端取得，不接受客户端提交操作者身份或支付金额授权。
- 401：需要重新登录；403：身份有效但无权；404：资源不存在或不在可见范围；409：状态/版本冲突；400：参数错误。沿用现有业务码；新增细分码由后端统一登记，不靠中文 message 分支。
- 写操作超时先查询结果，不自动重发新增、退款等操作。409 后读取最新状态，不强行覆盖。
- 新建用户名为 3–64 位 ASCII 字母、数字、下划线、点或短横线；临时密码至少 8 字符、最多 72 UTF-8 字节（BCrypt 限制）。requestKey 为 1–64 位 ASCII 字母、数字、下划线或短横线，可直接使用 UUID。remark/reason 最多 255 字符；拒单 reason 上限按最终提交的分类、分隔符与说明合计计算，参数错误保留弹窗和输入，409 则重新读取最新订单；remark 首尾空白会裁剪，空串归一为 null。同一 SKU 重复项合并后数量也必须为 1–99。

## 2. 角色与权限

固定新业务角色 `GOODS_ADMIN`、`COFFEE_ADMIN`，保留 `SUPER_ADMIN`。新建账号一次分配一个角色；咖啡角色首版绑定一个实际门店，其他角色不绑定咖啡门店。旧账号/旧角色不删除，迁移前盘点，不能自动把未知角色升级为商品管理员或超管。

| 模块 | 超管 | 商品管理员 | 咖啡管理员 |
| --- | --- | --- | --- |
| 后台账号及授权 | 全部 | 无 | 无 |
| 原商品、商品订单、库存、仓储、分销、优惠券、客户/跟进 | 全部 | 对应业务 | 无 |
| 商品经营概览、商品支付管理及对账 | 全部 | 对应商品业务 | 无 |
| 首页活动运营 | 全部 | 暂不开放 | 无 |
| 咖啡菜单/店务/订单/流水 | 全部门店 | 无 | 绑定门店 |
| 新建首个咖啡门店 | 允许 | 无 | 无 |
| 图片上传、自助改密、退出登录 | 本人有权操作的范围 | 同左 | 同左 |

权限码复用 `dashboard:view, product:manage, order:manage, inventory:manage, customer:manage, coupon:manage, distribution:manage`；新增最小模块权限：`admin:accounts:manage`、`marketing:manage`、`coffee:menu:read`、`coffee:menu:write`、`coffee:store:write`、`coffee:orders:read`、`coffee:orders:operate`、`coffee:orders:refund`、`coffee:ledger:read`。超管拥有本轮全部权限；其他角色按表分配。不建设逐按钮可配置的权限平台。

商品用户管理、客户跟进与受限账号选项查询归入商品业务；商品支付查询/关闭/退款/账单归入商品订单权限。共享上传不得成为跨店读写资源的入口。未分类的后台业务接口默认拒绝普通业务账号，补齐分类后才放行。

前端菜单和按钮以服务端上下文权限为准；后端每次重新校验启用账号、有效角色和资源归属。账号停用、重置密码、修改角色或绑定门店后，旧会话失效，重新登录取得新权限。不得仅依赖登录时缓存的角色。

## 3. 管理员上下文与账号维护（新增）

保留 `/admin/login`、`PUT /admin/password`、`POST /admin/logout` 原有流程。新增 `GET /admin/auth/context`，返回：

```text
{ adminId, username, realName, roles: string[], permissions: string[],
  stores: [{id,name}], defaultRoute }
```

`defaultRoute`：超管/商品管理员 `/dashboard`，咖啡管理员 `/coffee/workbench`。未知旧角色按实际权限计算可访问落点，无任何有效业务权限则显示无授权，不默认进入商品后台。首次改密受限 token 仍只允许走既有改密流程。

超管账号页前端路径 `/system/accounts`。以下接口只允许启用的超管：

| 方法与路径 | 输入 | 返回 |
| --- | --- | --- |
| GET `/admin/accounts/list` | page,size,keyword?,roleCode?,status? | `Page<Account>` |
| GET `/admin/accounts/roles` | 无 | `[{code,name,description,permissions[]}]`，三种可分配角色 |
| GET `/admin/accounts/coffee-stores` | 无 | `[{id,name}]`，实际可绑定门店 |
| POST `/admin/accounts` | username,realName?,temporaryPassword,roleCode,storeId? | Account |
| PUT `/admin/accounts/{id}` | realName | Account |
| PUT `/admin/accounts/{id}/access` | roleCode,storeId? | Account；撤销目标旧会话 |
| PUT `/admin/accounts/{id}/status` | status:0或1 | Account；停用撤销会话 |
| POST `/admin/accounts/{id}/reset-password` | temporaryPassword | true；要求下次改密并撤销旧会话 |

`Account={id,username,realName,roles:string[],storeId,storeName,status,mustChangePwd,lastLoginTime}`。旧账号可能有多角色，列表如实展示；超管明确编辑授权时才改成首版单角色。密码不返回。临时密码至少 8 位，与现有首次改密要求一致；当前本地测试账号密码不自动改动。

用户名唯一且创建后不可改；账号不物理删除。最后一个启用超管不能被停用或降级，须考虑并发修改。新账号或重置密码设置 `mustChangePwd=1`。管理操作记录目标和操作人，不记录明文密码、密码哈希或完整 token。

## 4. 咖啡上下文与店务

继续使用 `GET /admin/coffee/context` 的 `stores,permissions` 结构，返回调用者真实可管理门店；商品管理员返回 403，绑定为空的咖啡账号不隐式获得首店权限。

原门店/分类/饮品/SKU 维护接口沿用阶段 A 字段，增加门店范围校验。首店创建仍只允许超管。咖啡 UI 使用 `/coffee/menu`，旧 `/coffee-menu` 按权限重定向，不再挂到商品导航。

门店营业状态继续使用现有 `OPEN/CLOSED/PAUSED`，不新增重复开关字段；修改仍走原门店 PUT。现有菜单的上架状态和 SKU `available` 用于下架、售罄及恢复，无需新建售罄 API。暂停后不允许新建订单，已有支付订单继续人工处理。

## 5. 咖啡订单状态与展示对象

独立咖啡订单分开保存三个维度：

| 维度 | 值 |
| --- | --- |
| fulfillmentStatus | `WAIT_PAYMENT, WAIT_ACCEPT, WAIT_MAKE, MAKING, READY, COMPLETED, CANCELLED, REJECTED` |
| paymentStatus | `UNPAID, PAID, CLOSED` |
| refundStatus | `NONE, PENDING, SUCCESS, FAILED` |

支付成功由验签回调或可信渠道查询推进至 `WAIT_ACCEPT`；人工 accept 到 `WAIT_MAKE`；start 到 `MAKING`；ready 到 `READY`；complete 到 `COMPLETED`。接单不能与开始制作合并。已退款仍保留原成功支付依据；拒单用 REJECTED，其他取消履约的退款保留 CANCELLED，已取餐售后保留 COMPLETED 并单独显示退款状态。

`CoffeeOrder` 至少返回：

```text
id, orderNo, storeId, storeName, pickupCode, pickupDate,
fulfillmentStatus, paymentStatus, refundStatus, refundReason,
createdAt, paidAt, acceptedAt, makingAt, readyAt, completedAt, stageEnteredAt,
version, remark, itemCount,
items[{productId,skuId,productName,specName,imageUrl,temperature,sugar,unitPriceCent,quantity,lineTotalCent}],
amounts{totalAmountCent,payableAmountCent,paidAmountCent,refundedAmountCent},
allowedActions[], notificationStatus
```

新订单 orderNo 使用完整 UUID 的 32 位十六进制表示，创建时一并持久化为 coffee_payment.merchant_order_no，所有发起支付、查询、关单及回调按保存的映射复用；不在支付时截断或重新生成。已有订单号和渠道映射不迁移；历史不合规单号在新发起支付时本地拒绝，已有支付/退款通知仍按原映射校验处理。

退款查询/申请应答按 amount.currency=CNY 校验；退款通知使用官方 refund_status 与 amount（total/refund/payer_total/payer_refund）结构，不要求通知提供 currency，提供时必须为 CNY。独立商户验签/解密及商户、交易号、订单号、退款号、原金额和退款金额校验继续保留。

时间与尚未发生的金额可为 null；itemCount 是杯数总和。未发生退款的 refundedAmountCent=0。内容和金额取订单快照；不回查当前价格重算历史单。取餐号由服务端生成，门店+取餐日期+号码唯一，拒单后不重复利用号码。

`allowedActions` 从 `ACCEPT,REJECT,START,READY,COMPLETE,REFUND` 中返回当前操作者可用项；提交时仍重新校验。`notificationStatus` 为 `NONE,PENDING,SENT,SKIPPED,FAILED,UNKNOWN`，供详情显示提醒结果，不扩展通知管理平台。

## 6. 工作台、订单和流水接口（新增）

| 方法与路径 | 输入 | 返回 |
| --- | --- | --- |
| GET `/admin/coffee/workbench` | storeId | `{storeId,serverTime,columns[]}` |
| GET `/admin/coffee/orders/list` | storeId,page,size,fulfillmentStatus?,refundStatus?,keyword?,dateFrom?,dateTo? | `Page<CoffeeOrder>` |
| GET `/admin/coffee/orders/{id}` | 无 | CoffeeOrder 加 `events[]` |
| POST `/admin/coffee/orders/{id}/accept` | expectedVersion | 最新 CoffeeOrder |
| POST `/admin/coffee/orders/{id}/reject` | expectedVersion,requestKey,reason | 最新 CoffeeOrder，登记拒单及全额退款流程 |
| POST `/admin/coffee/orders/{id}/start` | expectedVersion | 最新 CoffeeOrder |
| POST `/admin/coffee/orders/{id}/ready` | expectedVersion | 最新 CoffeeOrder，持久化制作完成通知任务 |
| POST `/admin/coffee/orders/{id}/complete` | expectedVersion | 最新 CoffeeOrder |
| POST `/admin/coffee/orders/{id}/refund` | expectedVersion,requestKey,reason | 最新 CoffeeOrder |
| GET `/admin/coffee/ledger` | storeId,dateFrom,dateTo | Ledger |

工作台固定四列 WAIT_ACCEPT、WAIT_MAKE、MAKING、READY。每列 `{status,records,total,current,size,pages}`，首屏最多 20 条，不能隐瞒剩余数量；继续加载通过订单列表按相同状态分页。只包含仍需履约的订单，退款冻结/拒单订单不进入制作列。按该阶段进入时间 ASC、id ASC 排列，跨日未完成订单照常返回，不按今天截断。

订单列表默认查询今天；日期范围只过滤订单创建时间，退款状态可筛选。工作台继续加载时，传四个进行中 fulfillmentStatus 之一且不传日期，查询跨日、refundStatus=NONE 的同列订单，并保持 stageEnteredAt ASC,id ASC；显式日期仍只过滤创建时间。详情 events 返回操作名称、操作者显示名称、时间和必要原因，不暴露 token、密钥等信息。keyword 搜索订单号/取餐号，取餐号配合日期区分。

拒单只允许已支付 WAIT_ACCEPT。全额退款允许有权限管理员处理已支付且未全额退完的订单，不接受客户端金额；已处于退款流程时不再次申请。退款和接单/制作动作必须互斥；退款失败不自动恢复制作任务。

requestKey 为客户端对一次操作生成并复用的唯一字符串；服务端持久化去重，相同键不同参数返回409。重复已成功动作可以返回当前已实现目标状态；不得重复写履约事件或产生退款。退款成功以渠道确认数据为准，不能把申请受理当成功。

`Ledger={storeId,dateFrom,dateTo,timeZone,paidOrderCount,receivedAmountCent,refundedAmountCent,netReceivedAmountCent}`。日期为 YYYY-MM-DD，按北京时间含首尾整日；收款按支付成功时间、退款按退款成功时间。净收款可为负数，不代表结算到账或利润。

## 7. 咖啡交易与提醒的后端边界

为工作台提供真实交易来源，后端实现以下独立能力；本轮前端不新建顾客交易页面：

| 接口 | 要点 |
| --- | --- |
| POST `/user/coffee/orders` | 用户认证；body: storeId,requestKey,menuVersion,items[{skuId,quantity}],remark?；服务端定价与快照，返回 CoffeeOrder |
| GET `/user/coffee/orders/{id}` | 本人订单详情；与管理员事件信息适度裁剪 |
| POST `/user/coffee/orders/{id}/payment` | 返回现有格式微信支付参数；校验本人、未支付、有效期、咖啡商户 |
| POST `/user/coffee/orders/{id}/cancel` | 仅取消未支付本人订单；关闭渠道支付单，支付竞争按真实渠道结果处理 |
| POST `/api/coffee/payment/notify` | 咖啡专用支付回调，验签/解密/校验商户、AppID、金额、币种、订单 |
| POST `/api/coffee/refund/notify` | 咖啡专用退款回调，按实际渠道状态幂等处理 |

订单创建只支持已有 SKU、数量1–99和非空明细，不支持优惠券、分销、配送费或加料；金额不能由请求覆盖。menuVersion 与当前菜单不符时拒绝创建并返回409，不能静默按新价格成交。同一用户 requestKey 相同且参数一致返回同一订单。创建不自动清空整个购物车，实际购物车清理留给后续明确的顾客下单接入。

保留原商品支付配置，新增独立咖啡配置及客户端。只复用通用签名工具；不得把咖啡订单塞进商品库存、支付回调或佣金流程。未付款到期沿用项目现有支付有效期配置，另处理咖啡订单，不改变原商品超时任务行为。已付款无人接单不自动取消。

采用 `COFFEE_ORDERING_ENABLED=false` 默认值，正式开放还需顾客入口、真实商户和退款联调验收；关闭只停止新建/新发起支付，不禁用已有订单查询、回调、退款和店员处理。已有效发起的支付最终仍按渠道真实结果处理，不静默丢失收款。

制作完成时事务内登记唯一通知任务，事务外使用现有 WxMaService 发送实际模板消息。模板或真实授权条件不足时记录 SKIPPED/原因，不伪造已授权或已送达。网络结果不确定记 UNKNOWN，不能无限盲重发；SENT 只表示微信接口已受理，不代表顾客看到横幅。本轮不新增订阅授权 UI，正式消息闭环验收留待接入条件具备。

统一订单接口的商品字段与排序不改；当前小程序仍按阶段 A 展示，不自行增加咖啡详情页或新标准状态。真实咖啡数据接入统一列表涉及 WAIT_MAKE 的标准状态和详情路由，须与后续小程序交易方案一起确认；本轮后台数据已存在不等于可提前发布顾客端交易。

## 8. 首页运营

复用 GET `/user/marketing/activity/list?position=...` 和原管理接口。新增位置字符串 `home_recommend`；现有 VARCHAR 字段可保存，不新增广告表或 API。`home_banner` 只展示有效列表第一条；推荐按后端已有 sort DESC,id DESC 取前六条。零条隐藏区域、单数末项正常宽度靠左。

展示 imageUrl、title、subtitle；不手填展示价格。不新增商品选择器、自动去重、下架联动、草稿、审批、统计。保留 home_notice、login_popup 数据与行为。图片路径沿用当前项目处理；product 跳商品详情、page 可跳已有咖啡菜单，不新增饮品定位。

后台首页运营首版仅超管拥有 `marketing:manage`。公开读取不要求用户登录；失败不影响双业务入口。运营记录可增加超过六条，限制的是前台展示数量，不是后台保存数量。

## 9. 未确认事项

待接单多久升级提醒、无人接单是否自动退款，尚未确认；本轮只显示实际等待时间和待处理数量，不擅自实现自动处理。商品管理员的共享首页编辑权也不自行开启。

本契约由后端实施对话维护，前端按契约消费。新增命名是这次交接约定，不能当作现有代码事实；实现变更先更新并说明差异，再联调。

## 10. 后端业务码登记

| code | HTTP | 含义 |
| --- | --- | --- |
| 91409 | 409 | 账号名或授权配置冲突 |
| 91410 | 409 | 最后一个启用超管保护 |
| 92404 | 404 | 咖啡订单不存在或不可见 |
| 92409 | 409 | 咖啡订单状态/版本冲突 |
| 92410 | 409 | 幂等请求键与参数不匹配 |
| 92411 | 409 | 菜单版本已变更 |
| 92412 | 400 | 渠道签名、商户、币种、订单或金额不合法 |
| 92503 | 503 | 独立咖啡渠道不可用或结果尚不能确认 |

## 2026-10-28 顾客交易入口与共用商户补充

按用户新确认，小程序增加咖啡结算、支付调用和取餐详情，使用既有 `/user/coffee/orders` 系列接口，不修改已有数据结构。首版允许 `COFFEE_PAY_*` 显式配置为与商品相同的商户凭据，仍使用独立咖啡回调及交易快照。具体请求重试、篮内勾选、顾客展示和开放边界见[单商户收款交接](coffee-shared-merchant.md)。此补充取代原先顾客入口未实现和必须不同商户的阶段约定，不改变旧商户未结单不可直接切换渠道的约束。
