# AI 项目上下文

更新日期：2026-08-24

## 产品边界

校园二手交易平台首期只有三类使用者：游客、学生用户、管理员。学生用户在每笔订单中临时成为买家或卖家；不要创建永久“买家账号”或“卖家账号”。当前 Web 已可本地部署，Windows 与移动原生客户端尚未实现。

## 代码地图

```text
backend/src/main/java/com/campus/secondhand/
  security/     Session 当前身份、Security 配置、Client IP
  user/         邮箱注册登录、验证码、个人资料
  item/         商品发布、公开查询、卖家商品管理
  media/        商品图片验证、标准化、持久存储与公开读取
  message/      商品公开留言及本人维护
  order/        预留、订单状态机、超时释放
  admin/        管理员用户/商品/留言操作
  common/       统一响应和异常映射
backend/src/main/resources/db/migration/  Flyway 全新数据库结构
frontend/       HTML 页面
frontend/assets/js/api.js                 Session/CSRF/请求唯一 seam
frontend/assets/css/styles.css            全站桌面与移动视觉系统
frontend/tests/                           无依赖的 UI 结构与会话竞态回归测试
deploy/         MySQL + Spring Boot + Nginx Compose 部署
```

## 关键 module 与 interface

### Web 会话 module

- Seam：后端 `/auth/login|logout|csrf`、`/users/me` 与前端 `api.js` 的 `session`/`request`。
- Interface 不变量：Web 凭据仅在 HttpOnly Session Cookie；写请求带 CSRF；本地用户对象只用于渲染。
- Adapter：Spring Session JDBC；未来可替换为 Redis，页面调用方不变。
- 页面角色导航由 `api.js` 统一 hydration；所有 `data-admin-only` 入口必须在 HTML 默认 `hidden`，仅当前 Session 的 `/users/me` 返回 ADMIN 后显示。这个规则只改善体验，不替代后端授权。
- 会话读取带 generation；登录、退出或 401 后，旧请求不得覆盖新会话的页面状态。

### 公开商品目录 module（第七轮）

- Seam：`ItemRepository.searchPublic(category, keyword, status, moderationStatus)`。
- Interface：分类和关键词都是可选条件；两者同时存在时返回交集，只公开 `ON_SALE + VISIBLE` 商品并按发布时间倒序。
- 首页桌面结构以 `market-shell` 为根，包含分类侧栏、活动横幅、快捷分类和商品网格；移动端隐藏侧栏，但查询语义保持一致。

### 交易 module

- Seam：`TradingService.placeOrder/listOrders/perform`。
- Interface 不变量：商品锁顺序先于订单锁；买卖身份由 Session 和商品推导；合法动作由订单状态和参与方共同决定；过期释放与状态修改在同一事务提交。
- Controller、定时任务和测试都必须穿过这个 interface，不要直接改订单状态。

### 商品管理 module（第五轮）

- Seam：`SellerInventory`。
- Interface：列出自己的商品、修改允许修改的商品资料、执行卖家下架/重新上架动作。
- 不变量：只有发布者能操作；`RESERVED`/`SOLD` 不可编辑或卖家下架；卖家重新上架不能绕过管理员 `REMOVED`；修改状态使用商品行锁。
- 商品交易状态与内容审核状态相互独立：`ItemStatus` 表示交易可用性，`ItemModerationStatus` 表示管理员是否允许公开展示。

### 商品图片 module（第六轮）

- Seam：`ProductImages.store/load`。
- Interface：学生上传图片后得到平台内部图片路径；公开读取只接受系统生成的 ownerId + UUID 文件名。
- 不变量：只接受真实内容为 JPG/PNG 的文件；输入和标准化输出均不超过 5MB；最多 1200 万像素、单边不超过 8000px；重新编码会清除 EXIF；每个学生本地配额 100MB。
- 单实例内同一 owner 的配额核算串行化，图片解码全局最多并发 2 个；多实例阶段由对象存储 adapter 承担原子配额和处理队列。
- 当前 adapter：Docker `media-data` 持久卷中的文件系统。未来替换为 MinIO/S3 adapter 时，商品只继续保存同一种受控路径，不接触存储细节。
- 商品写入还会验证图片路径属于当前卖家，页面不会加载任意外部图片 URL。

## 数据与状态

- 用户：`ACTIVE`/`DISABLED`，安全状态变化递增 `authVersion`，旧 Session 不会复活。
- 商品交易状态：`ON_SALE`、`RESERVED`、`SOLD`、`WITHDRAWN`。
- 商品审核状态：`VISIBLE`、`REMOVED`。
- 订单：`PENDING_SELLER_CONFIRMATION` → `WAITING_HANDOVER` → `COMPLETED`；也可进入 `CANCELLED`/`EXPIRED`。
- 订单保存下单时的标题、价格、双方昵称快照。

## 当前部署事实

- Docker 服务：`mysql`、`backend`、`web`，只向宿主机暴露 Web 80 端口；`media-data` 保存商品图片。
- Nginx 同源代理 `/api/`；生产 TLS 需把 Session Cookie Secure 设为 true。
- Flyway 从空 MySQL 建库；本项目没有历史生产库升级负担。
- `database/seed.sql` 仅允许全新空业务库执行一次，禁止生产导入。

## 已知非阻断债务

- 未被商品引用的上传图片暂未自动回收；后续可增加临时上传记录与定时清理。
- 文件系统 adapter 适合当前单机部署；多实例部署前应替换为 MinIO/S3 adapter。
- Windows/移动原生客户端的短 access token + rotation refresh token 尚未实现。
- 当前是模块化单体；只有出现明确独立伸缩/部署需求后才拆微服务。
