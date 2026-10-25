# 双业务共用接口与边界 · v1

日期：2026-10-24。供前端、后端两个实施对话共同遵守。**本文定义的是待新增契约，不代表接口已经实现。** 阶段 A 只实现浏览、独立加购、后台菜单维护和统一订单查询。咖啡真实下单／支付／制作／叫号是阶段 B。

## 1. 不变约定

| 项目 | 约定 |
| --- | --- |
| 业务类型 | `GOODS`＝现有商品；`COFFEE`＝咖啡点单业务；“全部”不传 `businessType` |
| 履约类型 | `DELIVERY`／`PICKUP` |
| 新接口 ID | 十进制字符串，如 `"123"`；前端不将其当数组序号，不用裸 ID 跨业务判断订单 |
| 旧接口 | `/user/product/**`、`/user/cart/**`、`/user/order/**` 保持原字段、数字状态及金额元制 |
| 新接口金额 | 后缀 `Cent`，非负整数分；可售 SKU 单价须大于 0。前端只格式化展示，不以浮点元累计 |
| 时间 | `createdAt` 等按 ISO 8601 含 `+08:00` 输出；门店业务时区 `Asia/Shanghai` |
| 成功响应 | 保持 `Result`：`code=0`、`message`、`data`，HTTP 200 |
| 失败响应 | 保持项目业务码与真实 HTTP 错误；不把失败改成 HTTP 200 的空列表 |
| 分页 | `records / total / size / current / pages`；page≥1，size 为 1–100，默认 page=1、size=20 |
| 认证 | 用户接口 Bearer 用户 token；管理接口 Bearer 管理员 token；身份不得由请求体 `userId/adminId` 指定 |
| 能力判断 | 门店能力以服务端返回为准；客户端文案／按钮不能反向授权下单 |

商品和咖啡购物车不共表、不共 DTO、不共接口；商品接口中传咖啡 `skuId` 不是跨域调用方案。新统一列表的 key 为 `businessType + ':' + id`，详情按业务路由。

JSON 类型统一：`capabilities.*`、`available` 是布尔值；`quantity / checked / sort / status（管理端启用状态）` 是整数，不能混用字符串和布尔值。购物车 `version / expectedVersion` 是非负安全整数；菜单 `version` 则是不透明字符串，两者不能混用。数量、版本、金额须在 JavaScript 安全整数范围内，后端校验而非依赖前端限制。可空字段显式返回 null，集合返回空数组，不返回 null。

## 2. 阶段 A 的用户接口

下列新增路径为双方固定约定；本轮不再各自另取 `/cafe`、`/drink` 等别名。

| 方法与路径 | 登录 | 入参 | `Result.data` |
| --- | --- | --- | --- |
| `GET /user/coffee/store` | 否 | 无，首版查询当前咖啡店 | `CoffeeStore` |
| `GET /user/coffee/menu` | 否 | query：`storeId` | `CoffeeMenu` |
| `GET /user/coffee/cart/list` | 是 | query：`storeId` | `CoffeeCartItem[]` |
| `POST /user/coffee/cart` | 是 | `storeId, skuId, quantity, checked?` | 最新 `CoffeeCartItem`，同 SKU 累加数量 |
| `PUT /user/coffee/cart/{id}` | 是 | `quantity? / checked?` 至少一个，以及 `expectedVersion` | 最新 `CoffeeCartItem` |
| `PUT /user/coffee/cart/selection` | 是 | `storeId, checked` | 当前店完整最新 `CoffeeCartItem[]` |
| `DELETE /user/coffee/cart/{id}` | 是 | 当前用户的购物车行 ID | `true`；本人记录已不存在时也可成功，不泄露他人记录 |
| `GET /user/business-order/list` | 是 | 见订单筛选约定 | `PageResult<BusinessOrderSummary>` |

`/selection` 使用专用固定路径，不能被 `/{id}` 当作数字 ID 解析。公开目录放行只限两个浏览路径，购物车仍需认证。

阶段 A 不实现 `/user/coffee/order/create` 或 `/pay`。前端不能请求它们；不能为了视觉闭环在生产返回模拟成功。未来创建接口即使存在，也必须独立检查服务器能力，不能只依赖按钮禁用。

### 2.1 CoffeeStore

| 字段 | 类型／含义 |
| --- | --- |
| `id` | 字符串 ID |
| `name / address / openingHours` | 真实名称、地址、营业时间展示文案 |
| `timeZone` | 固定 `Asia/Shanghai` |
| `businessStatus` | `OPEN / CLOSED / PAUSED`；独立于线上点单是否开通 |
| `announcement` | 字符串或 null |
| `capabilities.browse` | 是否可浏览 |
| `capabilities.addToCart` | 是否可加购；操作本身仍需用户登录 |
| `capabilities.placeOrder` | 阶段 A 必须为 false |
| `capabilities.payOnline` | 阶段 A 必须为 false |
| `orderUnavailableReason` | `PAYMENT_NOT_READY / ORDERING_DISABLED / STORE_CLOSED / STORE_PAUSED` 或 null |

配置未启用或没有真实门店时，公开接口返回明确未配置／未开放错误，不返回示例门店。已启用目录时，非营业时段仍可浏览和选购收藏，能否加购由 `addToCart` 决定，不在前端擅自推断。

**首发显示**：门店区域说明“点单筹备中，可先浏览与加购”；购物栏保留金额，按钮“暂未开放”。“营业中”不能被当成已支持线上下单。

### 2.2 CoffeeMenu 与规格

`CoffeeMenu` 包含 `storeId`、`version`（不透明菜单版本字符串）、`categories`、`products`。首版为单店小菜单聚合，不要求客户端再为每杯饮品请求一次 SKU。禁止静默截断菜单；后续需要分页时先升级契约。

| 对象 | 字段 |
| --- | --- |
| 分类 | `id, name, sort` |
| 饮品 | `id, categoryId, name, imageUrl（可空）, description, sort, skus[]` |
| SKU | `id, productId, name, temperature, sugar, priceCent, available, unavailableReason（可空）` |

温度固定枚举 `HOT / ICED`，甜度 `REGULAR / LESS / NONE`；显示文案由前端映射。**SKU 列表才是允许组合的事实来源**：某饮品只有热饮 SKU 时不得生成冰饮选项。列表起价取可售 SKU 最低价；全部不可售时明确售罄，不显示可用加购按钮。首版没有加料、奶基底、多杯型、套餐或平台券叠加逻辑。

公开菜单只含启用分类、上架饮品和启用规格；其中 `available=false` 的启用规格仍可展示为售罄。管理保存后的菜单 `version` 必须能反映相关变更，不依赖客户端据此跳过可售校验。分类、饮品均按 `sort ASC, id ASC` 排序，规格按 ID 升序。没有可售规格时不展示虚构起价。

`imageUrl` 为可访问的 HTTPS 地址或项目认可的 `/uploads/` 路径，由前端统一补齐域名；不能将本地绝对磁盘路径当作图片 URL。

### 2.3 CoffeeCartItem 与操作

| 字段 | 含义 |
| --- | --- |
| `id, storeId, productId, skuId` | 全部为字符串 ID |
| `productName, skuName, imageUrl` | 当前展示数据，图片可空 |
| `temperature, sugar` | 与菜单相同的枚举 |
| `unitPriceCent` | 当前服务端售价，不接受客户端提交售价 |
| `quantity` | 整数 1–99；POST 是增加，PUT 是设置绝对数量 |
| `checked` | 0 或 1，新增省略时默认 1 |
| `available / unavailableReason` | 当前可售性及原因，不可售项保留在列表中 |
| `version` | 乐观锁版本；成功更新后递增；PUT 带 `expectedVersion` |

勾选金额／已选数量只统计 `checked=1 && available=true`；不可售行禁选并允许移出。页签徽标表示该业务篮中全部数量，不与“已选”混用。

全选只作用于当前门店的可售行，返回列表包括不可售行但不能把它们当作可结算项。相同 SKU 在同一用户、门店下合并；不同温度／甜度必须对应不同 SKU 行。删除不修改其他账号或其他业务。

POST 合并已有行时累加数量并按 `checked` 更新选择，省略 `checked` 视为 1；超过 99 则整体拒绝，不能静默截断。单行任何有效更新、POST 合并、批量选择变更都同步递增该行版本，保证旧版本 PUT 无法覆盖新状态。不可售行仍允许移出和取消勾选；增加数量或勾选不可售行应拒绝，减少数量可保留。全选不重置不可售行的既有选择，但其金额始终不计入合计。

`available` 表示商品／规格和目录是否允许当前加购，不因本轮 `placeOrder=false` 就全部返回 false。目录停用时仍可读取本人旧购物车以移出条目，所有条目均不可售。`unavailableReason` 固定为 `STORE_DISABLED / CATEGORY_DISABLED / PRODUCT_OFF_SHELF / SKU_DISABLED / SOLD_OUT` 或 null，按该顺序取首个原因；前端映射展示文案。SKU 或饮品已停用时，列表仍通过保留的数据关联返回名称和原因。

加购发生超时后，客户端先重新查询确认结果，不自动重复 POST；数量／勾选版本冲突时重新读取，不默默覆盖服务端新状态。

## 3. 统一订单查询

query：`page`、`size`、可选 `businessType`、`statusGroup`、`status`。

- `statusGroup`：`ALL / IN_PROGRESS / COMPLETED / CLOSED / AFTER_SALES`，默认 ALL。
- `status`：指定单个标准状态；指定时 `statusGroup` 必须省略或为 ALL。参数不兼容返回参数错误。
- 全部业务推荐使用状态组；具体业务可用单状态筛选。不支持该业务的状态不是“找不到接口”，应返回参数错误。
- 阶段 A 的 `COFFEE` 返回正常空页；全部业务只查询真实商品数据。不要插演示咖啡订单。
- 排序固定 `createdAt DESC, businessType ASC, id DESC`。未来接咖啡数据后仍由服务端全局排序、计数与分页，不能将两页各 20 条直接拼成一页。

### 3.1 状态映射

| 业务 | 原数据／状态 | 标准 `status` | 展示／分组 |
| --- | --- | --- | --- |
| GOODS | 0 | `PENDING_PAY` | 待付款／IN_PROGRESS |
| GOODS | 10 | `PENDING_SHIP` | 待发货／IN_PROGRESS |
| GOODS | 20 | `PENDING_RECEIVE` | 待收货／IN_PROGRESS |
| GOODS | 30 | `COMPLETED` | 已完成／COMPLETED |
| GOODS | 40 | `CANCELLED` | 已取消／CLOSED |
| GOODS | 50／60 | `REFUNDING / REFUNDED` | 退款中／已退款，均为 AFTER_SALES |
| COFFEE（阶段 B） | 独立订单状态 | `PENDING_PAY / PAID / MAKING / READY` | 待付款／待接单／制作中／待取餐，均为 IN_PROGRESS |
| COFFEE（阶段 B） | 完成／取消／退款 | `COMPLETED / CANCELLED / REFUNDING / REFUNDED` | 按相应标准分组 |

标准状态不更改旧商品数字码。已取消订单必须能在全部／取消筛选中找到，不能因换视觉而消失。咖啡未来需要待接单状态，不能把所有已付款都直接叫“制作中”。

### 3.2 BusinessOrderSummary

| 字段 | 含义 |
| --- | --- |
| `businessType, id, orderNo` | 业务、字符串 ID、真实订单号 |
| `storeId` | 咖啡店 ID；旧商品可为 null |
| `storeName` | 真实业务展示名，旧商品可用当前商品商城配置名 |
| `fulfillmentType` | 商品 DELIVERY；咖啡 PICKUP |
| `status, createdAt` | 标准状态、带时区创建时间 |
| `itemCount` | 全部订单明细数量之和，不是只取前三条的数量 |
| `summaryItems` | 最多三条快照摘要；完整明细仍从业务详情读取 |
| `amounts` | 见下表 |
| `pickupCode, pickupDate` | 阶段 A 均为 null；未来只由服务端提供真实取餐信息 |

每条 `summaryItems` 包含字符串 `productId / skuId`，以及 `productName, specName, imageUrl, unitPriceCent, quantity, lineTotalCent`，来源是订单快照，不能改用今天的商品名称／价格重算历史订单。

`amounts` 包含整数分 `totalAmountCent / discountAmountCent / freightAmountCent / payableAmountCent`，以及可空 `paidAmountCent`。未实际支付时 `paidAmountCent=null`；旧商品 `payAmount` 非零不等于已付款，需结合支付依据。原商品金额通过 BigDecimal 精确换算为分，不经二进制浮点近似。

商品适配明确使用 `OrderMaster.totalAmount / discountAmount / freightAmount / payAmount` 对应以上四个金额；旧记录可空的优惠／运费按 0 处理。以 `payTime` 或关联的成功支付记录为付款依据，成功支付金额优先取支付流水；退款后仍表示原支付金额，不擅自解释为净收款。`OrderItem.productImage / skuName / price / totalPrice` 对应摘要的 `imageUrl / specName / unitPriceCent / lineTotalCent`；历史缺图用前端占位，不能用当前商品价格重建历史摘要。

统一列表只提供展示摘要，不提供通用“发货／制作”按钮。商品详情及付款／收货继续调用已有商品业务接口；阶段 B 咖啡走独立接口，不能把咖啡 ID 传给商品 `/user/order/{id}/pay`。

## 4. 阶段 A 的管理接口

所有接口要求可信平台管理员，并由后端真实校验 `SUPER_ADMIN` 角色；不是有任意管理员 token 就允许操作。首版不发咖啡店员或显示屏账号。

| 方法与路径 | 入参 | `Result.data` |
| --- | --- | --- |
| `GET /admin/coffee/context` | 无 | `stores[{id,name}], permissions[]` |
| `POST /admin/coffee/store` | 门店资料 | 新建 `CoffeeStore`；首版仅允许首个店铺 |
| `GET /admin/coffee/store/{storeId}` | 字符串 ID | `CoffeeStore` |
| `PUT /admin/coffee/store/{storeId}` | 门店资料 | 更新后的 `CoffeeStore` |
| `GET /admin/coffee/menu` | query：`storeId` | `storeId, categories[], products[]`，包括停用／下架项 |
| `POST /admin/coffee/category` | query：`storeId`；body：分类资料 | 保存后的完整分类 |
| `PUT /admin/coffee/category/{id}` | 分类资料 | 保存后的完整分类 |
| `POST /admin/coffee/product` | query：`storeId`；body：饮品及全部规格 | 保存后的完整饮品／规格 |
| `PUT /admin/coffee/product/{id}` | 饮品及全部规格 | 保存后的完整饮品／规格 |

- 上下文权限码首版只有 `coffee:menu:read / coffee:menu:write`；无门店时 `stores=[]`，授权管理员可建立首个店。非授权管理员返回 403。
- 门店资料：`name, address, openingHours, businessStatus, announcement`。时区服务端固定；不接受支付密钥、`placeOrder` 或 `payOnline`。
- 分类资料：`name, sort, status`；返回补 `id`。`status` 取 0／1。
- 饮品资料：`categoryId, name, imageUrl, description, sort, status, skus[]`。
- 规格资料：可选 `id`（新增时省略）、`name, temperature, sugar, priceCent, status, available`。返回补全所有 ID；SKU 还需归属当前饮品。
- 保存饮品时，已有规格保留稳定 ID；未提交的旧规格停用而不是物理删除。新增规格不能与已有温度／甜度组合冲突；恢复旧组合时复用其 ID。
- 后台菜单返回分类、饮品和规格的状态字段；不得复用公开菜单的过滤结果导致后台看不到下架数据。
- 本轮不提供物理删除 API，停用通过 PUT；分类仍有在售饮品时拒绝直接停用并给出原因。
- 复用现有 `/admin/upload/image`，表单字段 `file`，成功数据 `{url}`。不重复建设咖啡图片上传服务。

## 5. 咖啡业务错误码约定

下表为待加入 `BizCode` 的新码段，不能擅自改为成功空数据。通用参数错误和登录错误继续用项目现有码。

| 名称 | code／HTTP | 处理 |
| --- | --- | --- |
| `COFFEE_STORE_NOT_FOUND` | 90401／404 | 门店未配置、停用或不存在；前端显示筹备／不可用状态 |
| `COFFEE_SKU_NOT_FOUND` | 90402／404 | 规格不存在；刷新菜单 |
| `COFFEE_SKU_UNAVAILABLE` | 90403／409 | 规格停售／不可售；提示并刷新 |
| `COFFEE_CART_NOT_FOUND` | 90404／404 | 当前用户记录不存在；重新加载 |
| `COFFEE_CART_VERSION_CONFLICT` | 90409／409 | 版本冲突；读取服务端新状态，不强行覆盖 |
| `COFFEE_ORDERING_DISABLED` | 90410／409 | 预留后续创建接口：未开放点单，不能提交 |
| `COFFEE_STORE_CLOSED` | 90411／409 | 预留后续下单校验：当前不接单 |
| `COFFEE_STORE_ALREADY_EXISTS` | 90415／409 | 首版已建立咖啡店，不建立第二店 |

参数越界使用 40001／400；未登录 40100／401；无管理权限 40300／403。前端基础请求工具若原来丢失 `code/status`，应兼容补齐，保留原错误 message 和登录守卫。不要依赖精确匹配中文 message 判断分支。

## 6. 两端依赖与变更规则

1. 后端先交门店／菜单，前端可同时搭页面；随后联调咖啡购物车，再接统一订单与后台维护。
2. 前端开发模拟响应必须与本文字段一致，生产关闭模拟；后端不能为方便联调给生产注入示例订单。
3. 本轮不因商户号未就绪而阻塞浏览加购，也不因页面已完成而越过支付开关上线真实点单。
4. 前端负责 `c-uniapp/`、`admin-web/`；后端负责 `src/`、`sql/`、相关配置。共同契约由后端对话维护；改动前通知前端同步，不能静默变更。
5. 阶段 B 的支付、创建幂等、制作状态、退款、取餐号、工作台和叫号屏只保留方向，**不以本文件表格的预留名当作完整交易契约**。正式开发前另补完整方案，不在阶段 A 偷跑。
