# 开发环境搭建指南

## 前置依赖

| 软件 | 最低版本 | 用途 |
|------|---------|------|
| JDK | 21 | Java 运行时 |
| Maven | 3.9+ | 项目构建 |
| Docker | 24+ | 运行中间件 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7 | 缓存 |
| Elasticsearch | 8 | 搜索 |

## 本地启动步骤

```bash
# 1. 启动中间件
docker compose -f docker-compose.dev.yml up -d

# 2. 初始化数据库
mysql -u root -p < scripts/init.sql

# 3. 启动服务
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl order-service
# ... 其他服务同理
```

## 默认端口分配
- 8081: user-service
- 8082: order-service
- 8083: product-service
- 8084: payment-service
- 8085: logistics-service
- 8086: coupon-service
- 9000: Gateway

## 常见启动问题

**端口被占用**
```bash
lsof -ti:8081 | xargs kill -9
```

**MySQL 连接拒绝**
确认 Docker 容器在运行：`docker ps | grep mysql`
确认 application.yml 中 spring.datasource.url 指向 localhost:3306

**Nacos 连接失败**
开发环境 Nacos 使用 standalone 模式，确认 `-m standalone` 参数
