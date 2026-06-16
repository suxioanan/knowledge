# 数据库设计规范

## 命名规范
- 表名：小写 + 下划线，复数形式（users、orders、order_items）
- 字段名：小写 + 下划线（created_at、updated_at、is_deleted）
- 主键：统一使用 id，类型 BIGINT UNSIGNED 自增
- 索引命名：uk_字段名（唯一索引）、idx_字段名（普通索引）、fk_字段名（外键）

## 必备字段
每个表必须包含以下三个字段：
- id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
- created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
- updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

## 分表策略
订单表按用户 ID 哈希分 16 表（order_00 ~ order_15）
分表键：userId % 16
跨分表查询走 ES 索引，不直接联表

## 连接池配置
使用 HikariCP，默认配置：
- maximumPoolSize: 20
- minimumIdle: 10
- connectionTimeout: 30000ms
- idleTimeout: 600000ms
- maxLifetime: 1800000ms

## 慢查询阈值
超过 500ms 的查询记录慢日志
超过 2000ms 的查询触发告警
