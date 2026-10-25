-- 新安装统一从主初始化脚本加载（已包含咖啡阶段 A 五张表）；存量升级使用独立 migration。
source /docker-entrypoint-initdb.d/sql/jinlin24th_init.sql;
