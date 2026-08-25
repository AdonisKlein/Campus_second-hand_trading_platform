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
  search/       多关键词商品/用户搜索、筛选、排序与安全公开投影
  report/       学生举报、管理员治理决策与追加式处理审计
  media/        商品图片验证、标准化、持久存储与公开读取
  message/      商品公开留言及本人维护
  chat/         仅买卖双方可见的商品私聊、未读游标与屏蔽
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

## 业务页面信息架构规则

- `doc/ui-style-preview.html` 是视觉方向与组件预览，不是实际页面模板。
- 实际 Web 页面必须直接进入搜索、数据、表单或管理任务；不放品牌口号横幅、欢迎介绍、功能宣传、发布技巧侧栏或整块交易流程教学。
- 可以保留页面标题、字段帮助、真实状态说明，以及会直接影响隐私、安全或操作结果的短提示；不要用大卡片重复解释平台功能。
- `frontend/tests/ui-design-contract.test.js` 会拒绝 `campaign-banner`、`auth-intro`、`editor-guide`、`order-page-intro`、`trade-process-card`、装饰性 kicker 等重新进入业务 HTML/JS。

## 关键 module 与 interface

### 验证邮件 module

- Seam：`EmailService.sendVerificationCode(to, code)`；验证码 module 只知道“发送验证码”，不理解阿里云主机、端口或 TLS 模式。
- Production adapter：Spring `JavaMailSender` 连接阿里云邮件推送；test adapter：Mockito `JavaMailSender`，测试同一个发送 interface 的发件人、失败和禁用行为。
- `MAIL_ENABLED=false` 时应用可以启动，但验证码发送稳定返回 503；设为 true 时启动阶段要求合法 `MAIL_FROM` 以及非空 SMTP 用户名和密码，每封邮件都显式使用该地址。
- 阿里云推荐配置为 `smtpdm.aliyun.com:465` 隐式 TLS；受限网络可显式切换为端口 80 + STARTTLS。用户名、发件人必须使用控制台创建的发信地址，SMTP 密码只能保存在部署 Secret。
- SMTP 失败后当前验证码 challenge 会立即作废，客户端不会收到一个数据库仍可消费但实际从未送达的验证码。

### Web 会话 module

- Seam：后端 `/auth/login|logout|csrf`、`/users/me` 与前端 `api.js` 的 `session`/`request`。
- Interface 不变量：Web 凭据仅在 HttpOnly Session Cookie；写请求带 CSRF；本地用户对象只用于渲染。
- Adapter：Spring Session JDBC；未来可替换为 Redis，页面调用方不变。
- 页面角色导航由 `api.js` 统一 hydration；所有 `data-admin-only` 入口必须在 HTML 默认 `hidden`，仅当前 Session 的 `/users/me` 返回 ADMIN 后显示。这个规则只改善体验，不替代后端授权。
- 会话读取带 generation；登录、退出或 401 后，旧请求不得覆盖新会话的页面状态。

### 校园搜索 module（第八轮）

- Seam：`CampusSearch.search(SearchQuery, viewerRegion)`，HTTP 入口为公开的 `GET /search`。
- Interface：最多 8 个空格/逗号分隔关键词；关键词之间取交集，单个关键词可匹配商品标题、描述或标签。搜索范围明确分为 `ITEMS` 与 `USERS`。
- 商品只返回 `ON_SALE + VISIBLE` 且卖家仍为 `ACTIVE` 的记录，可按价格、校园区域、商品标签和卖家筛选，并支持相关度、最新、最近活跃、同区域优先、信用、价格排序。
- 用户搜索只匹配用户名/昵称，只返回 id、用户名、昵称、校园区域、信用分和最近活跃时间；不得暴露邮箱、手机号或其他登录资料。
- “离我最近”当前只表示登录用户与商品/用户处于同一校园区域时优先；尚未使用 GPS 或精确位置。
- 首页不使用商品分类侧栏。搜索前展示最新商品；提交搜索后才展开商品/用户切换、排序、价格、区域和标签筛选。

### 受保护操作导航 module（第八轮）

- Seam：前端 `api.js` 的 `requireAuthenticatedUser()`、`confirmAuthentication()` 和 `data-requires-auth`。
- Interface：游客点击发布、订单、留言、下单等操作时留在当前页面显示确认框；用户确认后才前往登录，登录成功回到原目标。
- `postLoginTarget` 只能接受站内相对目标；前端提示仅改善体验，后端 Session、CSRF 和资源鉴权仍是安全边界。

### 内容治理 module（第九轮）

- Seam：`ContentGovernance.submit/listMine/listForAdmin/decide`；学生入口 `/reports`，管理员入口 `/admin/reports`。
- Interface 不变量：举报人和管理员都只从当前 Session 推导；学生只能举报商品、留言或学生用户，不能举报自己；同一学生对同一对象只形成一条举报。
- 举报保存对象简要快照，即使留言之后被移除，处理记录仍可审计；普通学生只能查看自己的举报，管理员队列才包含举报人信息。
- 举报状态只允许 `OPEN → RESOLVED | DISMISSED`。确认成立时治理措施必须与对象匹配：商品下架、留言移除、用户禁用；驳回不得改变对象。
- 每次最终处理追加一条 `report_actions` 审计，记录真实管理员、结果、措施、说明和时间；同一举报以行锁保证只能最终处理一次。
- 学生每 24 小时最多提交 20 条举报；数据库唯一约束继续防止并发重复提交。

### 私聊 module（第十轮）

- Seam：`DirectChat.open/conversations/history/send/markRead/block/unblock`；HTTP 入口统一在 `/chat`，页面入口为商品详情与 `messages.html`。
- 一个在售商品、一个买家和该商品卖家最多形成一个会话；对外只暴露随机 UUID 会话号，内部数值主键不用于客户端定位。
- 会话和消息只能由真实买卖双方读取；发送者完全从 Session 推导，管理员没有查看学生私聊正文的特殊权限。
- 每个会话使用单调递增 sequence；买卖双方各自保存最后已读 sequence，由此计算未读数。消息历史按 sequence 游标向前分页，不使用不稳定的 offset。
- 商品详情发起的新会话只允许 `ON_SALE + VISIBLE`；订单参与者可通过 `openTrade` 为对应交易创建或复用会话，因此商品进入预留后仍能沟通。商品售出或下架后保留既有历史。任一方屏蔽后双方均不能继续发送，但仍可查看已有记录和解除自己的屏蔽。
- 当前 Web adapter 每 8 秒轮询，interface 不依赖轮询方式；未来换 SSE/WebSocket 时无需改变领域规则和数据库消息顺序。
- 当前只实现文本私聊；第十五轮将整体重设计私聊页面并加入结构化报价，现阶段不用自由文本伪装为报价状态。

### 交易 module

- 写入 seam：`TradingService.requestPurchase/perform`；订单工作台读取 seam：`TradeDesk.browse(actorId, perspective, stage)`，当前由同一个 `TradingService` 实现以复用有效状态与 `allowedActions` 规则。
- Interface 不变量：买家提交的是非独占购买意向，商品保持 `ON_SALE`；卖家接受其中一条后才把商品改为 `RESERVED`，并关闭其他待回应意向。
- 商品锁顺序先于订单锁；买卖身份由 Session 和商品推导；合法动作由订单状态和参与方共同决定；意向过期不修改商品，待交接预留取消或过期才恢复在售。
- Controller、定时任务和测试都必须穿过这个 interface，不要直接改订单状态。
- `TradeDesk` 按“我买到的 / 我卖出的”和交易阶段返回统计、商品分组、安全的对方公开资料、有效状态、剩余时间、时间线和关闭原因。邮箱、手机号等账号资料不得进入订单投影。

### 商品详情 module（第十二轮）

- Seam：`ProductDetail.show(itemId, viewerId)`；公开 HTTP 入口仍为 `GET /items/{id}`。
- Interface 一次返回商品公开字段、安全卖家摘要、当前浏览者状态、有效购买意向、可执行动作和同卖家其他在售商品；Web、移动端和未来 Windows adapter 不应分别拼装这些规则。
- 卖家摘要只包含公开昵称、校园区域、信用分、活跃时间和在售数量，绝不返回邮箱、手机号或账号安全字段。
- 游客可看到需要登录后执行的私聊/购买意向入口；买家已有有效意向时只返回查看进度，不再返回重复提交；卖家本人只返回管理商品。
- 普通访客不能查看卖家下架、管理员下架或禁用卖家的商品；发布者本人可以通过有效 Session 查看自己被下架商品的状态并进入管理。

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
- 订单：`PURCHASE_REQUESTED` → `WAITING_HANDOVER` → `COMPLETED`；也可进入 `DECLINED`、`CANCELLED` 或 `EXPIRED`。
- 订单保存下单时的标题、价格、双方昵称快照。
- 举报：`OPEN` → `RESOLVED` 或 `DISMISSED`；处理历史只追加，不由学生修改或删除。
- 私聊：会话由商品、买家、卖家唯一确定；消息 sequence 只增不改，已读游标只前进不后退。

## 当前部署事实

- Docker 服务：`mysql`、`backend`、`web`，只向宿主机暴露 Web 80 端口；`media-data` 保存商品图片。
- Nginx 同源代理 `/api/`；生产 TLS 需把 Session Cookie Secure 设为 true。
- Flyway 从空 MySQL 建库；本项目没有历史生产库升级负担。
- 当前本地验收数据库是 Docker Desktop 中的 `mysql:8.4`，通过 Compose 私有网络连接并保存在 Docker volume；不是远程数据库，也不是宿主机单独安装的 MySQL 服务。
- `database/seed.sql` 仅允许全新空业务库执行一次，禁止生产导入。

## 已知非阻断债务

- 未被商品引用的上传图片暂未自动回收；后续可增加临时上传记录与定时清理。
- 文件系统 adapter 适合当前单机部署；多实例部署前应替换为 MinIO/S3 adapter。
- Windows/移动原生客户端的短 access token + rotation refresh token 尚未实现。
- 当前是模块化单体；只有出现明确独立伸缩/部署需求后才拆微服务。
