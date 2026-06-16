# 物流接口

## 创建发货单 POST /api/logistics
必填参数：orderId（订单ID）、carrier（快递公司：SF/YTO/ZTO/EMS）、trackingNumber（运单号）
发货后订单状态自动由 PAID → SHIPPING
支持批量发货：传入 orderId 列表，最多 50 单/次

## 物流轨迹查询 GET /api/logistics/{orderId}/trace
实时查询快递最新轨迹，返回完整轨迹节点列表
每个节点包含：timestamp、location、status（IN_TRANSIT/OUT_FOR_DELIVERY/DELIVERED/EXCEPTION）
缓存策略：轨迹数据缓存 5 分钟，避免频繁调用快递公司接口

## 签收确认 POST /api/logistics/{orderId}/confirm
用户端确认收货操作
签收后订单状态由 SHIPPING → DELIVERED
超过 15 天未主动签收，系统自动签收
