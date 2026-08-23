# AI 项目上下文

更新日期：2026-08-23

## 产品边界

校园二手交易平台首期只有三类使用者：游客、学生用户、管理员。学生用户在每笔订单中临时成为买家或卖家；不要创建永久“买家账号”或“卖家账号”。当前 Web 已可本地部署，Windows 与移动原生客户端尚未实现。

## 代码地图

```text
backend/src/main/java/com/campus/secondhand/
  security/     Session 当前身份、Security 配置、Client IP
  user/         邮箱注册登录、验证码、个人资料
  item/         商品发布、公开查询、卖家商品管理
  message/      商品公开留言及本人维护
  order/        预留、订单状态机、超时释放
  admin/        管理员用户/商品/留言操作
  common/       统一响应和异常映射
backend/src/main/resources/db/migration/  Flyway 全新数据库结构
frontend/       HTML 页面
frontend/assets/js/api.js                 Session/CSRF/请求唯一 seam
frontend/assets/css/styles.css            全站桌面与移动视觉系统
deploy/         MySQL + Spring Boot + Nginx Compose 部署
```

## 关键 module 与 interface

### Web 会话 module

- Seam：后端 `/auth/login|logout|csrf`、`/users/me` 与前端 `api.js` 的 `session`/`request`。
- Interface 不变量：Web 凭据仅在 HttpOnly Session Cookie；写请求带 CSRF；本地用户对象只用于渲染。
- Adapter：Spring Session JDBC；未来可替换为 Redis，页面调用方不变。

### 交易 module

- Seam：`TradingService.placeOrder/listOrders/perform`。
- Interface 不变量：商品锁顺序先于订单锁；买卖身份由 Session 和商品推导；合法动作由订单状态和参与方共同决定；过期释放与状态修改在同一事务提交。
- Controller、定时任务和测试都必须穿过这个 interface，不要直接改订单状态。

### 商品管理 module（第五轮）

- Seam：`SellerInventory`。
- Interface：列出自己的商品、修改允许修改的商品资料、执行卖家下架/重新上架动作。
- 不变量：只有发布者能操作；`RESERVED`/`SOLD` 不可编辑或卖家下架；卖家重新上架不能绕过管理员 `REMOVED`；修改状态使用商品行锁。
- 商品交易状态与内容审核状态相互独立：`ItemStatus` 表示交易可用性，`ItemModerationStatus` 表示管理员是否允许公开展示。

## 数据与状态

- 用户：`ACTIVE`/`DISABLED`，安全状态变化递增 `authVersion`，旧 Session 不会复活。
- 商品交易状态：`ON_SALE`、`RESERVED`、`SOLD`、`WITHDRAWN`。
- 商品审核状态：`VISIBLE`、`REMOVED`。
- 订单：`PENDING_SELLER_CONFIRMATION` → `WAITING_HANDOVER` → `COMPLETED`；也可进入 `CANCELLED`/`EXPIRED`。
- 订单保存下单时的标题、价格、双方昵称快照。

## 当前部署事实

- Docker 服务：`mysql`、`backend`、`web`，只向宿主机暴露 Web 80 端口。
- Nginx 同源代理 `/api/`；生产 TLS 需把 Session Cookie Secure 设为 true。
- Flyway 从空 MySQL 建库；本项目没有历史生产库升级负担。
- `database/seed.sql` 仅允许全新空业务库执行一次，禁止生产导入。

## 已知非阻断债务

- 商品图片仍使用外部 URL；后续应改为自有对象存储上传/图片代理并收紧 CSP。
- 首页关键词与分类暂未真正组合查询。
- Windows/移动原生客户端的短 access token + rotation refresh token 尚未实现。
- 当前是模块化单体；只有出现明确独立伸缩/部署需求后才拆微服务。
