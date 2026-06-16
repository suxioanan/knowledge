# 用户管理 API

## 创建用户 POST /api/users
必填参数：username（用户名）、password（密码）、email（邮箱）
可选参数：phone（手机号）、department（部门）、role（角色，默认 USER）
返回值：userId、username、createdAt
权限要求：ADMIN 角色
密码规则：长度 8-32 位，必须包含大小写字母和数字

## 删除用户 DELETE /api/users/{id}
需要管理员权限，删除后不可恢复
删除前会校验该用户是否有关联订单，如有则拒绝删除并返回关联订单列表

## 修改用户 PUT /api/users/{id}
可修改字段：email、phone、department、role
不可修改字段：username（创建后不可改）、password（走单独改密接口）

## 修改密码 POST /api/users/{id}/password
必填参数：oldPassword（旧密码）、newPassword（新密码）
新密码不能与旧密码相同，不能包含用户名
连续输错 5 次旧密码，账号锁定 30 分钟

## 查询用户 GET /api/users
支持分页：page（页码，从 1 开始）、size（每页条数，默认 20，最大 100）
支持筛选：department、role、keyword（模糊匹配 username 或 email）
返回值：分页列表 + total 总数
