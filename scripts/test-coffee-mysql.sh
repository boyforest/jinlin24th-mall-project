#!/usr/bin/env bash
# 创建并销毁本机专用 MySQL 8 容器，不读取应用/生产数据库配置。
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
test_container="jinlin-coffee-test-$$"
test_tmp="$(mktemp -d)"
live_pid=""
cleanup() {
  if [ -n "$live_pid" ]; then kill "$live_pid" 2>/dev/null || true; fi
  docker rm -f -v "${test_container}-redis" >/dev/null 2>&1 || true
  docker rm -f -v "$test_container" >/dev/null 2>&1 || true
  rm -rf "$test_tmp"
}
trap cleanup EXIT

docker run --rm -d --name "$test_container" \
  -e MYSQL_ROOT_PASSWORD=coffee-test-only -e MYSQL_DATABASE=coffee_phase_a_test \
  -p 127.0.0.1::3306 mysql:8.0 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
for attempt in {1..60}; do
  if docker exec -e MYSQL_PWD=coffee-test-only "$test_container" mysql -h127.0.0.1 -uroot -Nse 'SELECT 1' >/dev/null 2>&1; then break; fi
  if [ "$attempt" = 60 ]; then docker logs "$test_container"; exit 1; fi
  sleep 1
done
mysql_test() { docker exec -i -e MYSQL_PWD=coffee-test-only "$test_container" mysql -uroot "$@"; }

# 存量升级：旧结构和一条历史商品订单，在迁移后完整保留。
awk '/^-- 咖啡阶段 A：/{exit}{print}' sql/jinlin24th_init.sql > "$test_tmp/legacy.sql"
mysql_test < "$test_tmp/legacy.sql"
mysql_test jinlin24th <<'SQL'
INSERT INTO order_master(order_no,user_id,total_amount,pay_amount,receiver_name,receiver_phone,receiver_address)
VALUES('COFFEE_MIGRATION_SENTINEL',1,1.23,1.23,'测试','测试','测试');
SQL
mysql_test jinlin24th < sql/migrations/20261024_coffee_catalog_cart.sql
mysql_test jinlin24th < sql/migrations/20261024_coffee_catalog_cart.sql
[ "$(mysql_test -Nse "SELECT COUNT(*) FROM jinlin24th.order_master WHERE order_no='COFFEE_MIGRATION_SENTINEL' AND pay_amount=1.23")" = 1 ]
[ "$(mysql_test -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='jinlin24th' AND table_name LIKE 'coffee\\_%'")" = 5 ]
mysql_test -Nse 'SELECT COUNT(*) FROM jinlin24th.coffee_store' | rg -x '0' >/dev/null
# 本轮分权与交易增量也必须可重复执行，并保留旧单/旧账号密码。
legacy_hash="$(mysql_test -Nse "SELECT password_hash FROM jinlin24th.sys_admin WHERE id=1")"
mysql_test jinlin24th < sql/migrations/20261026_coffee_workbench_rbac.sql
mysql_test jinlin24th < sql/migrations/20261026_coffee_workbench_rbac.sql
[ "$(mysql_test -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='jinlin24th' AND table_name LIKE 'coffee\\_%'")" = 12 ]
[ "$(mysql_test -Nse "SELECT password_hash FROM jinlin24th.sys_admin WHERE id=1")" = "$legacy_hash" ]
[ "$(mysql_test -Nse "SELECT COUNT(*) FROM jinlin24th.order_master WHERE order_no='COFFEE_MIGRATION_SENTINEL' AND pay_amount=1.23")" = 1 ]
mysql_test -Nse 'DROP DATABASE jinlin24th'

# 新安装：执行部署入口（SOURCE 指向同一主结构），不自动加载咖啡演示数据。
docker exec "$test_container" mkdir -p /docker-entrypoint-initdb.d/sql
docker cp sql/jinlin24th_init.sql "$test_container":/docker-entrypoint-initdb.d/sql/jinlin24th_init.sql >/dev/null
mysql_test < deploy/mysql/init/01-schema.sql
[ "$(mysql_test -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='jinlin24th' AND table_name LIKE 'coffee\\_%'")" = 12 ]
[ "$(mysql_test -Nse 'SELECT COUNT(*) FROM jinlin24th.coffee_product')" = 0 ]

# demo 只在这个一次性测试库显式验证，重复执行不重复造菜单。
mysql_test jinlin24th < sql/coffee_demo_seed.sql
mysql_test jinlin24th < sql/coffee_demo_seed.sql
[ "$(mysql_test -Nse 'SELECT COUNT(*) FROM jinlin24th.coffee_product')" = 1 ]
[ "$(mysql_test -Nse 'SELECT COUNT(*) FROM jinlin24th.coffee_sku')" = 2 ]

mysql_port="$(docker port "$test_container" 3306/tcp | awk -F: '{print $NF}')"
./mvnw -q "-Dcoffee.test.mysql.url=jdbc:mysql://127.0.0.1:${mysql_port}/coffee_phase_a_test?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  '-Dtest=com.jinlin24th.jinlin.coffee.*Test' test
printf '%s\n' '咖啡 MySQL 8：存量迁移、重复执行、新安装、显式 demo 与数据库/接口测试全部通过。'

# 可选：对本轮打包产物启动真实 prod HTTP 服务，复用当前一次性库。
if [ -n "${COFFEE_HTTP_JAR:-}" ]; then
  [ -f "$COFFEE_HTTP_JAR" ]
  docker run --rm -d --name "${test_container}-redis" -p 127.0.0.1::6379 redis:7-alpine >/dev/null
  redis_port="$(docker port "${test_container}-redis" 6379/tcp | awk -F: '{print $NF}')"
  http_port="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
  env SPRING_PROFILES_ACTIVE=prod DB_USERNAME=root DB_PASSWORD=coffee-test-only \
    DB_URL="jdbc:mysql://127.0.0.1:${mysql_port}/jinlin24th?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
    REDIS_HOST=127.0.0.1 REDIS_PORT="$redis_port" REDIS_PASSWORD= REDIS_DATABASE=1 \
    JWT_SECRET=coffee-test-only-at-least-thirty-two-byte-secret CORS_ALLOWED_ORIGINS=http://127.0.0.1 \
    COFFEE_CATALOG_ENABLED=true COFFEE_ORDERING_ENABLED=false COFFEE_PAYMENT_ENABLED=false \
    WX_PAY_ENABLED=false WX_MINIAPP_APPID=test-only WX_MINIAPP_SECRET=test-only COFFEE_READY_TEMPLATE_ID= \
    java -jar "$COFFEE_HTTP_JAR" --server.address=127.0.0.1 --server.port="$http_port" \
      --app.mq.enabled=false --logging.file.name="$test_tmp/backend.log" > "$test_tmp/backend-stdout.log" 2>&1 &
  live_pid=$!
  for attempt in {1..90}; do
    if curl -fsS "http://127.0.0.1:${http_port}/user/coffee/store" >/dev/null 2>&1; then break; fi
    if ! kill -0 "$live_pid" 2>/dev/null || [ "$attempt" = 90 ]; then tail -60 "$test_tmp/backend-stdout.log"; exit 1; fi
    sleep 1
  done
  python3 scripts/verify-coffee-http.py "http://127.0.0.1:${http_port}" "$COFFEE_HTTP_JAR"
fi
