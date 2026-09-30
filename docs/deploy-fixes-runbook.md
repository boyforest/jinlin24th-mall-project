# 上线前修复 · 服务器执行手册

对应 `docs/launch-readiness-review.md` 的第 1 批（资金安全）与第 2 批（部署配置）。
代码改动已全部完成并通过本地验证（75/75 测试通过、打包成功、nginx/compose 配置校验通过、备份可真实恢复）。

本手册是**你必须亲自在服务器上执行**的部分。请按顺序执行，不要跳步。

---

## 📌 当前形态：尚未取得微信支付许可（`WX_PAY_ENABLED=false`）

这会改变本次执行的重点，先看清楚：

| 项目 | 当前状态 | 说明 |
|---|---|---|
| 第 1 批资金安全修复 | **代码已修好，但当前不可达** | `WxPayController`（含退款接口）与支付回调 `/api/payment/notify` 都带 `@ConditionalOnProperty(wx.pay.enabled=true)`，支付关闭时这些端点**不存在**（404），不会成为攻击面 |
| P0-1 / P0-2 / P1-6 密钥轮换 | **仍然必须现在做** | 与支付无关，是独立的认证边界问题 |
| Swagger / SQL 日志 / 备份 / XFF 限流 | **仍然必须现在做** | 与支付无关 |
| 数据库迁移（`refund_remark`） | 建议现在做（幂等、无害） | **但在启用支付之前必须已完成**，否则退款会报 `Unknown column` |
| 真实小额退款验证 | **推迟到取得支付许可后** | 改为"启用支付前的强制验证门"，见第 6 步 |

**支付关闭时的启动安全性已核实**：`WxPayConfig`、`WxPayClient`、`WxPayServiceImpl`、`WxPayController`
四个类的条件注解完全一致（均为 `havingValue="true"`），关闭时均不创建；
`UserOrderController` 通过 `ObjectProvider.getIfAvailable()` 优雅降级为"微信支付未启用，请先配置"。
新增的配置 fail-fast 校验同样被该条件门控，**不会**在支付关闭时阻断启动。

---

## ⚠️ 两个必须先知道的前提

1. **必须按顺序执行**。数据库迁移（第 2 步）**必须先于**新代码部署（第 4 步）。
   新代码在退款流程里会显式写 `payment_record.refund_remark` 列，列不存在时退款会直接报
   `Unknown column 'refund_remark'`。支付关闭期间不会触发，但一旦启用支付就会踩到——所以别留到最后。

2. **`.env` 新增了两项必填变量**。`docker-compose.yml` 已改为强制校验，
   缺任意一项 `docker compose up` 会**直接报错拒绝启动**（这是刻意设计，避免静默使用弱配置）：
   - `MYSQL_APP_PASSWORD`（业务数据库账号密码，新）
   - `REDIS_PASSWORD`（从"可选"变为"必填"）

3. **轮换 JWT 密钥会让所有已登录用户掉线**，包括你自己的后台会话。这是预期行为（旧 token 全部失效）。

---

## 第 0 步：备份（不可跳过）

```bash
cd /opt/jinlin24th        # 换成你的实际项目目录
git status                # 确认工作区干净，没有未提交的本地改动
```

**先备份数据库**——出问题时要能回滚：

```bash
docker exec -i jinlin-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --events \
  jinlin24th | gzip -9 > ~/pre-upgrade-$(date +%Y%m%d-%H%M%S).sql.gz
ls -lh ~/pre-upgrade-*.sql.gz    # 确认文件非空
```

---

## 第 1 步：生成新密钥并更新 `.env`

```bash
cd /opt/jinlin24th
cp .env .env.bak-$(date +%Y%m%d-%H%M%S)      # 改坏了好回滚

# 生成三个强随机值（复制输出备用）
echo "JWT_SECRET=$(openssl rand -base64 48)"
echo "MYSQL_APP_PASSWORD=$(openssl rand -hex 24)"
echo "REDIS_PASSWORD=$(openssl rand -hex 24)"
```

```bash
vim .env
```

确保以下项存在且**不再是示例值**：

```env
JWT_SECRET=<上一步生成的 JWT_SECRET>
MYSQL_APP_PASSWORD=<上一步生成的 MYSQL_APP_PASSWORD>
REDIS_PASSWORD=<上一步生成的 REDIS_PASSWORD>
MYSQL_APP_USER=jinlin_app
WX_PAY_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/notify
WX_PAY_REFUND_NOTIFY_URL=https://api.jinlin24th.cn/api/payment/refund/notify
```

检查项：
- `JWT_SECRET` **不能**再是 `dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGp3dCB0b2tlbiBzaWduaW5n`（历史泄漏值）
- `WX_PAY_NOTIFY_URL` 两项**不能为空**（空字符串会覆盖默认值；新代码会启动失败，这是保护）
- `MYSQL_ROOT_PASSWORD` **暂时不要在这里改** —— root 密码在第 2.1 步用它自己的流程轮换（需要旧密码登录）

---

## 第 2 步：轮换数据库密码 + 建业务账号 + 执行迁移

```bash
cd /opt/jinlin24th
set -a; . ./.env; set +a      # 把 .env 读进当前 shell
```

### 2.1 轮换 MySQL root 密码

要点：**必须用「旧密码」登录、设置「新密码」**，然后把新密码写回 `.env`。
（不要用 `.env` 里的值去设同一个值——那是空操作，看着成功其实没换。）

```bash
cd /opt/jinlin24th

# ① 先备份当前 .env（此时 .env 里还是旧 root 密码）
cp .env .env.bak-preroot-$(date +%Y%m%d-%H%M%S)

# ② 取出旧密码 + 生成新密码
OLD_ROOT_PW="$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
NEW_ROOT_PW="$(openssl rand -hex 24)"

# ③ 用旧密码登录，改成新密码
docker exec -i jinlin-mysql mysql -uroot -p"$OLD_ROOT_PW" -e \
  "ALTER USER 'root'@'%' IDENTIFIED BY '${NEW_ROOT_PW}';
   ALTER USER 'root'@'localhost' IDENTIFIED BY '${NEW_ROOT_PW}';
   FLUSH PRIVILEGES;"

# ④ 验证新密码可用（预期输出 1）
docker exec -i jinlin-mysql mysql -uroot -p"$NEW_ROOT_PW" -e "SELECT 1;" 2>/dev/null

# ⑤ 新密码写回 .env，否则下一步重启后容器连不上库
sed -i.bak "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${NEW_ROOT_PW}|" .env
grep '^MYSQL_ROOT_PASSWORD=' .env | sed 's/=.*/=<已更新>/'

# ⑥ 让当前 shell 用上新密码
set -a; . ./.env; set +a
```

> 若第 ③ 步报 `Access denied`，说明 `.env` 里的旧密码与实际不符
> （可能之前手工改过库但没更新 `.env`）。此时先确认真实密码再执行，不要盲目重试。

### 2.2 创建低权限业务账号（backend 不再用 root）

```bash
./scripts/create-app-db-user.sh
```

预期输出：`>>> 完成。请确认 backend 使用的账号`

### 2.3 执行数据库迁移（幂等，可重复执行）

```bash
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th \
  < sql/migrations/20260921_payment_record_refund_remark.sql
```

验证列和索引已创建：

```bash
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SHOW COLUMNS FROM payment_record LIKE 'refund_remark'; SHOW INDEX FROM payment_record WHERE Key_name='idx_refund_remark';"
```

### 2.4 更新 Redis 密码（需重启 redis）

`.env` 里的 `REDIS_PASSWORD` 已是新值，下一步重启容器时自动生效。

---

## 第 3 步：安装备份定时任务

```bash
cd /opt/jinlin24th
chmod +x scripts/backup-db.sh

# 先手工跑一次，确认能产出备份文件
./scripts/backup-db.sh
ls -lh backups/ | tail -3
```

**立刻做一次真实恢复演练**（只验证不覆盖生产数据，用临时库）：

```bash
# 用备份恢复到一个一次性库，确认备份有效
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "DROP DATABASE IF EXISTS jinlin24th_restorecheck; CREATE DATABASE jinlin24th_restorecheck;"

gunzip -c "$(ls -t backups/*.sql.gz | head -1)" | \
  docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th_restorecheck

docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th_restorecheck -e "SHOW TABLES;" | head
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE jinlin24th_restorecheck;"
```

看到表清单 = 备份可用。然后装 cron：

```bash
crontab -e
# 加入下面一行（每天 03:30 备份，保留 14 天）
30 3 * * * cd /opt/jinlin24th && ./scripts/backup-db.sh >> /var/log/jinlin-backup.log 2>&1
```

> 强烈建议再把 `backups/` 同步到另一台机器或对象存储（异地保留）。
> 否则宿主机故障时，备份会和数据一起丢。

---

## 第 4 步：拉取新代码并重启

```bash
cd /opt/jinlin24th
git pull
```

确认新文件到位：

```bash
ls -l src/main/resources/application-prod.yml        # 新增，必须存在
ls -l deploy/mysql/init/04-app-user.sh                # 新增
ls -l sql/migrations/20260921_*.sql                   # 新增
grep -c "demo-seed" docker-compose.yml                # 应为 0
```

先做配置解析自检（不启动服务，能提前发现 `.env` 缺项）：

```bash
docker compose config >/dev/null && echo "✅ .env 完整，compose 配置可解析"
```

重启：

```bash
# ⚠️ 五个服务里有四个这次都变了，只 build backend 是不够的：
#   backend   —— 后端代码
#   mysql     —— 初始化脚本（回填迁移随第 4 步单独执行）
#   redis     —— 密码/配置
#   admin-web —— 管理端前端（退款入口、库存页单仓隐藏、规格页、依赖升级）
#   nginx     —— nginx.conf 是**只读挂载**的，文件改了不会自动生效，必须 restart
docker compose up -d --build backend mysql redis admin-web
docker compose restart nginx
docker compose ps
```

**为什么 nginx 必须单独 restart**（这是个容易漏的坑）：
`docker-compose.yml` 把 `./deploy/nginx/nginx.conf` 以 `:ro` **挂载**进容器。
改文件不会改变 compose 的服务定义，所以 `up -d` **不会重建**该容器，它继续用启动时读入的旧配置。
本次 nginx.conf 改了 `X-Forwarded-For`（改为 `$remote_addr`，共 7 处），
那是**防 IP 伪造**的安全修复（限流与登录 IP 都会受影响）——不 restart 等于修复没上线。

> 若 `docker compose restart nginx` 后配置有误导致起不来：
> `docker exec jinlin-nginx nginx -t` 先验证；回滚用 `git checkout deploy/nginx/nginx.conf && docker compose restart nginx`。

**admin-web 为什么必须重建**：它由 `admin-web/Dockerfile` 在镜像内执行
`npm ci && npm run build` 产出静态文件（不是本机打包后挂载），所以前端改动必须重新 build 镜像才会生效。

### 4.1 小程序必须重新构建并上传（否则用户端仍是旧逻辑）

本轮改了 `c-uniapp`（`effectivePrice` 成交价展示、实时库存、会员价提示、手机号脱敏提示文案）。
小程序是**编译产物**，不重新构建上传，用户看到的一直是旧代码：

```bash
cd c-uniapp
npm run build:mp-weixin     # 产物在 dist/build/mp-weixin
```

然后在**微信开发者工具**中导入 `c-uniapp/dist/build/mp-weixin`，上传体验版 → 提交审核 → 发布正式版。

**⚠️ 不重新上传的后果不只是"看到旧界面"，而是价格显示与实付不一致：**

旧版小程序的购物车仍按 `memberPrice || price` 在**前端自算**金额（非会员会算出会员价 ¥59.90），
而支付金额由服务端按下单快照 `pay_amount` 生成（非会员是 ¥69.90）。
两者不一致会让用户在**购物车看到 ¥59.90、微信支付弹窗却是 ¥69.90**，
属于必然引发质疑与弃付的体验事故。**因此小程序重建上传是启用收款前的强制项。**

---

## 第 5 步：验证（每条都要看到预期结果）

### 5.1 后端正常启动

```bash
docker logs jinlin-backend --tail 50 | grep -Ei "error|exception|failed" || echo "✅ 无错误日志"
docker logs jinlin-backend --tail 5
```

### 5.2 Swagger 已关闭（本次修复的核心目标之一）

```bash
echo -n "/v3/api-docs        -> "; curl -s -o /dev/null -w '%{http_code}\n' https://api.jinlin24th.cn/v3/api-docs
echo -n "/swagger-ui/index.html -> "; curl -s -o /dev/null -w '%{http_code}\n' https://api.jinlin24th.cn/swagger-ui/index.html
```

**预期：两个都是 404**。若返回 200，说明 `application-prod.yml` 没生效，立即停下排查。

### 5.3 SQL 日志已关闭

```bash
docker logs jinlin-backend --since 5m | grep -c "Preparing:" 
```

**预期：0**。若 > 0，说明仍在打印全量 SQL（含参数与手机号）。

### 5.4 业务账号已生效（不再是 root）

```bash
docker exec jinlin-backend sh -c 'echo "DB_USERNAME=$DB_USERNAME"'
```

**预期：`DB_USERNAME=jinlin_app`**

### 5.5 Redis 密码已生效并需要认证

```bash
docker exec jinlin-redis sh -c 'redis-cli ping'                       # 预期：NOAUTH Authentication required
docker exec jinlin-redis sh -c 'redis-cli -a "$REDIS_PASSWORD" ping'  # 预期：PONG
```

### 5.6 旧 JWT 已失效（密钥轮换生效）

用浏览器打开管理后台，**旧的登录态应当失效并跳回登录页**。
然后重新登录（注意：`admin` 账号若有 `must_change_pwd` 会要求改密）。

### 5.7 限流伪造 IP 无法绕过

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " \
    -X POST https://api.jinlin24th.cn/admin/login \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: 9.9.9.$i" \
    -d '{"username":"admin","password":"wrong-password"}'
done; echo
```

**预期：前 10 次 401，之后出现 429**。
关键点：**即使每次伪造不同的 XFF，第 11 次起仍必须被限流**（修复前会因为 IP 一直变化而永远不限流）。

### 5.8 待人工退款记录检查（应为空）

```bash
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT id, order_no, pay_amount, refund_status, refund_remark FROM payment_record WHERE refund_remark IS NOT NULL;"
```

### 5.9 告警日志通道可用

```bash
docker logs jinlin-backend --since 24h | grep -E "PAY-ALERT|ORDER-ALERT" || echo "✅ 无资损/订单异常告警"
```

建议把 `PAY-ALERT`、`ORDER-ALERT` 两个关键字接入告警（邮件/企微）：
- `PAY-ALERT`：用户付了钱但订单没生效，或退款未成功 —— 直接资损信号
- `ORDER-ALERT`：兜底取消订单失败（多为库存记录缺失）—— 需人工核对数据

### 5.10 CRM 客户数据不再暴露给 C 端用户（越权修复）

```bash
echo -n "GET /user/bizcustomer/list -> "
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer <任意小程序用户token>" \
  https://api.jinlin24th.cn/user/bizcustomer/list
```

**预期：404**。若返回 200 并带出客户姓名/电话，说明越权入口仍在，立即停止上线。

同时确认管理端功能没被误删（应返回 401/200，不能是 404）：

```bash
echo -n "GET /admin/customer/list -> "
curl -s -o /dev/null -w '%{http_code}\n' https://api.jinlin24th.cn/admin/customer/list
```

### 5.11 分页越界已被限制（防全表导出）

```bash
for s in -1 1000000 10; do
  echo -n "size=$s -> 返回条数: "
  curl -s -H "Authorization: Bearer <管理员token>" \
    "https://api.jinlin24th.cn/admin/order/list?page=1&size=$s" \
    | python3 -c "import sys,json; d=json.load(sys.stdin).get('data') or {}; print(len(d.get('records') or []))" 2>/dev/null || echo "(解析失败)"
done
```

**预期：`size=-1` 与 `size=1000000` 都只返回 ≤100 条**（修复前 `-1` 会拉走全表）。
`size=10` 返回 10 条。

### 5.12 订单超时兜底能释放库存（默认部署下唯一生效的机制）

`WX_PAY_ENABLED=false` 时 MQ 也关闭，订单超时取消完全依赖这个定时任务。

```bash
# ① 记下某个 SKU 当前库存
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT id, sku_id, stock FROM inventory WHERE sku_id=<测试SKU> ;"

# ② 用测试账号下一笔不付款的订单（占用库存）

# ③ 等待超过 ORDER_TIMEOUT_CANCEL_SECONDS（默认 1800 秒 = 30 分钟）
#    如果想立刻验证，可临时把 .env 的 ORDER_TIMEOUT_CANCEL_SECONDS 改成 60
#    并 ORDER_TIMEOUT_SCAN_INTERVAL_MS 改成 20000，重启 backend 后再测

# ④ 查看兜底任务日志
docker logs jinlin-backend --since 30m | grep -E "订单超时兜底|超时未支付，系统自动取消"

# ⑤ 核对订单状态与库存
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT order_no, status, create_time FROM order_master WHERE order_no='<订单号>';
   SELECT id, sku_id, stock FROM inventory WHERE sku_id=<测试SKU>;"
```

**预期：订单 `status=40`（已取消），库存回到下单前的数量。**

### 5.13 小经验：MQ 关闭时也建议留着兜底任务

兜底任务与 MQ 延迟消息互补：MQ 正常时它几乎无事可做（CAS 返回 false），
MQ 故障/关闭时它是唯一能释放库存的机制。**不要因为启用了 MQ 就把它关掉。**

### 5.14 下单错误码正确（用户能看到"库存不足"而不是"系统繁忙"）

```bash
# 用一个库存为 0 的 SKU 下单（或把某 SKU 库存临时改成 0 再测）
curl -s -X POST https://api.jinlin24th.cn/user/order/create \
  -H "Authorization: Bearer <用户token>" -H 'Content-Type: application/json' \
  -d '{"receiverName":"测试","receiverPhone":"13800138000","receiverAddress":"测试地址",
       "items":[{"skuId":<无库存SKU>,"quantity":1}]}'
```

**预期：业务错误码 `40000`（请求参数错误），message 含"库存不足"**。
修复前会返回 `50000`（系统繁忙）——用户看不懂、前端也没法提示。

### 5.15 购物车部分商品失效时整单失败（不生成缩水订单）

```bash
# 提交两件商品：一件正常、一件 skuId 不存在
curl -s -X POST https://api.jinlin24th.cn/user/order/create \
  -H "Authorization: Bearer <用户token>" -H 'Content-Type: application/json' \
  -d '{"receiverName":"测试","receiverPhone":"13800138000","receiverAddress":"测试地址",
       "items":[{"skuId":<正常SKU>,"quantity":1},{"skuId":99999999,"quantity":1}]}'
```

**预期：返回错误，且数据库里没有新订单**。

```bash
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT order_no, status, create_time FROM order_master ORDER BY id DESC LIMIT 3;"
```

修复前会**静默创建一张只含 1 件商品的订单**（金额也按 1 件算），用户收到的货与预期不符。

### 5.16 佣金导出：公式注入已中和、超限有提示

```bash
# ① 造一条"恶意昵称"的用户（把某个买家昵称改成公式）
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "UPDATE app_user SET nickname='=cmd|''/c calc''!A1' WHERE id=<该订单买家ID>;"

# ② 导出 CSV（管理端「导出 CSV」按钮，或直接调接口）
# ③ 用文本编辑器打开，检查昵称列
grep -o "'=cmd" distribution.csv && echo "✅ 已中和（带单引号前缀）" || echo "❌ 未中和，请立即排查"
```

**预期**：昵称前有单引号（`'=cmd...`）。若不中和，财务用 Excel 打开即触发公式执行。

导出超过 `DISTRIBUTION_MAX_EXPORT_ROWS`（默认 10000）条时，文件末尾必须出现：

```
#注意,#导出已达上限 10000 条,#结果可能不完整,#请缩小筛选范围后分批导出
```

### 5.17 退款时佣金作废语义正确（不抹掉已打款记录）

```bash
# 查看某订单的佣金状态：0-待结算 1-可结算 2-已结算 3-已退回
docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT id, order_no, status, level1_amount, level2_amount, remark FROM distribution WHERE order_no='<订单号>';"
```

**预期**：
- 退款前状态为 0/1 的记录 → 变成 `3`，备注含"退款时尚未结算"
- 退款前状态为 `2`（已打款）的记录 → **状态仍为 2**，备注变为"已结算打款，需人工追回"，
  并在日志里出现告警：
  ```bash
  docker logs jinlin-backend --since 24h | grep "DIST-ALERT"
  ```
  出现该告警表示**有佣金已打款但订单退款了，需要财务人工追回**——建议接入告警通道。

修复前会把已结算记录也改成 3，抹掉财务的付款凭证，且**没有任何提示**。

### 5.18 管理端登出使服务端登录态立即失效

```bash
# ① 登录拿 token
TOKEN=$(curl -s -X POST https://api.jinlin24th.cn/admin/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"<密码>"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# ② 确认 token 可用（预期 200）
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://api.jinlin24th.cn/admin/order/list

# ③ 调用登出
curl -s -X POST -H "Authorization: Bearer $TOKEN" https://api.jinlin24th.cn/admin/logout; echo

# ④ 再用同一个 token 请求（预期 401 —— 服务端登录态已删除）
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://api.jinlin24th.cn/admin/order/list
```

**预期：④ 返回 401**。修复前没有登出接口，token 会一直有效到 7 天后过期——
泄漏的 token 无法主动作废。

### 5.19 库存展示与真实可售量一致（不再显示永不变化的旧值）

```bash
# ① 记录某 SKU 的库存表合计
docker exec -i jinlin-mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT sku_id, SUM(stock) AS 可售合计 FROM inventory WHERE sku_id=<SKU> GROUP BY sku_id;"

# ② 调 C 端 SKU 接口（需用户 token）
curl -s "https://api.jinlin24th.cn/user/product/<商品ID>/skus" \
  -H "Authorization: Bearer <用户token>" | python3 -m json.tool | grep -A2 stock

# ③ 管理端规格列表
curl -s "https://api.jinlin24th.cn/admin/product/sku/list?page=1&size=10" \
  -H "Authorization: Bearer <管理员token>" | python3 -m json.tool | grep -A2 stock
```

**预期：② 与 ③ 的 stock 都等于 ① 的「可售合计」。**

修复前它们返回的是 `product_sku.stock`——一个**不随销售变化**的字段
（下单只扣 `inventory.stock`），因此卖光后仍显示有货。

补充验证：下一笔订单（占用库存）后**立即**再查 ②，库存应当**立刻减少**；
若仍显示旧值，说明 SKU 缓存把库存一起缓存了（本应只缓存静态属性 30 分钟）。

### 5.20 管理端规格页不再有"可编辑但无效"的库存框

打开管理后台 → 「规格」页：
- 库存列标题应为 **可售库存**，且带悬浮说明"各仓库库存合计（只读）"
- 新增/编辑弹窗里**不应再有库存输入框**

原因：该输入框写的是 `product_sku.stock`，而下单只认 `inventory.stock`，
运营照它补货会被误导。调整库存请到「库存」页按仓库设置。

### 5.21 推荐官手机号已脱敏（不在 C 端泄漏他人号码）

```bash
# 需要有一个已绑定推荐官的用户 token
curl -s https://api.jinlin24th.cn/user/appUser/me -H "Authorization: Bearer <用户token>" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('parentUserPhone =',d.get('parentUserPhone'))"

curl -s https://api.jinlin24th.cn/user/appUser/recommender -H "Authorization: Bearer <用户token>" \
  | python3 -c "import sys,json;d=json.load(sys.stdin).get('data') or {};print('recommender phone =',d.get('phone'))"
```

**预期：两处都是脱敏形式（如 `138****8000`），且都不是 11 位明文。**

注意有**两条**泄漏路径：`/me` 的 `parentUserPhone` 与 `/recommender` 的 `phone`
（后者把推荐官当作 VO 主体，完整号码落在 `phone` 上）。两条都必须脱敏。

同时确认**用户自己的手机号仍是完整的**（那是本人的号码，不应被脱敏）：
```bash
curl -s https://api.jinlin24th.cn/user/appUser/me -H "Authorization: Bearer <用户token>" \
  | python3 -c "import sys,json;print('自己的 phone =',json.load(sys.stdin)['data'].get('phone'))"
```

---

### 5.22 会员价只对分销会员生效（本次定价改造）

准备两个小程序账号：**A = 分销会员**（后台「用户」页把 `is_distributor` 打开）、**B = 普通用户**。
同一件商品（售价 69.90 / 会员价 59.90，数量 1）：

```bash
# ① 非分销会员下单：实付必须 = 售价
curl -s -X POST https://api.jinlin24th.cn/user/order/create \
  -H "Authorization: Bearer <B的token>" -H 'Content-Type: application/json' \
  -d '{"receiverName":"测试","receiverPhone":"13800138000","receiverAddress":"测试地址",
       "items":[{"skuId":<SKU>,"quantity":1}]}' >/dev/null
docker exec -i jinlin-mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT order_no, user_id, total_amount, pay_amount FROM order_master ORDER BY id DESC LIMIT 1;"
# 预期：pay_amount = 69.90

# ② 分销会员下单：实付必须 = 会员价
#    （把上面请求换成 A 的 token 再跑一次）
# 预期：pay_amount = 59.90
```

**关键：① 必须是 69.90。** 修复前所有用户都会被无条件按 59.90 计费
（`memberPrice != null ? memberPrice : price`），等于给所有人打折。

**再看展示是否与结算一致**（显示 A 价、结算 B 价是最容易被投诉的问题）：

```bash
# ③ 商品详情（公开接口）：成交价应为售价，并保留会员价作为营销提示
curl -s https://api.jinlin24th.cn/user/product/<商品ID>/skus \
  -H "Authorization: Bearer <任意token>" | python3 -m json.tool | grep -E "price|effectivePrice|memberPrice"
# 预期：effectivePrice = 69.90（= 售价），memberPrice = 59.90（仅用于展示"分销会员 ¥X"）

# ④ 购物车：会员看到 59.90，非会员看到 69.90
curl -s https://api.jinlin24th.cn/user/cart/list -H "Authorization: Bearer <A的token>" \
  | python3 -c "import sys,json;print('会员 effectivePrice =', json.load(sys.stdin)['data'][0]['effectivePrice'])"
curl -s https://api.jinlin24th.cn/user/cart/list -H "Authorization: Bearer <B的token>" \
  | python3 -c "import sys,json;print('非会员 effectivePrice =', json.load(sys.stdin)['data'][0]['effectivePrice'])"
# 预期：59.90 / 69.90
```

**⑤ 缓存串号验证（本次最容易被绕过的一点）**：
先用**非会员**反复访问 ③（把公开接口的 30 分钟缓存写满），再用 **A** 调 ④。

**预期：A 的 `effectivePrice` 仍是 59.90。**
若 A 看到 69.90（或非会员看到 59.90），说明成交价被写进了共享缓存 —— 立即排查。

**小程序侧**：商品详情/首页应显示原价 + `分销会员 ¥59.90` 的小字提示；
加入购物车后，分销会员看到的是会员价。

---

### 5.23 单仓模式与"新建 SKU 自动建库存"（本次上架流程收敛）

**先做存量回填**（否则改动前已建的 SKU 仍然无库存记录、无法销售）：

```bash
# 幂等，可重复执行；仅在"启用仓库恰好 1 个"时生效
docker exec -i jinlin-mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th \
  < sql/migrations/20260921_backfill_inventory_for_existing_skus.sql
```

脚本会先打印「缺少库存记录的 SKU 数」，执行后再打印一次。
**预期：执行后为 0。** 若仍 > 0，说明不是单仓部署（有多个启用仓库），需人工在
「库存」页为这些 SKU 指定仓库。

**再验证单仓模式接口**：

```bash
TOKEN=$(curl -s -X POST https://api.jinlin24th.cn/admin/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"<密码>"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

curl -s https://api.jinlin24th.cn/admin/inventory/mode -H "Authorization: Bearer $TOKEN"
```

**预期（单仓）：** `{"singleWarehouse":true,"defaultWarehouseId":1,"defaultWarehouseName":"主仓库"}`

**最后验证自动建库存行**（这是修复的核心）：

```bash
# ① 在管理端「商品」页新建一个带价格的商品，或「规格」页新建一个规格
# ② 查库确认库存行已自动生成
docker exec -i jinlin-mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
  "SELECT i.id, i.warehouse_id, i.sku_id, i.stock, s.sku_name
     FROM inventory i JOIN product_sku s ON s.id = i.sku_id
    ORDER BY i.id DESC LIMIT 3;"
```

**预期：新建的 SKU 出现在结果中，`stock = 0`。**
随后到管理端「库存」页把数量改成实际值即可销售。

**管理端界面核对（单仓模式）**：
- 「库存」页顶部出现提示条「当前为单仓模式（主仓库），已隐藏仓库相关选项」
- 该页**没有**仓库筛选框、**没有**仓库列、编辑弹窗里**没有**仓库字段
- 页面加载中或接口失败时，仓库选择**照常显示**（保守回退，不会替运营猜仓位）

**多仓回退验证（可选）**：在「仓库」页新建第二个仓库并启用 → 刷新「库存」页 →
仓库筛选/列/表单字段应**自动恢复**（单仓模式是按"启用仓库恰好 1 个"派生的，无需改配置）。

---

## 第 6 步：真实退款验证 ⏸ 推迟 —— 取得支付许可后、启用支付前的**强制门**

> **为什么推迟**：尚未取得微信支付许可，`WX_PAY_ENABLED=false` 时退款接口与支付回调端点
> 都不存在（404），无法也无法验证。
>
> **但它绝不能省**：这正是本次修复的核心，也是此前唯一没测过的链路。
> 在把 `WX_PAY_ENABLED=true` 打开之前，**必须**完成下面的验证。
> 迁移未做 + 退款验证未做，就直接开启真实收款，是当前残留的最高风险动作。

1. 用小程序下**一笔真实小额订单**并完成支付（例如 ¥0.01）
2. 管理端或直接调接口发起退款（`refundAmount` 省略即全额退款）：
   ```bash
   curl -X POST "https://api.jinlin24th.cn/admin/payment/refund?orderNo=<订单号>" \
     -H "Authorization: Bearer <管理员token>"
   ```
3. 观察微信回调是否被正确处理：
   ```bash
   docker logs jinlin-backend --tail 100 | grep -E "退款回调|退款ID|退款处理完成"
   ```
4. 核对数据库（**这是修复前永远失败的断言**）：
   ```bash
   docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
     "SELECT order_no, status, refund_status, refund_id, refund_amount, refund_time
        FROM payment_record WHERE order_no='<订单号>';
      SELECT order_no, status FROM order_master WHERE order_no='<订单号>';"
   ```
   **预期：`refund_status=2`（退款成功）、`order_master.status=60`（已退款）**
5. 若订单已生成佣金，确认佣金被作废：
   ```bash
   docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th -e \
     "SELECT order_no, status FROM distribution WHERE order_no='<订单号>';"
   ```
   **预期：`status=3`（已退回）**

### 退款接口的防误用校验（可顺手验证）

```bash
# 超额退款必须被拒绝（预期 400 + REFUND_AMOUNT_INVALID）
curl -X POST "https://api.jinlin24th.cn/admin/payment/refund?orderNo=<已支付订单号>&refundAmount=99999" \
  -H "Authorization: Bearer <管理员token>"
```

**预期：返回错误，不会真的向微信发起退款。**

---

## 回滚方案

| 出问题的环节 | 回滚动作 |
|---|---|
| 后端启动失败 | `docker logs jinlin-backend --tail 100` 看原因；多半是 `.env` 缺项，补齐后 `docker compose up -d backend` |
| 配置改坏 | `cp .env.bak-<时间戳> .env && docker compose up -d backend` |
| 新代码有问题 | `git log --oneline -3` 找到上一个 commit → `git checkout <上一个commit>` → `docker compose up -d --build backend` |
| 数据库需还原 | 用第 0 步的备份：`gunzip -c ~/pre-upgrade-*.sql.gz \| docker exec -i jinlin-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" jinlin24th` |
| 迁移列需撤销 | `ALTER TABLE payment_record DROP COLUMN refund_remark;`（先确认无代码依赖） |

> 注意：`application-prod.yml` 已提交入库，回滚代码时它会跟着回去。
> 但**基座 `application.yml` 的安全默认值**（关 Swagger / 关 SQL 日志）是独立的，
> 即使回滚到旧代码，也请保留这两处开关，不要退回到"生产开调试"的状态。

---

## 执行完成后的自检表

### A. 本次执行（支付关闭状态下即可完成，全部为必做）

- [ ] 第 0 步备份已生成且非空
- [ ] `.env` 中 `JWT_SECRET` 已换、不再是泄漏值
- [ ] `.env` 中 `REDIS_PASSWORD` / `MYSQL_APP_PASSWORD` 已设置
- [ ] MySQL root 密码已轮换
- [ ] `jinlin_app` 业务账号已创建，`DB_USERNAME=jinlin_app`
- [ ] 迁移已执行，`refund_remark` 列与索引存在
- [ ] 备份 cron 已装，且**恢复演练成功**
- [ ] `docker compose config` 无报错
- [ ] `/v3/api-docs` 返回 404
- [ ] SQL 日志为 0 行
- [ ] `redis-cli ping` 返回 NOAUTH（未认证被拒）
- [ ] 限流伪造 IP 仍然生效（第 11 次 429）
- [ ] `PAY-ALERT` 已接入告警通道
- [ ] `GET /user/bizcustomer/list` 返回 404（CRM 越权入口已删除）
- [ ] 管理端 `/admin/customer/list` 仍可用（未被误删）
- [ ] `size=-1` 与超大 `size` 都只返回 ≤100 条（分页越界已限制）
- [ ] 订单超时兜底任务日志出现「订单超时兜底任务执行完成」
- [ ] 未付款订单在超时时长后被自动取消且库存已归还
- [ ] 下架商品 / 停售 SKU 直接 POST skuId 下单被拒绝（返回 400，不扣库存）
- [ ] 库存不足返回业务错误（400 + 含"库存不足"），不是 500"系统繁忙"
- [ ] 购物车含失效商品时整单失败，数据库里没有"缩水订单"
- [ ] 佣金导出 CSV 中公式前缀已被单引号中和
- [ ] 退款时已结算(2)的佣金状态未被改写，且有 `DIST-ALERT` 告警
- [ ] 管理端登出后旧 token 立即返回 401（服务端登录态已删除）
- [ ] C 端与管理端的库存都等于 `inventory` 各仓合计；下单后立即减少
- [ ] 管理端「规格」页无库存输入框，库存列标注为只读的"可售库存"
- [ ] `/me` 的 `parentUserPhone` 与 `/recommender` 的 `phone` 均为脱敏值
- [ ] 用户**自己**的 `phone` 仍为完整值（脱敏不应误伤本人号码）
- [ ] 非分销会员下单 `pay_amount` = 售价；分销会员下单 = 会员价
- [ ] 公开商品接口 `effectivePrice` = 售价，`memberPrice` 仍下发（营销提示）
- [ ] 购物车：会员/非会员分别看到 59.90 / 69.90
- [ ] **非会员先访问详情后，会员购物车仍显示会员价**（缓存不串号）
- [ ] 已执行 `20260921_backfill_inventory_for_existing_skus.sql`，回填后缺失数为 0
- [ ] 新建 SKU 后库存行自动生成（`stock=0`），到「库存」页填数量后即可售
- [ ] 单仓模式下「库存」页无仓库筛选/列/表单字段，且顶部有单仓提示条
- [ ] `docker compose up -d --build backend mysql redis admin-web` 已执行（admin-web 必须重建）
- [ ] `docker compose restart nginx` 已执行（否则 X-Forwarded-For 防伪造修复不生效）
- [ ] **小程序已重新构建并上传发布**（否则前端显示价与服务端实付价不一致）
- [ ] 确认 `WX_PAY_ENABLED=false`（尚未取得支付许可期间必须保持关闭）

### B. 启用微信支付前（取得许可后必做，缺一不可）

- [ ] 数据库迁移已执行（第 2 步，A 表已勾）
- [ ] 第 6 步真实小额退款全链路通过：`refund_status=2` 且订单 `status=60`，佣金 `status=3`
- [ ] 小额超额退款被拒绝（防误操作）
- [ ] `WX_PAY_NOTIFY_URL` / `WX_PAY_REFUND_NOTIFY_URL` 已填真实域名（非空、https）
- [ ] 微信商户平台回调地址已配置为上述两个地址
- [ ] 配置齐全后再把 `WX_PAY_ENABLED=true` 并重启（配置不全时后端会拒绝启动，这是保护）
