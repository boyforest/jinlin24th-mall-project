# 单仓上架流程收敛（B + 单仓隐藏）设计文档

日期：2026-09-21
状态：**已评审通过并实现完成**（实现中的偏离见文末「实现说明」）
仓库：jinlin24th-mall-project（金霖二十四养私域商城）

## 背景与现状

### 事实一：仓库这一层不影响交易

下单选库存的代码完全按 `skuId` 找第一条有货的记录，**不关心仓库**：

```java
// OrderServiceImpl.occupyInventory
.eq(Inventory::getSkuId, skuId).ge(Inventory::getStock, quantity).orderByAsc(Inventory::getId)
```

仓库只决定"这批货从哪个仓出"。取消订单归还库存时按订单明细快照的 `warehouseId` 原仓归还，但单仓场景下这同样是同一个仓。

### 事实二：初始化脚本只建了一个仓库

`sql/jinlin24th_init.sql`：`INSERT INTO warehouse (id=1, name='主仓库', status=1)`。
运营实际是**单仓**经营。

### 事实三：上架一个商品要跨 3 个页面，且有两处同名 `stock`

| 步骤 | 页面 | 写入 |
|---|---|---|
| 1. 建商品 | 商品页 | `product` |
| 2. 建规格（售价/会员价） | 规格页 | `product_sku` |
| 3. **再设库存** | 库存页 | `inventory`（仓库 × SKU 二维） |

由此产生三个具体问题：

1. **概念重复**：`product_sku.stock`（已于上一轮废弃并加注释）与 `inventory.stock` 同名不同义，运营容易改错地方；
2. **维度多余**：库存页必须**先选仓库**（下拉框里只有一个"主仓库"），单仓下这是纯操作负担；
3. **上架后无库存记录**：建完 SKU 若未去库存页建 `inventory` 行，商品**看起来已上架、实际库存为 0**，用户看到"无货"，而运营以为已经上架。

### 已被上一轮解决的部分

- `product_sku.stock` 已废弃（列注释 + 代码不再读取）
- 管理端「规格」页已**只读展示**「可售库存」（= inventory 各仓汇总），零库存标红，并提示去库存页调整

## 目标与非目标

### 目标

1. **消除"上架后无库存记录"**：建 SKU 时自动为其创建库存行（默认仓，`stock=0`）。
2. **单仓场景下隐藏"仓库"这一层**：库存页不再要求选仓库，不再展示仓库列。
3. 不改动数据模型、不改动下单/扣减逻辑、不新增库存写入语义。

### 非目标（明确不做，避免范围蔓延）

- **不做**「在规格页直接编辑库存」：那会新增一个与下单并发写库存的入口。现有库存页编辑本身是 last-write-wins，本轮**不新增**写入语义，风险与今天持平。
- **不做**多仓的默认仓选择与切换（`warehouse.is_default` 列）：留到真正需要多仓时再做，届时用显式列而非隐式约定。
- **不做**库存盘点的版本号/CAS 加固（防覆盖并发扣减）：独立议题，单独排期。
- **不做** `product_sku.stock` 列的物理删除（需要破坏性迁移）。

## 设计

### 第 1 节：单仓模式判定（后端）

**判定规则（派生，不引入配置项、不迁移）**：
「单仓模式」= 启用状态的仓库**恰好一个**（`warehouse.status = 1`）。

- 0 个启用仓库 → 不是单仓模式（未配置仓库，属部署缺失）
- 1 个启用仓库 → 单仓模式，该仓即**默认仓**
- ≥2 个启用仓库 → 不是单仓模式，维持现状（显式选择）

为什么派生而非配置：将来加第二个仓库时 UI 自动恢复"选择仓库"，不会留下一个忘记关掉的开关导致库存被写进错误的仓。

新增：

- `InventoryService.resolveSingleWarehouse()` → 单仓模式返回该 `Warehouse`，否则 `null`
- `AdminInventoryController` 新增 `GET /admin/inventory/mode`
  ```json
  { "singleWarehouse": true, "defaultWarehouseId": 1, "defaultWarehouseName": "主仓库" }
  ```
  多仓时 `singleWarehouse=false`，`defaultWarehouseId/Name` 为 null。

### 第 2 节：建 SKU 自动创建库存行（后端，B）

新增 `InventoryService.ensureInventoryForSku(Long skuId)`：

1. 若该 `skuId` 已存在任意 `inventory` 行 → 直接返回（幂等，不做任何修改）
2. 若**不是**单仓模式 → 记 warn 日志后返回（多仓下无法替运营决定放哪个仓）
3. 否则为该 `skuId` + 默认仓插入一行：`stock = 0`、`warning_stock` 取默认值
4. **任何异常都不得向上抛出**：库存行创建失败不能阻断商品上架（记 error 日志，交人工补）
5. 并发重复插入由 `uk_warehouse_sku(warehouse_id, sku_id)` 唯一键兜住，捕获重复键后忽略

调用点（两条建 SKU 路径都要覆盖，否则又留下一个坑）：

- `ProductSkuServiceImpl.create()`：保存 SKU 成功后
- `ProductServiceImpl.create()`：它内部为商品自动建"默认"规格（`productSkuService.save(sku)`），同样要调

### 第 3 节：管理端单仓隐藏（前端）

- `admin-web/src/api/inventory.js` 新增 `getInventoryMode()`
- `admin-web/src/pages/InventoryPage.jsx`：进入页面时取一次「库存模式」
  - **单仓模式**：隐藏「仓库」筛选、隐藏「仓库」列、隐藏表单里的仓库下拉（创建时自动填 `defaultWarehouseId`）、隐藏详情里的仓库行
  - **多仓模式**：保持现状不变
  - 模式接口失败时**按多仓处理**（保守回退到显式选择，绝不静默替用户决定仓位）
- `admin-web/src/pages/SkuPage.jsx`：库存列提示文案在单仓下调整为「可直接在「库存」页修改数量」

### 第 4 节：验收与边界

**后端测试（TDD，先红后绿）**

| 用例 | 断言 |
|---|---|
| 单仓 + SKU 无库存行 → 自动建行 | 插入 1 行，`stock=0`，`warehouse_id` = 默认仓 |
| 单仓 + SKU 已有库存行 → 幂等 | 不再插入、不修改现有库存 |
| 多仓（2 个启用仓）→ 不自动建 | 不插入（避免替运营决定仓位） |
| 0 个启用仓库 → 不自动建且不抛异常 | 不阻断上架 |
| 插入抛异常 → 不向上抛 | `create` 仍返回成功 |
| `resolveSingleWarehouse` | 1 启用→该仓；2 启用→null；1 启用+1 禁用→启用那个；0→null |
| 两条建 SKU 路径都会触发 ensure | `ProductSkuServiceImpl.create` 与 `ProductServiceImpl.create` 各验证一次 |

**前端验证（无测试框架，靠构建 + 人工）**

- `npm run build` exit 0
- 单仓模式下库存页无仓库筛选项/列/表单字段，能正常新增与修改库存
- 手工把仓库改为 2 个 → 界面自动恢复仓库选择（验证派生判定）

**边界**

- 单仓模式下 SKU 列表的库存列与库存页数量应一致（都来自 `inventory` 汇总）
- 自动建行后，`stock=0` 会让 C 端立刻显示"无货" —— 这是**正确**的：未配库存本就不该卖。运营配置数量后即可售。

## 影响面与风险

| 项 | 影响 |
|---|---|
| 下单/库存扣减 | **零改动**（`occupyInventory` 不涉及仓库） |
| 数据库 | **零迁移**（不加列、不建表） |
| 已有 inventory 数据 | 不受影响（ensure 只补缺失行） |
| 上架流程 | 建 SKU 后自动多一行 `stock=0`；运营只需在库存页填数量 |
| 回退 | 删掉 ensure 调用即可；前端隐藏是纯展示层 |

**主要风险**：`ensureInventoryForSku` 若因仓库未配置或数据库异常而抛错，会**阻断上架**。
→ 因此第 2 节第 4 条明确要求"任何异常都不得向上抛出"，并有专门测试覆盖。

## 任务清单（实现顺序）

1. 后端：`resolveSingleWarehouse()` + 测试
2. 后端：`ensureInventoryForSku()` + 测试（含异常不外抛）
3. 后端：两条建 SKU 路径接入 ensure + 测试
4. 后端：`GET /admin/inventory/mode` 接口
5. 前端：`getInventoryMode()` + 库存页单仓隐藏
6. 前端：规格页提示文案调整
7. 验证：后端全量测试 + admin-web 构建 + 小程序构建（未改小程序，但确认无回归）
8. 文档：`docs/deploy-fixes-runbook.md` 补充单仓模式的验证步骤

## 待确认事项

- 单仓模式判定用「启用仓库恰好 1 个」的**派生规则**（而非新增配置项）是否认可？
- 默认仓库存行的 `warning_stock` 取什么值？（建议沿用 `inventory` 表默认值 10，即不特殊处理）

---

## 实现说明（相对本规格的偏离与新增）

### 偏离 1：`ensureInventoryForSku` 放在新组件而非 `InventoryService`

规格原写"新增 `InventoryService.ensureInventoryForSku`"。实现时改为新增
`InventoryMaintenanceService`（只依赖 `WarehouseMapper` + `InventoryMapper`）。

**原因（技术强制）**：`InventoryServiceImpl` 的**构造器**依赖 `ProductSkuService`，
而本方法的调用方正是 `ProductSkuServiceImpl` / `ProductServiceImpl`。
若把方法放在 `InventoryService` 上就形成循环引用——Spring Boot 2.6+ 默认禁止循环依赖，
构造器注入会在**启动时直接失败**。本会话此前处理库存汇总时已踩过同一个坑。

### 新增 1：存量 SKU 的库存回填迁移（规格未预见，属必要补充）

实现过程中发现一个**比"概念混淆"严重得多**的问题：

> 改动前，**全项目没有任何创建 `inventory` 行的入口**——后端无 POST 接口、
> 管理端「库存」页未传 `createApi`（无「新增」按钮）、连 `createInventory` API 函数都不存在。
> 演示环境之所以正常，只是因为当时启用的 `sql/demo_seed.sql` 恰好插入了配套库存行。

也就是说：**正式运营路径下，新建商品永远无法设置库存，除非手工写 SQL**（上线阻断级）。

自动建库存行只覆盖"今后的新 SKU"，**存量 SKU 仍然无解**。因此补充：
`sql/migrations/20260921_backfill_inventory_for_existing_skus.sql`（幂等，仅在单仓部署下生效）。

已用真实 MySQL 验证：① 已有库存行不被改动（`stock=88` 保持）；② 重复执行不重复插入；
③ 多仓部署下正确地什么都不做。

### 新增 2：单仓隐藏的"安全性"用真实 SQL 验证（规格未列）

单仓模式隐藏了编辑表单里的仓库字段，于是 PUT 请求体不再带 `warehouseId`。
若 `updateById` 会把 null 字段写回，则每次改库存都会**清空 `warehouse_id`**，
该行变成孤儿数据（所有仓库都查不到），表现为"改了库存却显示不出来"。

**不靠推断**：新增 H2 真实 SQL 测试 `InventoryNullFieldUpdateIntegrationTest`，
证明 `updateById` 默认策略（`FieldStrategy.NOT_NULL`）跳过 null 字段，不传 `warehouseId` 是安全的。

### 新增 3：前端可见性判定抽为纯逻辑并单测

将「单仓时隐藏什么」抽到 `admin-web/src/utils/inventoryView.js` 并用 `node --test` 覆盖，
其中最关键的一条是**失败即保守**：`singleWarehouse` 字段缺失、类型异常
（如字符串 `"true"`、数字 `1`）时必须回退多仓、继续显示仓库选择，
绝不因为响应异常而替运营猜仓位。

### 验证结果

| 项 | 结果 |
|---|---|
| 后端全量测试 | 192 通过 / 0 失败（新增 11 + 3 + 2 + 2 = 18 个用例） |
| 后端打包 | exit 0 |
| admin-web 测试 / 构建 | 27 通过（+4）/ exit 0 |
| 小程序构建 / 类型检查 | exit 0 / exit 0 |
| 回填迁移 | 真实 MySQL 验证：不误伤、幂等、多仓不生效 |
