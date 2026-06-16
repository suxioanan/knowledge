# 电商平台产品概述

## 系统架构
基于 Spring Cloud 微服务架构，主要模块：
- 用户服务（user-service）：注册、登录、用户管理、权限
- 商品服务（product-service）：商品 CRUD、库存管理、分类
- 订单服务（order-service）：订单创建、状态流转、查询
- 支付服务（payment-service）：支付发起、回调处理、退款
- 物流服务（logistics-service）：发货、轨迹查询、签收
- 营销服务（coupon-service）：优惠券创建、发放、校验

## 技术栈
- 后端：Spring Boot 3.5 + Spring Cloud + MyBatis-Plus
- 数据库：MySQL 8.0（读写分离） + Redis 7 + Elasticsearch 8
- 消息队列：RocketMQ（订单状态变更、库存扣减、物流更新）
- 服务发现：Nacos
- 配置中心：Nacos Config
- 网关：Spring Cloud Gateway

## 部署环境
- 开发环境（dev）：单机部署，Mock 第三方接口
- 测试环境（test）：3 节点 K8s 集群，联调真实第三方沙箱
- 预发环境（staging）：生产镜像 + 独立数据库，用于发布前验证
- 生产环境（prod）：双机房多活，同城容灾，RPO < 1 分钟

## 监控体系
- 应用监控：Spring Actuator + Prometheus + Grafana
- 日志采集：Filebeat → Kafka → Logstash → Elasticsearch → Kibana
- 链路追踪：SkyWalking
- 告警通知：Prometheus AlertManager → 飞书/钉钉/邮件
