# 订单管理 API

## 创建订单 POST /api/orders
必填参数：userId（用户ID）、items（商品列表）、address（收货地址）
可选参数：couponCode（优惠券码）、remark（备注）、invoiceInfo（发票信息）
返回值：orderId、状态（PENDING）、预计送达时间、totalAmount
校验规则：
- items 不能为空，每个 item 必须包含 productId、quantity（正整数）、price
- address 必须包含 province、city、detail 三个字段
- couponCode 如果提供，会实时校验是否有效且未过期

## 取消订单 PUT /api/orders/{id}/cancel
只有 PENDING 状态的订单可以取消
取消后状态变为 CANCELLED，库存自动释放
不可恢复

## 查询订单 GET /api/orders/{id}
返回完整订单信息，包含物流信息（如果已发货）
普通用户只能查自己的订单，ADMIN 可以查所有

## 订单列表 GET /api/orders
支持筛选：userId、status（PENDING/PAID/SHIPPING/DELIVERED/CANCELLED）、createTime 范围
默认按创建时间倒序
支持分页
