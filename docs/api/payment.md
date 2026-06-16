# 支付接口

## 发起支付 POST /api/payments
必填参数：orderId（订单ID）、payMethod（支付方式：ALIPAY/WECHAT/CREDIT_CARD）
返回值：paymentId、payUrl（收银台地址）、expireTime（15分钟后过期）
校验：订单状态必须为 PENDING，订单总金额 > 0

## 支付回调 POST /api/payments/callback
第三方支付平台的异步通知接口
验签流程：RSA256 签名校验 → IP 白名单校验 → 金额一致性校验 → 更新订单状态为 PAID
幂等保障：同一 paymentId 重复回调不重复处理，返回已处理成功

## 退款 POST /api/payments/{paymentId}/refund
必填参数：amount（退款金额，支持部分退款）、reason（退款原因）
退款金额不能超过已付金额，退款状态变化：REFUNDING → REFUNDED（成功）/ REFUND_FAILED（失败）
原路返回：ALIPAY 退支付宝、WECHAT 退微信
全款和部分退款均支持
