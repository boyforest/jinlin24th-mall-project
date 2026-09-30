# 金霖二十四养 · 上线前最终审查报告

审查日期：2026-09-21
审查范围：Spring Boot 3 后端（221 个 Java 文件 / 约 13,000 行）、admin-web（React 19 + Vite，约 2,600 行）、c-uniapp（uni-app 小程序，约 3,300 行）、部署编排（docker-compose / nginx / SQL）
审查方式：只读代码审查 + 依赖审计 + 关键路径源码逐行核对。**本报告未修改任何业务代码**，仅新增本文件。
结论口径：**不追求完美，只追求最小可上线**。

---

## 0. 总体结论

> **允许上线，但必须先完成 6 个 P0 动作 + 2 项运维兜底；P1 里只有 4 项属于"上线前必修"，其余可排期。**

| 维度 | 评价 |
| --- | --- |
| 核心闭环能否跑通 | ✅ 能。下单 → 占用库存 → 微信支付 → 发货 → 收货 → 佣金生成 → 到期结算，主链路完整 |
| 安全基线 | ⚠️ 中等偏上。鉴权、验签、IDOR、防超卖、防注入这些"容易出大事故"的地方做得**比多数 MVP 好**；扣分项集中在**配置与密钥管理**和**缺失的授权层** |
| 代码质量 | ⚠️ 中等。分层清晰、金额全 BigDecimal、无 SQL 注入；但**测试几乎为零**，且已经因此漏掉一个真实的资金 bug |
| 产品完整度 | ⚠️ 偏薄。核心交易闭环可上线，但**退款只能去微信商户后台手工操作**、优惠券/积分是空壳、后台无权限分级 |
| 上线阻塞项 | 🔴 你列的 4 条 P0 **全部确认成立**；本次另发现 **2 条 P0**（支付回调与超时取消的资金竞态、生产配置模板未生效引发的连带问题） |

**已确认的"没问题"的部分，我逐条复核后同意**（详见 §4）：JWT 签名/过期/jti 单点登录、支付回调验签+时间戳+平台公钥比对、订单/购物车 IDOR 防护、下单价格取后端 SKU、库存 CAS 防超卖、佣金唯一键幂等、上传路径穿越防护、无 SQL 注入、无硬编码前端密钥、CORS 无 `*`、容器端口仅绑 127.0.0.1。

---

## 1. 你已发现问题的逐条确认

### 🔴 P0-1 · 生产 JWT 密钥是公开泄漏值 —— ✅ 确认，且比你说的更严重

| 项 | 结论 |
| --- | --- |
| 该值是否真的在公开仓库历史里 | ✅ 确认。`application.yml` 曾有兜底默认值 `${JWT_SECRET:dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGp3dCB0b2tlbiBzaWduaW5n}`，出现在多个历史 blob（`git grep` 命中至少 10 个 commit） |
| 当前 main 分支代码 | ✅ 已修好。`src/main/resources/application.yml:63` 现为 `secret: ${JWT_SECRET}`（无兜底），启动时无该环境变量会直接失败——这是正确做法 |
| 服务端 `.env` 是否仍在用这个泄漏值 | ⚠️ 无法从仓库验证（`.env` 未提交）。若确如你所述，则**当前生产密钥 = 历史泄漏值** |
| "靠 Redis jti 兜住了"是否成立 | ✅ 成立，但**仅限"伪造任意新 token"这一种攻击**。我核对了 `AuthSessionService.validateAdmin/validate`：要求 token 的 `jti` 与 Redis 中该账号的 jti 完全相等，随机 jti 会被拒。所以泄漏密钥**不能直接登录**。 |
| **但存在两条你没提到的利用路径** | ① **jti 被读出即可完全接管**。攻击者一旦拿到 Redis 里任一账号的 jti（Redis 无密码、或运维误执行 `CONFIG GET`/`MONITOR`/`KEYS auth:*`、或恢复了一份 `dump.rdb`——仓库根目录就有个 `dump.rdb`），配合已知密钥即可离线签发**任意用户、任意管理员**的合法 token，且无需知道密码。② **主动踢人**。用已知密钥可伪造出"过期/畸形但签名有效"的 token 去探测接口差异，属于信息泄露。 |

**风险定级：P0（确认）。** 结论：**立即轮换，且必须同时轮换一切和该密钥同源的产物**——轮换后所有存量 token 自动失效（jti 会话还在，但签名不再匹配），这是期望行为。

**修复**
1. 生成新密钥：`openssl rand -base64 48`（JwtUtil 会优先按 Base64 解码并要求 ≥32 字节，48 字节 Base64 正好合适）。
2. 服务器更新 `.env` 的 `JWT_SECRET`，`docker compose up -d backend`。
3. **清理历史**：仅删 main 上的默认值不够，历史仍在 `git log` 里。二选一：
   - 简单：仓库转私有 + 视为已泄漏并永久轮换（本项目推荐，成本最低）；
   - 彻底：`git filter-repo --replace-text` 清洗后强推，并要求所有协作者重新 clone。
4. **顺手修一个隐藏问题**：仓库根目录的 `dump.rdb`（Git 忽略但存在于工作区）含有登录态 jti，**不要**把它或 redis 数据卷拷贝到任何地方，建议直接删除。
5. 加一道保险：在 `JwtUtil` 构造时校验密钥不等于已知泄漏值，命中就启动失败（防止回滚配置时又被写回去）。

---

### 🔴 P0-2 · 生产 MySQL root 密码 `Aa790205` 泄漏 —— ✅ 确认（位置比你说的还多一处）

| 位置 | 是否确认 | 说明 |
| --- | --- | --- |
| 本地 `master` 分支历史 | ✅ `c24a20b`（"金霖二十四养私域营销项目"）的 `application.yml:9` 明文 `password: Aa790205` |
| `.idea/workspace.xml:154` | ✅ `<env name="B_PASSWORD" value="Aa790205" />`，且 `.idea/` 已 commit 进历史 |
| `target/jinlin24th-1.0.0.jar` | ✅ 内含 `BOOT-INF/classes/application-dev.yml`，其中 `password: ${DB_PASSWORD:Aa790205}` |
| **`src/main/resources/application-dev.yml:9`（当前工作区）** | ⚠️ **仍在硬盘上，且值是 `Aa790205`**。已被 `.gitignore` 忽略、也被 `.dockerignore` 排除，所以不会进仓库和镜像——但违反最小暴露原则，应清除 |
| `origin` 是否已推送 | ✅ 确认 `origin` 只有 `main` / `uniapp-brand-polish` / `uniapp-ui-polish`，`master` 未推送。**只要不推 master 就不会全网公开** |
| `target/*.jar` 是否被 Git 跟踪 | ✅ 确认未跟踪、历史中也无 `*.jar`（`git log --all -- '*.jar'` 为空） |

**风险定级：P0（确认）。** 当前暴露面 = 本地开发机 + `.idea` + 构建产物；一旦误推 master 立即全网公开。

**修复**
1. 轮换 MySQL 密码：`ALTER USER 'root'@'%' IDENTIFIED BY '<新密码>';`（或改用非 root 账号，见 P1-新2）。同步更新服务器 `.env` 的 `MYSQL_ROOT_PASSWORD`。
2. 删除 `src/main/resources/application-dev.yml` 中的明文兜底：`password: ${DB_PASSWORD:}`（与主配置一致），真正的本地密码只放本机环境变量或 IDEA Run Configuration 的**非提交**配置里。
3. 把 `target/` 整个删掉重建；不要拷 jar 给别人。可选：`git gc --prune=now` 后确认 `git log --all -S'Aa790205'` 为空（本地历史清理，不影响 origin）。
4. 若将来要推 master：**先执行 `git filter-repo` 或直接废弃 master 分支**（分支已无价值）。

---

### 🔴 P0-3 · 退款回调 bug（资金安全）—— ✅ 确认，定位精确

**证据链（逐行核对）**

```
WxPayServiceImpl.java:243   String refundId = jsonNode.get("refund_id").asText();   // 微信退款单号
WxPayServiceImpl.java:251   paymentRecord.setRefundId(refundId);                    // 存进 refund_id 列
WxPayServiceImpl.java:407   String outRefundNo = jsonNode.get("out_refund_no").asText(); // 回调给的是商户退款单号
WxPayServiceImpl.java:415   paymentRecordMapper.selectByRefundNo(outRefundNo);      // 却拿它当 refund_id 查
PaymentRecordMapper.java:27 @Select("SELECT * FROM payment_record WHERE refund_id = #{refundNo} LIMIT 1")
```

`out_refund_no ≠ refund_id`，且**数据库里没有任何一列保存过 `out_refund_no`**（我核对了 `payment_record` 的建表语句 `sql/jinlin24th_init.sql:396-411` 与 `sql/payment_record.sql`：只有 `refund_id`）。因此该查询**恒返回 null**，随后 `WxPayServiceImpl.java:416-418` 直接 `log.error("退款记录不存在")` 并 `return false`。

**后果（确认）**：退款成功但 `payment_record.refund_status` 永远是 1（退款中）、订单永远不进入 60（已退款）、`distributionService.markRefundedByOrderNo()` 永不执行（佣金不退回）；微信收到 `FAIL` 会持续重试，日志被刷爆。

**补充发现**：`refund_order` 表（`sql/jinlin24th_init.sql:531`）**已经**定义了 `refund_no` 与 `refund_id` 两列，正是为此场景准备的，但全项目没有任何 entity/mapper/service 使用它——属于设计到一半的死代码。

**修复（二选一，推荐方案 A）**
- **A（不改表）**：把查询改成按订单号 + 状态定位，最稳的是加一列：
  ```sql
  ALTER TABLE payment_record ADD COLUMN out_refund_no VARCHAR(64) NULL COMMENT '商户退款单号' AFTER refund_id;
  ALTER TABLE payment_record ADD KEY idx_out_refund_no (out_refund_no);
  ```
  `refund()` 里同时写入 `outRefundNo`，`selectByRefundNo` 改为 `WHERE out_refund_no = #{refundNo}`。
- **B（快修，不改表）**：回调里既然已经拿到 `refund_id`（第 408 行），直接按 `refund_id` 查：
  `PaymentRecordMapper` 增加 `selectByRefundId(String refundId)`，`handleRefundNotify` 改用它。缺点是与商户退款单号解耦不清晰，但**改动最小、风险最低**，适合上线冲刺。
- **无论选哪个，必须补一条集成测试**：`refund()` → 构造回调报文 → `handleRefundNotify()` → 断言 `refund_status=2`、订单 `status=60`、佣金 `status=3`。目前 `WxPayServiceImplTest` 的 5 个用例只测了私有方法 `verifyCallbackSignature`，退款回调**零覆盖**——这正是这个 bug 能存活到上线前的原因。

---

### 🔴 P0-4 · 生产 Swagger + SQL 日志全开 —— ✅ 确认，但根因不是"模板没生效"，而是**模板根本没进生产**

**证据**

| 检查项 | 结果 |
| --- | --- |
| 仓库里有无真正的 `application-prod.yml` | ✅ 确认**没有**（`.gitignore:72` 忽略它，`.dockerignore` 也排除它，`Dockerfile` 只 `COPY src`） |
| 生产实际加载什么 | `docker-compose.yml:64` 设 `SPRING_PROFILES_ACTIVE=prod`，但没有 `application-prod.yml` → **只有基座 `application.yml` + 环境变量生效** |
| Swagger | `pom.xml:33-35` 引入 `springdoc-openapi-starter-webmvc-ui:2.5.0`，基座配置**没有任何 `springdoc.api-docs.enabled: false`**（该开关只写在未生效的 `application-prod.example.yml:41-44`）→ `/swagger-ui/index.html`、`/v3/api-docs` 公网可访问。✅ 确认 |
| SQL 日志 | `application.yml:43` `log-impl: org.apache.ibatis.logging.stdout.StdOutImpl` → **每条 SQL 含参数**打到 stdout（`docker logs` 可见，含手机号、金额、token 相关查询）。关闭语句只在模板里。✅ 确认 |
| 连带影响 | 生产同时**丢掉了**模板里的 Hikari 连接池调优、Tomcat 线程池调优、日志文件轮转（`logging.file.name` / `max-file-size` / `max-history`）→ 日志只进 stdout，无轮转、无落盘，`logs` 数据卷实际是空的。⚠️ 新发现 |

**风险定级：P0（确认）**——Swagger 暴露全部接口签名与 DTO 结构，是渗透的第一步；SQL 日志泄露 PII 和业务数据。

**修复（推荐：不再依赖"服务器上手工复制模板"，把生产配置内置到仓库）**
1. **把安全开关写进基座 `application.yml`（默认安全，而不是默认不安全）**：
   ```yaml
   springdoc:
     api-docs: { enabled: false }
     swagger-ui: { enabled: false }
   mybatis-plus:
     configuration:
       log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
   ```
   然后在 `application-dev.yml` 里**反向打开**（dev 才需要 Swagger 和 SQL 日志）。密钥同理，生产缺环境变量就该启动失败——现在已经这样了，方向正确。
2. 或者：把 `application-prod.example.yml` 重命名为 **`application-prod.yml` 并提交**（它本身不含任何密钥，全是环境变量占位符，提交是安全的），再从 `.gitignore` 和 `.dockerignore` 中移除该行。**推荐这条**，因为它同时恢复了生产日志轮转与连接池调优。
3. 上线后立即验证（应全部为 404 / 无 SQL 输出）：
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://api.jinlin24th.cn/v3/api-docs
   curl -s -o /dev/null -w '%{http_code}\n' https://api.jinlin24th.cn/swagger-ui/index.html
   docker logs jinlin-backend --since 5m | grep -c 'Preparing:'
   ```

---

### 🟠 P1-1 · 任意用户可读全部 CRM 客户 —— ✅ 确认

**证据**：`BizCustomerController.java:16` `@RequestMapping("user/bizcustomer")`，暴露 `GET /user/bizcustomer/list`（返回 `CustomerVO`：`name`、`contactName`、`contactPhone`、`totalAmount`、`orderCount`、`tags`、`remark`）和 `GET /user/bizcustomer/get/{id}`（直接返回 `BizCustomer` 实体）。`WebMvcConfig.java:62-69` 的 `JwtInterceptor` 只放行 `/user/appUser/login`、`/user/product/**`、`/user/product/category/**`、`/user/marketing/**`，**`/user/bizcustomer/**` 需要有效用户 JWT**——即任何一个小程序注册用户（微信一键登录即可，无门槛）都能翻走全部 B2B 客户名单。管理端真正的入口是同功能的 `/admin/customer`（`AdminCustomerController`），且前端只用后者（`admin-web/src/api/customer.js`）。

**风险定级：P1（确认）**——不是"未授权"，而是"任意注册用户可越权读取 CRM PII"。性质上接近 P0，但因需要先登录、且不含密码类数据，按 P1 处理并**列为上线前必修**。

**修复**：删除 `BizCustomerController`（推荐——它是 `/admin/customer` 的重复实现，删掉最干净），或把 `@RequestMapping` 改为 `/admin/bizcustomer`。修完用小程序 token 直连验证：`curl -H "Authorization: Bearer <用户token>" .../user/bizcustomer/list` 应返回 404。

---

### 🟠 P1-2 · 退款接口无任何校验 —— ✅ 确认

**证据**：`WxPayController.java:152-165` 接收 `orderNo / refundNo / totalAmount / refundAmount` 四个**纯调用方传入、无任何校验**的参数，直接进 `WxPayServiceImpl.refund()`（`:215-257`）：
- 无 `@Valid`、无金额上限、无正负校验；
- 不校验订单是否存在、是否为已支付状态、是否已退款、累计退款是否超过实付；
- `WxPayServiceImpl.java:226-227` 直接 `refundAmount.multiply(100).intValue()` 传给微信；
- `:247-254` 若按订单号查不到 `payment_record` 就**跳过落库但仍然返回 refundId**；
- 未做"同一订单只允许一个在途退款"的 CAS，两个并发请求（不同 `refundNo`）可双退。

唯一门槛是"任意有效 admin token"（任意后台账号都算超管，见 P1-3）。

**修复**
1. 服务端以 `payment_record.pay_amount` 为唯一事实来源，`refund ≤ pay_amount - 已退金额`，拒绝调用方自定义 `totalAmount`（不要信任客户端传的总额）。
2. 校验订单状态 ∈ {10 待发货, 20 已发货}，`refund_status ∈ {0, 3}`。
3. 用 CAS 占位在途退款：`UPDATE payment_record SET refund_status=1 WHERE order_no=? AND refund_status IN (0,3)`，影响行数为 0 即拒绝。
4. `refundNo` 由服务端生成（如 `RF + orderNo`），不接受客户端传入。
5. `paymentRecord == null` 时**抛异常**，绝不静默成功。
6. 接口路径 `/admin/payment/refund` 加上后续 P1-3 的权限点（如 `order:refund`）。

---

### 🟠 P1-3 · RBAC 未落地 —— ✅ 确认，且比描述更彻底：**服务端 0 校验，前端也是 0 使用**

**证据**
- `AdminJwtInterceptor.java` 全文只做四件事：验签、验过期、验 `tokenType=ADMIN`、比对 Redis jti；**没有任何权限/角色判断**。全项目 `grep permission` 只命中 `SysRolePermissionMapper`、`SysAdminServiceImpl` 的查询方法和 DTO/VO——**没有一处** `hasPermission` / `@PreAuthorize` / 拦截器权限点比对。
- `SysAdminServiceImpl.login()`（`:102-103`）确实查出了 `roles` 和 `permissions` 并返回给前端——**数据链路是通的，只是没人用**。
- 前端同样没用：`admin-web/src/utils/auth.js:64-73` 的 `getRoles()/getPermissions()` **在整个 src 中零调用**，权限只是写进 localStorage 再也没读；`routes.jsx` 所有菜单对所有账号无条件渲染。

**结论**：当前**任意后台账号 = 超级管理员**，后端无任何功能级隔离。若实际上只有一个管理员在用，这是"可接受的技术债"；一旦要招第二个运营/客服，就是**必须立即修**的越权风险。

**风险定级：P1（确认）**——MVP 单管理员场景下不阻塞上线，但必须在上线公告里明确"暂时只开一个后台账号"。

**最小修复（不引入 Spring Security 的方案）**
1. `AdminJwtInterceptor` 里注入 `SysAdminService`，按 `adminId` 取权限码集合放入 `CurrentUserContext`（**并补 `afterCompletion` 清理，见 P2**）。
2. 新增自定义注解 `@RequirePerm("order:refund")` + 一个 `HandlerInterceptor`（或 AOP 切面）比对；先给**高危写操作**挂上：退款、订单改价/取消/完成、商品/库存/SKU 增删改、佣金结算、用户禁用、后台账号管理。
3. `sys_permission` 表已经预置了权限码（`sql/jinlin24th_init.sql:631-645`），直接复用即可，无需改表。
4. 前端用 `getPermissions()` 隐藏无权限菜单（**仅体验优化，安全边界永远在服务端**）。

---

### 🟠 P1-4 · 限流可绕过（信任 XFF 首值）—— ✅ 确认，附带一个你评估过重的修正

**证据**：`RedisRateLimitAspect.clientIp()` 先读 `X-Forwarded-For` 并取**最左侧**（`xff.indexOf(',')` 之前）值；`LoginRateLimitService.getClientIp()` 同样取首值；`OperationLogInterceptor.clientIp()` 也取首值。而 `deploy/nginx/nginx.conf:63,71,101` 用的是 `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`——**追加**而非覆盖，所以客户端自带的 `X-Forwarded-For: 1.2.3.4` 会被 nginx 变成 `1.2.3.4, <真实IP>`，代码取首值就取到了攻击者伪造的值。受影响的限流点：`AppUserController.java:51`（短信发送 5 次/分，**最值钱**——短信轰炸、真金白银）、`:63`（验证码校验 20 次/分）、登录限流（10 次/分，用户端与服务端共用）。

**风险定级：P1（确认，但严重度取决于部署形态）**：`docker-compose.yml:92` 中 backend 只绑 `127.0.0.1:7878`，公网流量**必须**经过 nginx，客户端无法直连伪造。因此当前要利用需要"已经能直连后端"或"上游再套一层会透传 XFF 的代理/CDN"。**一旦**在宝塔/CDN/负载均衡后面部署（这是很常见的下一步），该绕过立刻从"理论"变成"5 分钟写个脚本"。按 P1 处理、上线前修。

**修复（两层都做，成本极低）**
1. nginx 改成**覆盖**：`proxy_set_header X-Forwarded-For $remote_addr;`（保留 `X-Real-IP $remote_addr`）。若未来确实有多级可信代理，用 `real_ip` 模块 + `set_real_ip_from` 白名单而非无条件信任。
2. 代码改成**取最右侧值**（`xff` 最后一个逗号之后），或优先使用 `X-Real-IP`/`request.getRemoteAddr()`。三处 `clientIp` 实现建议抽成一个共用工具类，避免继续各写一份。
3. 短信接口额外加"按手机号"维度的限流（当前只按 IP，换 IP 即可轰炸同一号码）。

---

### 🟠 P1-5 · 默认口令 `admin/123123` 写入初始化 SQL + 公开 README —— ✅ 确认，且**缓解措施确实有效**

**证据**：`sql/jinlin24th_init.sql:650-654` 注释明写"初始密码 123123"，BCrypt 哈希硬编码；`README.md:177` 同样公开；`docs/docker-deploy.md:61` 也写了。缓解：`must_change_pwd=1`，`SysAdminServiceImpl.login()`（`:106-107`）签发 `scope=password_change` 的受限 token，`AdminJwtInterceptor` 强制该 token 只能访问 `/admin/password`——**这个设计是对的，我确认它真的生效**。

**残留风险（确认）**：从执行 init SQL 到运维首次登录改密之间存在**抢注窗口**。更关键的是——**BCrypt 哈希 + 明文初始密码同时公开，等于给全网提供了"该密码未被修改"的离线验证手段**：任何人都能 `bcrypt.checkpw("123123", "$2a$10$KwwY...")`，命中即说明生产库仍是默认密码。

**风险定级：P1（确认）**——有缓解，但缓解不完整。

**修复**
1. 删掉 `sys_admin` 的种子 INSERT，改成"首次启动若无管理员则生成随机密码并打印到启动日志一次"，或提供 `scripts/init-admin.sh` 由运维交互式设置密码。
2. 无论如何：**上线当天第一件事就是登录并改密**（你说已改，请在服务器上再确认一次 `must_change_pwd=0` 且哈希 ≠ 上述值）。
3. 从 README 与部署文档中删除明文初始密码，改为"初始化后请立即执行 `scripts/init-admin.sh`"。

---

### 🟠 P1-6 · Redis 无密码 —— ⚠️ **部分不成立：默认编排已支持密码；风险被你低估**

**证据（修正你的描述）**：
- `.env.example:5` **有** `REDIS_PASSWORD=change-this-redis-password`；
- `docker-compose.yml:42-47` 的 redis 启动脚本会在 `REDIS_PASSWORD` 非空时加 `--requirepass`，`healthcheck` 也带 `-a`；
- `application.yml:23` 为 `password: ${REDIS_PASSWORD:}`；
- **结论**：不是"缺配置"，而是**服务器上那份 `.env` 没填这个值**（与 P0-1 同源问题）。默认编排是安全的。

**但风险应上调**：Redis 里存的是 **jti 登录态**。结合 P0-1 的已知 JWT 密钥，**读取任一 jti = 完全接管该账号（含管理员）**，无需密码。因此 Redis 未授权访问在本项目中的后果是**认证体系被完全攻破**，不是"中低风险"。

**风险定级：P1（与 P0-1 联动）**。

**修复**
1. 在服务器 `.env` 里填 `REDIS_PASSWORD`（强随机），重启 redis + backend。
2. Redis 继续只绑 `127.0.0.1`（已确认 `docker-compose.yml:52`），并建议在 redis 配置里加 `rename-command FLUSHALL ""`、`rename-command CONFIG ""`、`rename-command KEYS ""`。
3. **立即轮换 JWT 密钥**（P0-1）——这两件事必须一起做，只做一件没有意义。
4. 删除工作区根目录的 `dump.rdb`。

---

## 2. 本次新发现（你清单之外）

### 🔴 NEW-P0-A · 支付回调与超时取消的资金竞态：**钱已收，订单已取消，无自动退款**

**证据**：`WxPayServiceImpl.java:337-345` 先把 `payment_record.status` 置 1（支付成功）并 `updateById`；`:346-354` 再对订单做 CAS（仅 `status=0` → `10`）；`:363-365` 若 CAS 失败（订单已被超时任务改成 40 已取消）**只打一条 warn 日志，然后 `return true`**（告诉微信"成功"，不再重试）。全项目 `refund()` 只有一个调用方——`WxPayController.java:159` 的管理端手工接口；`grep @Scheduled` 只有 `DistributionSettleTask`，**没有任何对账/补偿任务**。另外支付回调虽然验签严格，但**从不校验回调金额 `amount.total` 是否等于 `order.pay_amount`**（`:310` 解析后未参与判断）。

**当前真实影响（重要修正）**：`app.mq.enabled` 默认 `false`（`application.yml:78`，docker-compose 也默认 `ROCKETMQ_ENABLED=false`），此时 `OrderTimeoutMessageProducer` Bean 不存在，`OrderServiceImpl.java:182-185` 直接跳过——**订单永远不会被自动超时取消**。所以"钱货两空"在当前默认部署下**不会发生**；取而代之的是另一个问题（见 NEW-P0-B）。**一旦启用 MQ，这条立刻变成真实的资损路径。**

**风险定级：P0（确认成立，触发条件 = 启用 RocketMQ；但启用 MQ 是解决库存锁死的必要动作，所以两者必须一起修）**。

**修复**
1. CAS 失败时**不吞掉**：记录告警（邮件/企微/日志 ERROR 专用通道），并落一条"待退款"记录（可复用 `refund_order` 表）交给人工或补偿任务处理；返回 `true` 前必须确保这笔钱有归属。
2. 支付回调**必须**校验金额：`amount.total / 100 == order.payAmount`，不等则拒绝并不改状态。
3. 增加一个每日对账任务（微信账单 vs `payment_record`），至少覆盖"已支付但订单非 10/20/30/60"的异常单。
4. 按 NEW-P0-B 给超时取消加兜底，让这条竞态真正被触发前就有确定性行为。

---

### 🔴 NEW-P0-B · RocketMQ 默认关闭 → 订单超时无兜底 → 库存被永久锁死

**证据**：`application.yml:78` `app.mq.enabled: ${ROCKETMQ_ENABLED:false}`；`OrderServiceImpl.java:181-185` 注释直白写着"未开启时 ObjectProvider 返回 null，不影响下单主流程"。而 `OrderServiceImpl.java:103-104` **在下单时就占用库存**（`occupyInventory`）。因此默认部署下：用户下单不付款 → 库存永久占用 → 没有任何自动释放路径 → 热销 SKU 被少量恶意（或仅仅是犹豫的）用户锁死。管理端有 `/admin/order/{id}/cancel` 可手工取消（`AdminOrderController.java:44-47`），但没有批量、没有超时自动取消。你的清单把这条列为 P2「RocketMQ 关闭时订单超时无兜底（库存可被恶意锁死）」——**定级偏低，建议提到 P0/P1**，因为它是"默认部署形态下必然发生"的问题，而不是"边缘场景"。

**修复（选一，推荐 C）**
- A. 生产启用 RocketMQ（`ROCKETMQ_ENABLED=true` + 部署 broker），但**必须先修 NEW-P0-A**，否则引入资损。
- B. 加一个 `@Scheduled` 兜底扫描（每 1-5 分钟）：`SELECT ... WHERE status=0 AND create_time < NOW()-INTERVAL 30 MINUTE` 分批调用已有的 `cancelUnpaidOrder()`（该方法已有状态 CAS，天然幂等）。无需 MQ 即可闭环。
- C. **B + 保留 MQ 作为快速通道**（MQ 开则延迟消息即时取消，MQ 关则定时兜底兜住）。这是最稳的组合，且改动很小。
- 顺带：给 `@Scheduled` 加分布式锁（ShedLock 或 Redis SETNX），避免未来多实例重复执行（当前 `DistributionSettleTask` 也无锁，仅靠 CAS 缓解）。

---

### 🟠 NEW-P1-A · 生产配置模板未生效的连带运维陷阱（3 个，都会静默失效）

1. **`WX_PAY_NOTIFY_URL` 会被空字符串覆盖默认值。** `docker-compose.yml:86` 写 `WX_PAY_NOTIFY_URL: ${WX_PAY_NOTIFY_URL:-}`，`.env.example:38` 也是空值。空字符串会被当成"已设置"注入容器，Spring 的 `${WX_PAY_NOTIFY_URL:https://your-domain.com/...}` **不会**回退到默认值 → `notifyUrl` 变成 `""`。虽然微信支付请求里 `notify_url` 是可选字段（不传则用商户平台配置），但一旦走这条路径，回调地址就完全依赖商户后台配置，极易配错且**支付成功不会回调**。`WX_PAY_REFUND_NOTIFY_URL` 同病。**修复**：删除 compose 里这两行的 `:-` 空默认（改成不传该环境变量，或让 Spring 用 `:${WX_PAY_NOTIFY_URL:https://...}` 之外的方式处理空值）；并在 `WxPayConfig` 启动时校验 `notifyUrl` 非空且为 https，否则启动失败。
2. **MySQL 直接用 root 连接应用。** `docker-compose.yml:72-73` 把 `DB_USERNAME: root` / `DB_PASSWORD: ${MYSQL_ROOT_PASSWORD}` 传给 backend。应用一旦被 SQL 注入或被攻破，攻击者直接握有实例最高权限（可 `FILE` 读写、可建用户、可 drop 库）。**修复**：init 阶段创建 `jinlin_app` 账号，只授权 `jinlin24th.*` 的 DML，backend 使用它；root 只用于运维。
3. **演示数据被写进生产初始化流程。** `docker-compose.yml:25` 挂载 `./deploy/mysql/init/03-demo-seed.sql`，其内容是 `source .../sql/demo_seed.sql` → 首次部署**必然**插入"演示用户"、假客户"演示客户公司/李四/13900139000"、demo 商品"枸杞红枣茶"（图片指向 `example.com`）。**修复**：把 seed 从默认 init 流程移除（改名不带数字前缀或移到 scripts/），需要演示数据时手工执行。

---

### 🟠 NEW-P1-B · 库存恢复的重试在同一事务内，等于不会重试（库存静默丢失）

**证据**：`OrderServiceImpl.java:380-432`。CAS 恢复库存失败后进入"重试"分支（`:396-418`），但 `findInventoryForRestore(item)` 是在**同一个 `@Transactional` 内**重新查询——MySQL 默认 REPEATABLE-READ 返回的是同一事务快照，`beforeStock` 与第一次完全相同，CAS 条件 `eq(stock, beforeStock)` 必然再次失败。最终 `:414-417` 只 `log.error("需人工介入")` 然后 **`return`（正常返回）**，`cancelUnpaidOrder` 继续返回 `true`——**订单取消了、库存没还、没有任何异常或补偿**。

**修复**：改成单条原子语句 `UPDATE inventory SET stock = stock + #{qty} WHERE id = #{id}`（无需 CAS），失败才抛异常让整个取消事务回滚；或把重试挪到事务外并加告警。

---

### 🟠 NEW-P1-C · 超时取消消费者"先占坑、后干活"，崩溃会导致订单永不取消

**证据**：`OrderTimeoutCancelConsumer.java:52-58`——先用 `setIfAbsentRequired(consumedKey, "1", 7天)` 占位，**再**执行 `cancelUnpaidOrder`。若 JVM 在 SETNX 成功与业务完成之间崩溃（或 DB 卡死导致 Pod 被杀），`catch` 分支的 `delete(consumedKey)` 不会执行 → 消息被 RocketMQ 重投时看到 key 存在 → 判定为重复消费直接 ack → **订单永久停留在待付款，库存不释放**，且没有任何地方会发现。`SmsMessageConsumer.java:51-58`、`OperationLogConsumer.java:53-60` 是同一模式（影响较小）。
**修复**：改成"先处理、成功后再写已消费标记"；或写 `status=processing` 并带短 TTL，失败/超时可重入。

---

### 🟠 NEW-P1-D · 下架商品 / 禁用 SKU 仍可下单

**证据**：`OrderServiceImpl.java:94-101` 只做 `getById(skuId)` / `getById(productId)`，**从不检查 `sku.getStatus()` 或 `product.getStatus()`**。攻击者（或缓存了旧链接的用户）直接 POST 已下架 SKU 的 id 仍能创建订单。
**修复**：`if (!Integer.valueOf(1).equals(sku.getStatus()) || !Integer.valueOf(1).equals(product.getStatus())) throw BizException.badRequest("商品已下架");`

---

### 🟠 NEW-P1-E · 优惠券与积分是"空壳功能"，但接口已暴露

**证据**：`UserCouponController` 只有 `/available`、`/my` 两个查询接口；全项目**没有任何** `user_coupon` 的 INSERT（无领券入口），`coupon.received_count` / `used_count` 只在创建时初始化为 0（`CouponServiceImpl.java:50-51`）从不递增；下单时 `master.setDiscountAmount(BigDecimal.ZERO)` 硬编码（`OrderServiceImpl.java:148`），`pointsUsed/pointsGained` 也恒为 0。前端能查到"可用优惠券"（若库里手工插了数据），但下单**永远用不上、也不会回滚**。
**修复**：最小可上线方案 = **把优惠券/积分相关接口和菜单先摘掉**（避免用户看到用不了的功能产生投诉），排期后再实现完整的"领券 → 下单占用 → 取消/退款回滚"。`OrderMaster.status` 注释中的"50-退款中"也无任何代码写入，属同类未完成状态。

---

### 🟡 其它新发现（P2，可排期）

| 问题 | 位置 | 说明 |
| --- | --- | --- |
| 分页 `size` 无边界，`size=-1` 可全表导出 | `MybatisPlusConfig.java:16` + 15 个 controller | MyBatis-Plus 未设 `maxLimit`，`size<0` 时该拦截器**跳过分页**。`/user/order/list?size=-1` 或任意管理端列表可拖垮服务 / 导出全表。修复：`new PaginationInnerInterceptor(DbType.MYSQL).setMaxLimit(100L)` + 共享校验 |
| 日志里明文打手机号 / 支付参数 | `WxPayServiceImpl.java:217,312,412`、`OperationLogInterceptor` 记录 requestParams | 与 P0-4 叠加后，stdout 与操作日志表都含 PII。建议手机号脱敏后再打 |
| 管理端错误提示被自身 401 逻辑吞掉 | `admin-web/src/utils/request.js:47-53` | 密码错误返回 401 → 拦截器 `clearAuth()` + `window.location.href` 整页跳转，**下一行的 `messageApi.error()` 永远不会显示** → 用户看到"表单被莫名清空"。修复：登录请求排除出 401 分支，且已在 `/login` 时不跳转 |
| 后续登录页 `redirect` 开放重定向 | `LoginPage.jsx:33,49` + `react-router 7.15.0` | **已确认可利用**：该版本落在 GHSA-wrjc-x8rr-h8h6 影响区间（`>=6.0.0 <7.18.0`），链路为 `new URL('/\\evil.com','https://admin.jinlin24th.cn').href === 'https://evil.com/'` → react-router 内部 `pushState` 跨源抛 SecurityError → 回退到 `window.location.assign(url)` 的真实跳转。利用方式：`https://admin.jinlin24th.cn/login?redirect=/\evil.com`，管理员登录成功后落到攻击者站点（典型登录页钓鱼）。`ProtectedRoute` 只负责**写**安全值，**读**的时候零校验。修复：只接受 `/` 开头且不含 `//`、`\` 的路径（或比对 `new URL(r, location.origin).origin`）；同时把 react-router-dom 升到 ≥7.18.2 |
| 依赖漏洞 | `admin-web/package.json:15,19` | `npm audit --registry=https://registry.npmjs.org`：**17 项（10 high / 2 moderate / 5 low / 0 critical）**。逐条核实后修正：axios 1.16.0 虽命中 **10 条**公告，但其中**只有 1 条为 high，且仅 Node HTTP adapter 可达**（浏览器产物中 `follow-redirects`/`proxy-from-env`/`http-proxy-agent` 被 Vite 映射为 null，不可达）；其余为原型污染/DoS/`maxBodyLength` 类中低危。react-router 7.15.0 命中 6 条，**其中开放重定向是真实可达的**（见上一行）。剩余 high 集中在 vite/esbuild/postcss/babel/browserslist 等**构建期**依赖。注意 `admin-web/.npmrc` 指向 npmmirror，其 audit 端点返回 `[NOT_IMPLEMENTED]`，必须显式指定官方 registry，否则会误以为"无漏洞"。两条直接依赖都有可用修复版本且**在已声明的 `^` 范围内**（axios→1.18.0/最新 1.20.0，react-router-dom→7.18.2），`npm audit` 报 "No fix available" 具有误导性 |
| 下单事务内做同步 MQ/短信 I/O | `OrderServiceImpl.java:182-191` | 同步发送 + 重试最多占用 3×3s 事务/连接；且事务回滚后短信与超时消息已投出。项目里已有正确范式（`OrderCreatedEventListener` 用 `AFTER_COMMIT`），照抄即可 |
| 订单明细静默丢弃 | `OrderServiceImpl.java:90-131` | 购物车 3 件中有 1 件 SKU 无效时，返回的订单只有 2 件，**不报错**。用户会为"少发的货"投诉。修复：抛 400 并指明 skuId |
| 库存不足抛 `IllegalArgumentException` | `OrderServiceImpl.java:343` | 被 `GlobalExceptionHandler` 兜成 **HTTP 500「系统繁忙」**，前端无法提示"库存不足"。修复：改 `BizException.badRequest` |
| `product_sku.stock` 是"死"的第二库存源 | `CartAppServiceImpl.java:157`、小程序 `product-detail/index.vue:44`、`cart/index.vue:83` | 下单只改 `inventory.stock`，**从不改** `product_sku.stock`，而小程序"余量 {{sku.stock}}"和购物车上限都读后者 → **用户看到的库存永远不变**（卖光仍显示有货、加购上限错误）。这是**用户可感知的功能 bug**，建议排到 P1 一起修：展示库存统一从 `inventory` 派生 |
| 佣金退回会覆盖"已结算"记录 | `DistributionServiceImpl.java:259-269` | `markRefundedByOrderNo` 把 `status≠3` 全部改成 3，包括**已结算（2）已打款**的记录，抹掉付款凭证且无冲正。修复：仅允许 `0/1 → 3`，`2` 需生成负向冲正记录 |
| 结算任务全表加载 | `DistributionServiceImpl.java:142-144` | 每晚把全部待结算记录 `list()` 进内存 + 无界 `IN` 查询。量起来后会 OOM |
| 导出 CSV 公式注入 + 无上限 | `DistributionServiceImpl.java:276-305, 412-417` | `escapeCsv` 只处理 `"` `,` `\n`，**未中和** `=`/`+`/`-`/`@` 开头；昵称/备注可控 → 财务用 Excel 打开即触发公式。且 `.list()` 无上限。修复：前缀加 `'` 或引号包裹，加分页/流式 |
| 消费者幂等键 TTL 7 天但没有"坏消息重放"入口 | `OrderTimeoutCancelConsumer.java:31,88-97` | `mq:bad:*` 只写不读，坏消息永久躺在 Redis，需要人工捞 |
| `AdminJwtInterceptor` 不清理 ThreadLocal | `AdminJwtInterceptor.java:52-55` | 与 `JwtInterceptor.java:49-52` 不对称（后者清 `CurrentUserContext`）。企业级线程池下属资源/信息泄漏 |
| 管理端无登出接口 | `AdminAuthController.java` 全文 | `AuthSessionService.onLogoutAdmin()` 已实现但**无任何 Controller 调用**。前端"退出登录"只清 localStorage，服务端 jti 仍有效 → **token 泄漏后有整整 7 天可用期**（`jwt.expiration=604800000`），且无法主动踢人。修复：加 `POST /admin/logout` 调 `onLogoutAdmin`，前端调用后再清本地 |
| 全项目零 `@Valid` | 所有 controller | `WxPayPrepayDTO` 上的 `@NotBlank/@NotNull` 因无 `@Valid` 而**完全失效**；`OrderCreateDTO`（收货人姓名/电话/地址）无任何约束 |
| 无 `@Version` / 无乐观锁插件 | `MybatisPlusConfig` | 关键状态靠手写 `eq(status)` CAS（正确），但 `payment_record` 的退款状态是普通读改写 → 并发退款回调可能丢失更新 |
| 上传只校验扩展名，无魔数校验 | `AdminUploadController.java:38-48`、`UserUploadController.java:38-48` | `ALLOWED_EXTENSIONS` 白名单 + UUID 重命名 + 路径 `normalize()` 已防穿越（这部分是对的），但没有文件头校验，可上传 HTML/SVG 内容伪装成 `.png`。缓解：`UploadFileController` 强制按扩展名设置 `Content-Type` 且 nginx 未做内容嗅探。建议补魔数校验（如 `ImageIO.read` 或 Tika） |
| 上传 URL 来自可伪造 Header | `AdminUploadController.publicBaseUrl()` | 读 `X-Forwarded-Host` / `Host` 拼 URL，未校验白名单 → 可诱导返回攻击者域名的图片地址。生产已配 `APP_PUBLIC_URL`（走该分支即安全），建议配置缺失时**启动失败**而不是回退到 Header |
| 实体类 Javadoc 乱码 | 59 行，集中于 `pojo/entity/*` | 如 `BizCustomer.java:9` "客户名�?"、`MemberLevel.java:14`。不影响编译（我逐文件校验过：**全部是合法 UTF-8，仅注释内字符损坏**），但严重伤害可读性，建议批量修复 |
| 死代码 | `refund_order` 表、`src/main/resources/com/jinlin24th/jinlin/mapper/AppUserService.xml`（namespace/type 全错且不在 `mapper-locations` 里）、`TestController`（`@Profile("dev")`）、`AppUserService.getUserList()`、`DistributionService.getRequired()`、`application-dev.example.yml` 里的 `admin.username/password` | 清理可降低维护成本；`TestController` 在 profile 默认 dev 时可达，建议直接删 |
| 前端小坑 | `admin-web/src/components/CrudTable.jsx:172` 导出 CSV 时读的是**渲染期**的过滤条件 → 不点"查询"就导出全量；`:66-83` 无请求序号防竞态（慢的旧响应会覆盖新结果）；`:296-307` `RemoteSelect` 逐键触发、无防抖、无顺序保护，且 `try/finally` **无 `catch`** → 未处理 rejection；`DashboardPage.jsx:15-17` / `ProductPage.jsx:14-19` `.then()` 无 `.catch` → 失败时静默显示 0/空；`DistributionPage.jsx:28` `.catch(() => {})` 吞错、`:44-52` `downloadCsv` 无 try/catch、无 loading，`revokeObjectURL` 紧跟 `click()`（Firefox/Safari 下会打断下载） | 均为 P2，但第 1、4 条用户可感知 |
| `dayjs` 是"幻影依赖" | `admin-web/src/components/CrudTable.jsx:4` | 直接 `import dayjs from 'dayjs'`，但 `package.json` **未声明**该依赖（仅通过 antd 传递引入）。当前 npm 扁平化安装下能跑，换 pnpm 严格布局或 antd 移除该依赖即构建失败 |
| 登录页 `redirect` 丢失查询参数 | `admin-web/src/utils/request.js:51`、`router/index.jsx:16` | 只用 `location.pathname` 构造回跳地址，`?id=123` 这类深链参数在重新登录后丢失。修复：拼上 `location.search + location.hash`（仍需同源校验） |
| 静态资源缓存策略缺失 | `admin-web/deploy/nginx.conf:14-16` | 无缓存策略：带 hash 的 `/assets/*` 每次请求都协商缓存，`index.html` 可能被启发式缓存 → 发版后出现"旧 index 引新 chunk"白屏。修复：`/assets/` 加 `immutable` 长缓存，`index.html` 加 `no-cache` |
| 前端资源未声明 | `admin-web/src/components/CrudTable.jsx:4` 用了 `dayjs` 但 `package.json` 未声明（仅靠 antd 传递依赖）；`lucide-react`、`typescript` 未被使用；`antd` 6 的 `destroyOnClose` 已弃用（应 `destroyOnHidden`） | 打包健壮性 |
| 缓存/压缩 | 两个 nginx 配置均无 `/assets/*` 的 `immutable` 与 `index.html` 的 `no-cache`，也无 `gzip_vary on` | 发版后出现"新旧 chunk 不匹配"白屏的经典成因 |
| CSP 含 `unsafe-inline` | `deploy/nginx/nginx.conf:94` | 构建产物 `index.html` 无内联脚本，`script-src 'unsafe-inline'` 属无谓削弱；token 存 localStorage 的情况下这层很重要。建议移除并补 `object-src 'none'; base-uri 'self'` |
| 内层 nginx 无安全头 | `admin-web/deploy/nginx.conf:1-69` | 安全头只加在外层代理。当前 admin-web 未对外发布端口（`.env.example:12` 的 `ADMIN_WEB_HOST_PORT` 实际是**死变量**，compose 里没用到），但一旦按文档直接暴露 8080 就裸奔。建议内层也补齐 |
| 无数据库备份策略 | 全仓库 | `grep -i backup\|mysqldump` 零命中。数据全在 docker volume `mysql_data`，宿主机故障 = 数据全失。**上线前必须至少有一条 `mysqldump` 日备 + 异地保留** |
| 无监控/告警 | 全仓库 | `GlobalExceptionHandler` 只记日志；无 `/actuator/health`（pom 里未见 actuator）、无错误率告警。上线后出问题只能靠用户投诉发现 |

---

## 3. 阶段一：专业工程师视角 —— 代码质量与安全

### 3.1 做得好的地方（确认，不是客套）

| 项 | 核对结果 |
| --- | --- |
| 金额精度 | ✅ 全项目金额统一 `BigDecimal`，**没有任何 `double/float` 参与金额运算**。仅 `WxPayServiceImpl.java:129,226,227` 的 `.intValue()` 存在分位截断与 ¥21,474,836.47 以上溢出风险，建议 `movePointRight(2).intValueExact()` |
| SQL 注入 | ✅ 零命中。所有 mapper 用 `#{}`，无 `${}`，无字符串拼接 SQL；`.last(...)` 只用于常量 `LIMIT` |
| 事务 | ✅ 17 处 `@Transactional` **全部**带 `rollbackFor = Exception.class`；唯一自调用（`adminCancelUnpaid → cancelUnpaidOrder`）外层已是事务，无失效 |
| 异常处理 | ✅ `GlobalExceptionHandler` 不泄漏堆栈（统一 `SYSTEM_ERROR`）；但 `WxPayController.java:131,145,163,177,193` 把 `e.getMessage()` 拼进返回体，属例外，建议改为通用文案 + 日志留详情 |
| Redis 键生命周期 | ✅ `RedisUtil.requireTtl` 强制 TTL > 0，所有调用点都传了 TTL → **不存在无界增长的 key** |
| 并发控制 | ✅ 订单状态、库存扣减、佣金结算/资格判定均使用 `eq(旧值)` 的 CAS 更新，而非盲目覆盖——这是本项目最值得肯定的工程决策 |
| 上传路径穿越 | ✅ `normalize()` + `startsWith(root)` 双重校验，正确 |
| 幂等 | ✅ `distribution` 表有 `uk_order_id` 唯一键；支付回调有 `status==1` 幂等短路 |
| 分层与命名 | ✅ controller / service / mapper / pojo 分层清晰，单一职责基本成立；命名一致的、可读的中文注释覆盖面广 |
| 密钥硬编码（Java 侧） | ✅ 未发现硬编码的密钥/AppSecret/token。问题全部集中在**配置文件与部署环节** |

### 3.2 代码质量的主要短板

1. **测试几乎为零（最大风险项）**。221 个 Java 文件只对应 **9 个测试文件 / 33 个 `@Test`**，全部是 Mockito 或反射单测，**没有一个 `@SpringBootTest`**，没有 JaCoCo，也没有集成测试。真正有断言价值的只有 `DistributionServiceImplTest`（约 14 个用例）；`WxPayServiceImplTest` 的 5 个用例只测了私有方法 `verifyCallbackSignature`；`OrderServiceImplTest` **只有 1 个用例，且只测私有方法 `loadOrderUsers`**。
   - 结论：**payment/refund 回调、下单/取消/超时、库存扣减与恢复、优惠券、佣金生成与冲正、登录，全部零测试**。
   - 这不是"测试债"，而是**直接的因果证据**：P0-3 的退款 bug 和 NEW-P1-B 的库存恢复 bug 都是"跑一遍就能发现、但没人跑"的类型。**建议：上线前至少补 3 条集成测试（支付回调、退款回调、下单→取消→库存归位），用 H2 或 Testcontainers，成本约半天，收益是可回滚的信心。**
2. **超大服务类**：`OrderServiceImpl` 624 行、`WxPayUtil` 600 行、`WxPayServiceImpl` 548 行、`DistributionServiceImpl` 418 行。`OrderServiceImpl` 同时承担下单、占/还库存、状态流转、查询、VO 装配五种职责，是后续维护的主要成本来源。建议至少把"库存占用/恢复"抽成 `InventoryAllocator`。
3. **开发者体验欠佳**：包名 `com.jinlin24th.jinlin`（公司名重复）、实体类注释乱码 59 处、`application-dev.example.yml` 里留着已废弃的 `admin.username/password`（登录早已改为读 `sys_admin`）、`TestController` 这类演示代码留在生产包内。
4. **一致的坏习惯**：列表接口普遍不校验 `size` 上界；`@Valid` 一个没用导致 DTO 上的校验注解全部是装饰；`AdminJwtInterceptor` 与 `JwtInterceptor` 在 ThreadLocal 清理上不对称。这些都是"单点小疏漏 × 全项目复制"的模式，适合用一次统一收口（共享分页参数对象 + 统一校验切面 + 统一上下文清理）解决。
5. **构建与依赖**：`admin-web/.npmrc` 指向 npmmirror 导致 `npm audit` 静默失效（必须显式 `--registry=https://registry.npmjs.org`）；Dockerfile 用可变 tag（`node:22-alpine`、`nginx:1.27-alpine`）、无 `HEALTHCHECK`、无 `USER`（以 root 运行）、无 `NODE_ENV=production`；`vite`/`@vitejs/plugin-react`/`typescript` 被放在 `dependencies`。

### 3.3 安全总体评分

| 安全域 | 评级 | 依据 |
| --- | --- | --- |
| 认证（Authentication） | 🟢 良好 | JWT 验签/过期完整；jti 单点登录/登出/踢人可用；管理员 BCrypt + 强制改密；登录限流已有 |
| 授权（Authorization） | 🔴 缺失 | 服务端 0 权限校验；`/user/bizcustomer` 越权读 CRM；前端权限数据查了但不用 |
| 会话与密钥管理 | 🔴 差 | 生产密钥疑为公开泄漏值；Redis 无密码；管理端无服务端登出 |
| 支付与资金安全 | 🟠 中 | 验签+时间戳+平台公钥比对做得扎实（确认）；但退款查询 bug、退款无校验、回调不校验金额、支付/取消竞态 |
| 输入与注入防护 | 🟢 良好 | 无 SQL 注入；路径穿越已防；XSS 在当前前端无注入点（无 `dangerouslySetInnerHTML`/`v-html`） |
| 并发与数据一致性 | 🟠 中 | CAS 用得好，但库存恢复重试失效、cart 读改写竞态、payment_record 退款状态无锁 |
| 配置与部署安全 | 🔴 差 | Swagger + SQL 日志默认开；root 直连 DB；seed 数据进生产；无备份；CSP 含 `unsafe-inline` |
| 隐私合规 | 🟠 中 | 推荐官手机号明文展示给下游用户；小程序无隐私保护指引；日志含 PII |
| 可观测性 | 🔴 缺失 | 无 actuator/健康检查、无告警、无对账任务 |

---

## 4. 阶段二：产品经理视角 —— 是否合理、是否好用

### 4.1 产品定位与闭环完整度

定位清晰（私域 + 分销的养生电商小程序），主闭环**已经跑通**：

```
微信一键登录 → 首页 → 商品详情 → 加购/立即购买 → 下单（占用库存）
  → 微信 JSAPI 支付 → 支付回调改状态 → 后台发货 → 用户确认收货
  → 生成一级/二级佣金 → 到期自动可结算 → 财务导出 CSV 线下打款
```
推荐官绑定三条路径都实现了：分享链接携带 `inviterUserId`（`c-uniapp/src/utils/invite.ts` + `stores/auth.ts:23-29` 在登录时上送）→ 后端 `resolveValidInviterId` 校验"必须是启用中的分销员"→ 首登自动绑定；也支持结算页手工填 ID 绑定。**这条链路设计得比很多同类 MVP 完整。**

### 4.2 管理端（admin-web）可用性

**做得好的**：菜单/面包屑/子路由由 `routes.jsx` 单一数据源生成（新增页面成本低）；`CrudTable` 把列表/筛选/分页/弹窗表单抽象成了一个可复用组件（371 行覆盖十几个页面）；首登强制改密是不可跳过的 Modal（`LoginPage.jsx`）；表格列宽、状态徽标、筛选器统一样式。

**主要缺口（按产品影响排序）**
1. 🔴 **退款没有后台入口**。后端有 `/admin/payment/refund` 接口，**前端没有任何页面调用它**。一旦上线，客服收到退款诉求只能登录**微信商户平台手工退款**，而手工退款**不会**触发系统内的订单/佣金状态变更（即使修好 P0-3 也一样，因为回调关联的是系统发起的退款单）。这是**上线后第一天就会遇到**的运营阻断。
2. 🟠 **无法导出订单**。只有分销佣金页能导 CSV。财务/运营需要订单流水、发货清单时只能从数据库捞。
3. 🟠 **无库存预警可视化**。`inventory.warning_stock` 字段存在、可编辑，但没有任何"低于预警值"的筛选或角标。
4. 🟠 **商品/SKU 列表无批量操作**，无批量上下架、无批量改价——养生品常有几十个 SKU，逐个改很痛。
5. 🟠 **无新订单提醒**。没有角标、没有声音、没有 WebSocket 轮询。管理员不主动刷页面就会漏单（直接影响履约时效）。
6. 🟡 **无客户跟进台账入口的关键信息聚合**；`CustomerPage` 依赖 `/admin/customer`，而重复的 `/user/bizcustomer` 是安全隐患（P1-1），删掉它不影响前端。
7. 🟡 **权限 UI 完全不用**（P1-3）：所有账号看到全部菜单，且后端也放行。

### 4.3 小程序端（c-uniapp）可用性

**做得好的**：结算页明确展示"本单服务归属哪位推荐官"，减少了佣金归属纠纷；分享邀请按钮 `open-type="share"` 直接携带 `inviterUserId`；订单列表/详情/收货流程完整。

**主要缺口**
1. 🔴 **不存在的隐私保护指引**（`grep privacy/隐私` 在整个 `c-uniapp` 零命中）。**小程序提审的硬性要求**：涉及用户信息（登录、头像昵称、收货地址、手机号）必须在 `app.json`/`manifest.json` 配置 `__usePrivacyCheck__` 或调用 `wx.requirePrivacyAuthorize`，并在微信后台填写《小程序用户隐私保护指引》。**这一条不修，审核大概率不通过。**
2. 🟠 **推荐官手机号明文展示给下游用户**（`c-uniapp/src/pages/my/index.vue:75-76` 显示 `parentUserPhone`，数据来自 `AppUserVO.parentUserPhone`，由 `AppUserServiceImpl.toVO` 填充）。产品意图可能是"方便联系服务专员"，但**明文手机号属于 PII**，既是竞品挖人名单的来源，也不符合最小必要原则。建议改为脱敏展示（`138****0000`）+ 点击唤起客服会话，而不是直接展示号码。
3. 🟠 **库存展示是错的**（见 P2 表：`product_sku.stock` 从不随销售变化）。用户会看到"余量 200"买了 50 件仍然显示 200，卖光也能加购，到结算才报错。
4. 🟠 **优惠券/积分入口与"我的"页若已露出，是空壳**（P1-E）；建议上线版本先摘掉。
5. 🟡 **无客服/售后入口**（无 `<button open-type="contact">`），无物流轨迹（只有状态流转），无退款进度查询。
6. 🟡 **手动填"推荐官ID"对新用户不友好**：用户看不到自己的数字 ID（`my/index.vue:71` 只在未绑定时用昵称兜底显示 `推荐官 {id}`），要求用户手输一串数字的转化率极低。建议给分销员生成短邀请码（如 6 位字母数字）或上线"扫推荐官码"，把手工输入降级为兜底。
7. 🟡 **无头图/公告位的运营内容**：`product.images`、`marketing_activity` 等字段/表已具备，但首页内容运营能力弱（`home/index.vue` 1031 行，偏静态）。

### 4.4 数据与合规

| 项 | 状态 |
| --- | --- |
| 隐私政策 / 用户协议页面 | ❌ 缺失（仓库无任何静态页或入口） |
| 小程序隐私保护指引 | ❌ 未配置（提审阻塞项） |
| 用户注销账号能力 | ❌ 无（《个人信息保护法》要求提供） |
| 数据导出/删除权响应流程 | ❌ 无 |
| 手机号脱敏存储/展示 | ❌ 明文存储、明文展示 |
| 操作审计 | 🟡 表结构与拦截器都有，但**依赖 RocketMQ**；MQ 关闭时（默认）操作日志**完全不落库**（`OperationLogInterceptor` 直接 `log.debug` 跳过） |
| 备份 | ❌ 无 |
| 监控告警 | ❌ 无 |

---

## 5. 上线前必修清单（按执行顺序）

### 第 0 步 · 现在就做（不需改代码，30 分钟内）
1. [ ] 生成并轮换 `JWT_SECRET`（`openssl rand -base64 48`），重启 backend — **P0-1**
2. [ ] 服务器 `.env` 填入 `REDIS_PASSWORD`（强随机），重启 redis + backend — **P1-6**
3. [ ] 轮换 MySQL root 密码，同步 `.env` — **P0-2**
4. [ ] 删除工作区 `dump.rdb`、`target/`、`src/main/resources/application-dev.yml` 中的明文密码
5. [ ] 确认 GitHub 上**没有** `master` 分支，且今后不要推

### 第 1 步 · 生产配置收口（半天）
6. [ ] 提交 `application-prod.yml`（由 `application-prod.example.yml` 改名而来），从 `.gitignore:72`、`.dockerignore` 移除对应行 — **P0-4**
7. [ ] 把 Swagger 关闭与 SQL 日志关闭**改为基座默认**，dev profile 反向打开（防止再次回退）
8. [ ] 修 `docker-compose.yml:86-87` 的空字符串覆盖（`WX_PAY_NOTIFY_URL` / `WX_PAY_REFUND_NOTIFY_URL`）— **NEW-P1-A.1**
9. [ ] 创建低权限 MySQL 账号 `jinlin_app`，backend 改用它 — **NEW-P1-A.2**
10. [ ] 从 init 流程移除 `03-demo-seed.sql` — **NEW-P1-A.3**
11. [ ] 写 `scripts/backup-db.sh`（`mysqldump` + gzip + 保留 14 天 + 每日 cron），并在服务器上跑通一次恢复演练
12. [ ] 上线后验证：`/v3/api-docs` 与 `/swagger-ui/index.html` 返回 404，`docker logs` 无 `Preparing:`

### 第 2 步 · 资金安全代码修复（1 天，含测试）
13. [ ] 修退款回调查询（P0-3，推荐方案 B 快修）
14. [ ] 退款接口补校验：金额上限、订单状态、单笔在途 CAS、`refundNo` 服务端生成 — **P1-2**
15. [ ] 支付回调校验 `amount.total == order.payAmount` — **NEW-P0-A.2**
16. [ ] 支付回调 CAS 失败时告警 + 落待退款记录（不吞异常）— **NEW-P0-A.1**
17. [ ] 补 3 条集成测试：支付回调 / 退款回调 / 下单→取消→库存归位
18. [ ] 用微信沙箱或 1 分钱真实单**完整跑一遍退款**（这是你还没测过的最后一块）

### 第 3 步 · 必需的兜底与越权修复（半天）
19. [ ] 订单超时兜底：加 `@Scheduled` 扫描 + 调用已有 `cancelUnpaidOrder`（无需 MQ 即可闭环）— **NEW-P0-B**
20. [ ] 删除或迁移 `BizCustomerController`（`/user/bizcustomer` → `/admin/**`）— **P1-1**
21. [ ] 修库存恢复：改单条原子 `stock = stock + ?` — **NEW-P1-B**
22. [ ] 修 XFF 首值信任（nginx 改覆盖 + 代码取右侧）— **P1-4**
23. [ ] 消费者幂等键改为"成功后标记" — **NEW-P1-C**
24. [ ] 下单校验 `sku.status` / `product.status` — **NEW-P1-D**
25. [ ] 分页 `maxLimit=100` + `size` 边界校验 — P2 表首行

### 第 4 步 · 上线后 1-2 周内（不阻塞上线，但要排期）
26. [ ] 管理端加退款入口 + 订单导出 + 新单提醒（产品体验，客服刚需）
27. [ ] 小程序配置隐私保护指引（**提审前置**，越早越好）
28. [ ] 推荐官手机号脱敏 + 加客服入口
29. [ ] 修 `product_sku.stock` 展示（用户可感知）
30. [ ] 摘掉优惠券/积分空壳入口，或补全实现
31. [ ] 管理端登出接口（服务端失效 jti，把 token 泄漏窗口从 7 天压到 0）
32. [ ] 前端依赖升级：`axios@^1.20`、`react-router-dom@^7.18.2`；登录页 `redirect` 同源校验
33. [ ] 修管理端 401 吞掉登录错误提示的问题（`request.js:47-53`）
34. [ ] RBAC 落地（复用 `sys_permission` 已有权限码 + 自定义注解，先覆盖高危写操作）
35. [ ] 操作日志去 RocketMQ 依赖（或启用 MQ，同时完成第 3 步 19）
36. [ ] 实体类注释乱码清理、死代码清理（`refund_order`、`AppUserService.xml`、`TestController`）
37. [ ] 加 actuator + 简单的错误率告警

---

## 6. 上线判定

| 问题 | 若不修，上线后最可能发生的具体后果 |
| --- | --- |
| P0-1 JWT 密钥泄漏 | 表面平静；一旦 Redis 被读到 jti（或因 P1-6），攻击者可**无需密码登录任意管理员账号**，包括改价、改库存、导出全部客户手机号 |
| P0-2 MySQL 密码 | 当前仅本地暴露；误推 master 即全网公开，数据库可被直接拖走 |
| P0-3 退款 bug | **第一次真实退款就会遇到**：钱退给用户了，系统里订单还是"待发货"、佣金还在，财务对不上账，只能手工改库 |
| P0-4 Swagger+SQL 日志 | 接口全貌与含 PII 的 SQL 公网可见，成为渗透与数据抓取的第一入口 |
| P1-1 客户数据越权 | 任意注册用户可批量拉走 CRM 客户名单（含电话、消费额）——**这是最可能引发真实投诉/法律风险的一条** |
| P1-2 退款无校验 | 后台账号（当前人人都是超管）可任意金额退款，或并发双退 |
| P1-4 限流可绕过 | 短信费用被刷爆（真金白银），后台可被低成本爆破 |
| NEW-P0-A 支付回调竞态 | 启用 MQ 后出现"用户已付款但订单被取消且不退款" |
| NEW-P0-B 超时无兜底 | 默认部署下**必然发生**：热销 SKU 被未付款订单锁死，运营只能手工逐单取消 |
| NEW-P1-A.1 回调 URL 为空 | 支付成功但系统收不到回调，用户付了钱订单仍是待付款 |
| 隐私指引缺失 | **小程序审核不通过**，无法发布 |

**我的建议**：`第 0 步 + 第 1 步 + 第 3 步`（约 1 天，纯配置与兜底）必须完成；`第 2 步`（1 天）在开放真实支付前必须完成——**现在就能上线的话，只能上是"不接微信支付、仅内部演示"的版本**。全部 P0 与 P1 中标注"必修"的项完成后，这个项目达到"可以接真实资金"的最小可上线标准。它不是一个粗糙的 Demo：鉴权、验签、CAS、金额精度这些关键底座是扎实的，**问题集中在"配置管理与运维环节"和"从未被测试覆盖的资金分支"**——而这两类问题的修复成本都不高。

---

## 附：本次审查中未能验证的事项

1. **服务器 `.env` 的实际内容**（未提交，无法读取）——P0-1、P0-2、P1-6 的"生产实际值"均基于你的描述推定；请以服务器为准。
2. **线上真实响应**：无法从本地发起对 `https://api.jinlin24th.cn` 的探测，Swagger/限流/端口暴露均基于代码与 compose 配置推定。
3. **微信支付真实退款链路**：未在沙箱/真实环境执行，P0-3 的结论来自静态源码链路（该链路是确定性的）。
4. **`dump.rdb` 内容**：文件存在（152 字节，几乎为空），未解析其内容。
5. **npm audit 的精确可达性**：axios 的 9 条中低危多为 Node adapter 场景，浏览器端不可达；react-router 的开放重定向已确认可达。
