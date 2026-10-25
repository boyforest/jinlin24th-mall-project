# 后端实施方案 · 阶段 A

日期：2026-10-24。范围：现有 Spring Boot 单体中的 Java、SQL、配置示例及测试。本文只定义改法和文件，不提供实现代码。先读 [共用接口与边界](api-contract.md)，与 [前端方案](frontend-plan.md) 并行。

## 1. 首版目标

支持一个现有商品商城和一家独立咖啡店，共用当前登录系统、应用服务与管理后台。首版实现咖啡门店／菜单公开浏览、登录后独立加购、后台菜单维护及统一订单查询。

**咖啡商户尚未开通，用户已决定收款就绪前不开放线上下单。** 阶段 A 不创建咖啡待支付订单、不支持到店付款、不调用现有商品支付、不推送制作／取餐数据；`placeOrder`、`payOnline` 必须由服务端返回 `false`。

保留原有商品订单、库存、会员价、推荐官快照、分销佣金、微信支付和退款流程。不要把“推荐官线下结算”解释为所有交易线下收款。

## 2. 为什么采用独立咖啡模块

已检查的当前实现：

- `OrderServiceImpl.create()` 查询商品 SKU，按会员资格定价，占用 `inventory`，写入仓库与推荐官快照，发布商品订单事件。
- 商品取消流程按明细中的仓库快照还库存；超时逻辑和 MQ 消息也是商品订单语义。
- `WxPayServiceImpl` 支付回调把商品订单 `0 → 10`，其中 `10` 是待发货；还会关联分销处理。
- `order_master.receiver_name/phone/address` 为必填配送数据，不能给咖啡强塞虚假地址。
- `WxPayConfig`／`WxPayClient` 当前围绕一套商户配置工作。

因此，本轮新增小型咖啡域，独立菜单与购物车；未来独立咖啡订单、支付流水及制作状态。通过新查询适配层实现“同一订单入口”，通过现有管理后台添加咖啡菜单实现“同一管理后台”。不把咖啡写进商品仓储／分销链路，也不重构整个交易底座。

阶段 A 只新增下列 **5 张咖啡业务表**，不提前建设多仓、原料库存、通用多租户、服务商分账平台。

## 3. 数据方案与约束

| 新表 | 主要字段／作用 | 关键约束 |
| --- | --- | --- |
| `coffee_store` | 名称、地址、营业时间展示、`time_zone`、营业状态、公告、启用状态、创建／更新时间 | 首版只维护一家当前咖啡门店；内部固定主门店标识加唯一约束并做服务校验，不硬编码 `id=1` |
| `coffee_category` | 门店、名称、排序、启用状态、时间 | 分类必须属于当前店；停用有关联在售饮品的分类时明确阻止或先处理饮品 |
| `coffee_product` | 门店、分类、名称、图片、描述、排序、上下架状态、时间 | 门店与分类归属一致；停售保留引用数据，不物理删除 |
| `coffee_sku` | 门店、饮品、规格名、温度、甜度、整数分售价、启用状态、可售标记、时间 | `(product_id, temperature, sugar)` 唯一；金额为正整数；未列出的规格组合不可购买 |
| `coffee_cart` | 用户、门店、SKU、数量、勾选、版本、时间 | `(user_id, store_id, sku_id)` 唯一；数量 1–99；修改限定用户并校验版本 |

复用 `app_user` 和 `sys_admin`，不复制账号体系。咖啡金额建议存 `price_cent BIGINT`，VO 直接返回整数分；旧商品金额仍保留 `BigDecimal` 元制，在统一查询边界准确换算，不改变旧 API。

新接口 ID 统一为十进制字符串，避免 Java Long 与 JavaScript 数值精度歧义。时间按 `Asia/Shanghai` 输出带时区的 ISO 8601。咖啡可售标记不是原料库存，不扣现有 `inventory`，也不宣称实现了库存预占。

## 4. 模块实现方法

### 4.1 门店与公开菜单

- 复用现有 `Result`、异常处理、用户登录基础设施和 MyBatis-Plus；新增咖啡 Catalog Service。
- `/user/coffee/store` 返回当前店及真实能力；没有已配置门店时返回明确未配置错误，不生成示例数据。
- `/user/coffee/menu` 只返回对应店铺已上架商品和可展示的规格；保留停售／不可售的表达，不把“售罄”简单等同“接口为空”。
- 阶段 A 的能力：门店资料和目录已启用时允许浏览；可售 SKU 可登录加购；线上下单和支付固定关闭。营业状态与线上点单能力分开。
- 营业时间文案只用于展示；真实营业状态以服务器配置为准，本轮不引入复杂排班／节假日规则。
- 在 `WebMvcConfig` 中**仅精确放行两个浏览 GET 路径**。不能排除整个 `/user/coffee/**`，否则购物车也会被放行。控制器同时保持查询方法为 GET。

### 4.2 独立购物车

- 所有查询和更新以当前登录用户为准，不接受客户端 `userId`。
- 根据 SKU 反查商品、门店与有效规格；不能信任客户端的名称、价格或门店归属。
- 新增采用唯一键配合事务／原子累加，避免并发产生重复行或丢数量；不是“先查再插”后忽略重复键。
- 数量／勾选修改使用绝对值和 `expectedVersion` 做条件更新；冲突返回明确业务码，前端重新读取。删除只作用于当前用户的记录。
- 全选接口在当前用户、当前门店范围内处理可售行，事务内更新后返回最新列表，不影响商品购物车。
- 列表批量查询饮品和 SKU，返回当前价格、可售性和原因。不可售记录仍可移出，不可计入可结算金额。
- POST 加购不是可任意重试的幂等操作；客户端请求锁与失败后重新查询用于处理结果不确定。不要在文档或接口描述中错误承诺“重复 POST 不增量”。
- 后台价格、上下架、规格变化后，下次读取返回新状态；不把旧购物车价格作为事实来源。

### 4.3 统一订单只读适配

新增 `/user/business-order/list`，保留 `/user/order/**` 的旧接口及商品操作语义。

当前 `OrderServiceImpl.userPage()` 会把 `items` 设为 `null`，不能直接复用其返回值当新版卡片数据。新查询需要：

1. 按当前用户与业务／状态条件统计总数并分页。
2. 批量查询该页商品订单的明细快照，形成最多三条摘要及总件数，避免列表 N+1 详情请求。
3. 将旧数字状态映射为共用契约的字符串状态；旧字段与状态码不变。
4. 精确换算金额；没有真实支付依据时，`paidAmountCent=null`。商品表中 `payAmount` 在未支付时也可能已经填入应付金额，不能据此认定已付。
5. 按 `createdAt DESC, businessType ASC, id DESC` 稳定排序，分页总数和筛选条件一致。

阶段 A 尚无真实咖啡订单：`businessType=COFFEE` 返回合法空页，“全部”只包含原有商品订单；不要为展示预览插入虚假订单。阶段 B 接入咖啡表后，在服务端做统一排序分页，不能让前端分别取两页再拼接。

仅输出用户端必要信息，不把买家手机号、推荐官手机号等管理字段整份复制到新的列表摘要。

### 4.4 同一后台内维护菜单

复用当前管理员登录与图片上传；新增咖啡管理上下文、门店基本资料、分类、饮品及有效 SKU 组合维护。

首版仅允许现有可信 `SUPER_ADMIN` 平台管理员管理咖啡模块。新增访问服务使用 `SysAdminService.getRoleCodes()` 检查启用角色，在后端真实执行；管理上下文只返回允许的门店及菜单读写能力，前端据此展示入口。

`AdminJwtInterceptor` 当前主要验证管理员登录，不能把“通过拦截器”当成“已经完成门店 RBAC”。本轮不发放仅咖啡店员／叫号屏账号，避免这些账号反向调用尚未做域隔离的旧后台接口。未来账号隔离见阶段 B。

新增门店时只允许建立首个门店；已有门店时返回明确冲突。门店资料修改不接受商户密钥、支付就绪标志和开启下单字段。饮品连同 SKU 在一个事务中保存：校验分类与门店、保留现有 SKU ID，未保留的旧规格应停用而非删除，不能让已有购物车悬空。

## 5. 预计修改的现有文件

以下路径前缀为 `src/main/java/com/jinlin24th/jinlin/`，另注明的除外。

| 文件 | 改法 |
| --- | --- |
| `common/config/WebMvcConfig.java` | 精确放行门店与菜单浏览；购物车和新订单查询继续用户鉴权，管理接口继续管理员鉴权 |
| `common/constant/BizCode.java` | 新增咖啡不存在、不可售、版本冲突等业务码；保留已有码及 HTTP 语义 |
| `service/impl/OrderServiceImpl.java` | 原则上不改交易逻辑；统一查询使用新 Service，避免在原流程插咖啡分支 |
| `mapper/OrderMasterMapper.java` | 仅在统一查询确有需要时增加只读查询，不改原支付／状态更新语句 |
| `mapper/OrderItemMapper.java` | 按订单 ID 集批量读取列表摘要；允许通过新查询 Mapper 代替，不必强行改旧方法 |
| `src/main/resources/application.yml` | 增加咖啡目录启用配置；本轮不引入真实支付凭证 |
| `src/main/resources/application-dev.example.yml` | 增加本地咖啡目录配置说明，避免写入真实门店／凭证 |
| `src/main/resources/application-prod.example.yml` | 写明启用顺序和咖啡下单尚未开放；不改生产环境实际密钥文件 |
| `sql/jinlin24th_init.sql` | 为新安装环境同步新增咖啡表结构；不清空旧表 |
| `deploy/mysql/init/01-schema.sql` | 与新安装结构保持一致；不把初始化脚本当作存量升级脚本 |

**本轮不应修改** `WxPayServiceImpl`、`WxPayConfig`、`WxPayClient`、商品订单超时消费者、分销计算、仓库库存与退款链路。若只是为了菜单／购物车就需要动这些文件，应先重新检查实施范围。

## 6. 预计新增文件

Java 路径前缀同上；接口、实体和 VO 的拆分可遵循现有命名，但职责不可遗漏。

| 文件／组 | 职责 |
| --- | --- |
| `controller/UserCoffeeController.java` | 门店与菜单公开 GET |
| `controller/UserCoffeeCartController.java` | 当前用户咖啡购物车的读写、全选 |
| `controller/UserBusinessOrderController.java` | 新统一订单列表，只读 |
| `controller/AdminCoffeeController.java` | 管理上下文与门店／分类／饮品维护 |
| `service/CoffeeCatalogService.java`、`service/impl/CoffeeCatalogServiceImpl.java` | 门店能力、目录过滤、菜单维护事务 |
| `service/CoffeeCartAppService.java`、`service/impl/CoffeeCartAppServiceImpl.java` | 用户隔离、加购、版本更新、批量勾选 |
| `service/BusinessOrderQueryService.java`、`service/impl/BusinessOrderQueryServiceImpl.java` | 商品订单适配、摘要、筛选与分页 |
| `common/auth/CoffeeAdminAccessService.java` | 咖啡管理权限校验；不依赖前端隐藏菜单 |
| `pojo/entity/CoffeeStore.java`、`CoffeeCategory.java`、`CoffeeProduct.java`、`CoffeeSku.java`、`CoffeeCart.java` | 五张新表实体，遵循现有自动填充时间规则 |
| `mapper/CoffeeStoreMapper.java`、`CoffeeCategoryMapper.java`、`CoffeeProductMapper.java`、`CoffeeSkuMapper.java`、`CoffeeCartMapper.java` | 新表持久化与必要条件更新 |
| `pojo/dto/CoffeeCartAddDTO.java`、`CoffeeCartUpdateDTO.java`、`CoffeeCartSelectionDTO.java` | 参数和版本校验 |
| `pojo/dto/CoffeeStoreSaveDTO.java`、`CoffeeCategorySaveDTO.java`、`CoffeeProductSaveDTO.java` | 后台资料及规格组合维护，不能绑定支付字段 |
| `pojo/vo/CoffeeStoreVO.java`、`CoffeeMenuVO.java`、`CoffeeCartVO.java`、`CoffeeAdminContextVO.java` | 契约对应返回值，门店／商品／规格可使用嵌套子 VO |
| `pojo/vo/CoffeeAdminMenuVO.java`、`CoffeeAdminProductVO.java` | 包括下架、停售状态的管理返回值，不能直接复用过滤后的公开菜单 |
| `pojo/vo/BusinessOrderSummaryVO.java` | 统一订单的最小摘要和金额语义 |
| `sql/migrations/20261024_coffee_catalog_cart.sql` | 存量数据库的增量结构变更 |
| `sql/coffee_demo_seed.sql` | 仅本地／测试演示数据，生产不能自动执行 |
| `docs/dual-business/backend-deployment.md` | 实施完成后补充实际迁移、配置、验证和回退步骤 |

建议新增针对性测试：`CoffeeCatalogAvailabilityTest`、`CoffeeCartIsolationTest`、`CoffeeCartConcurrencyTest`、`CoffeeAdminAccessTest`、`BusinessOrderQueryTest`；位于当前 `src/test/java/com/jinlin24th/jinlin/` 相应目录。新增 Mapper SQL 如使用 XML，放在项目现有 Mapper 资源约定目录，不在根目录散放。

## 7. 开发与上线顺序

1. 检查独立工作目录与基线，读取仓库指引，锁定共用接口；不碰前端目录。
2. 完成增量建表及实体／Mapper，使用本地测试数据验证；生产不预置示例饮品或默认收款配置。
3. 交付公开门店／菜单和始终关闭的下单能力，前端即可联调咖啡浏览。
4. 交付独立购物车与版本语义，前端联调加购、选择、修改和失败恢复。
5. 交付管理上下文与菜单维护，再交付统一订单摘要查询，补齐端到端数据闭环。
6. 执行新功能测试和原商品交易相关回归，核对迁移在测试环境的存量／新安装两条路径。
7. 实施完成后补部署说明，先升级兼容的后端，再发布依赖新接口的前端。未配置门店时给出真实筹备状态，不能回退演示数据。

现有数据库应执行增量 SQL，不重新运行全库初始化脚本。回退时优先关闭咖啡目录入口／回退应用，保留新表数据；不要用删表当第一步回退。迁移脚本需说明重复执行行为，不伪称 MySQL DDL 可被整体事务回滚。

## 8. 验收与测试重点

- 无 token 可访问门店／菜单，不能访问咖啡购物车与订单；现有商品浏览和登录行为不变。
- 用户 A 不能读写 B 的咖啡购物车；相同 SKU 不重复建行，数量增量无丢失，版本冲突明确返回。
- POST／PUT 拒绝负数、小数、超过上限的数量、非法规格、跨店 SKU、前端改价和停售加购。
- 后台上下架、调价后购物车能反映实际价格／可售性；旧商品购物车完全独立。
- 非 `SUPER_ADMIN` 不能靠直调新管理接口修改菜单；创建第二家咖啡店不被默认放开。
- `placeOrder=false`、`payOnline=false`；阶段 A 不产生咖啡订单、支付流水、库存流水或分销记录。
- 统一列表只返回当前用户，摘要来自下单快照、没有 N+1；状态筛选、分页总数、排序和空页正确。
- 商品支付前不能显示实付；新接口金额分制与旧商品元制转换准确。
- 旧商品预览／下单／支付回调／取消还库／退款／分销测试保持通过。

在独立后端工作目录运行 `mvn test`，构建按仓库现有方式执行。并发唯一键、条件更新、事务和统一分页应包含数据库层验证；纯 Mockito 不能证明真实并发和 SQL 行为。测试库不连接生产数据库。本文不声称这些新测试已经存在或通过。

## 9. 后续阶段 B：仅预留，当前不实现

待独立商户开通与小程序绑定核验完成后，另开任务：

- 新增 `coffee_order`、`coffee_order_item`、`coffee_payment_record` 和取餐号计数，不复用商品仓库和配送字段。
- 服务端预览、幂等创建、支付、查单、关单、退款、回调和对账全部按咖啡独立商户贯通；商户信息写入支付快照，不能只在前端换一个商户号。
- 原商品继续使用原商户；咖啡配置单独管理，不能覆盖当前 `wx.pay`。即使共用签名／HTTP 工具，配置与回调路由也要正确隔离。
- 支付状态由验签且金额／商户／AppID 校验成功的回调／查单推进，不能由前端支付成功通知直接修改。
- 咖啡状态：待付款 → 待接单 → 制作中 → 待取餐 → 已完成；取消、超时、退款另行处理，不能把商品“待发货”改名后直接复用。
- 取餐号按门店、营业日期唯一；并发、跨日、支付重复回调不能重复生成；客户端不能自编取餐号。
- 后台增接单工作台和最小叫号屏，初期可用定时轮询，无需先建 WebSocket／MQ 平台。
- 发放店员或叫号屏账号之前，服务端落实功能权限与门店范围；仅限制新咖啡接口不够，还需防止这些 token 调用旧的 `/admin/**` 商品／退款接口。可采用受限 token scope，精确限制允许的路径和方法，并做越权回归。
- 叫号屏仅展示取餐号和制作／待取餐状态，不展示手机号、地址或完整订单资料。

上述商户审批、退款策略和店员权限都不应由本轮页面开发自动决定。未来阶段先补齐详细交易契约，再开通 `placeOrder`，不能把一个布尔配置改为 true 就宣布收款完成。
