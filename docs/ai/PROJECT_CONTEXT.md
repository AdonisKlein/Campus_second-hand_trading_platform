# AI 项目上下文

更新日期：2026-08-31

## 微服务迁移工作区

- 当前迁移分支为 `codex/microservices-refactor`；不可移动的单体基线标记 `monolith-start` 指向课程指定提交。
- `backend/` 只作为 `monolith-start` 的行为和性能对比参考保留，不再是 Gateway 运行路径。
- `services/api-gateway` 与 Account、Marketplace、Trading、Governance 已完成工作项 2—5；四个业务服务均已提取。
- Gateway 是浏览器唯一后端入口：Redis 保存 HttpOnly Session，Gateway 保留 CSRF、精确 CORS、登录/退出、账号安全状态复核、客户端身份头清洗并签发 60 秒内部 JWT。JWT 不进入浏览器或 localStorage。
- Account 独占自己的 `users` 与 `email_verification` 数据库结构；内部密码验证和安全状态查询要求共享内部服务 token，外部资料/管理员接口要求 Gateway JWT。
- 商品、图片、公开问答、搜索与商品管理路径由 Gateway 转发 Marketplace；订单和私聊路径转发 Trading；举报路径转发 Governance。Gateway 不再包含单体 URI 或兜底路由。
- Marketplace 独占 `items`、`item_tags`、`messages`、`searchable_user_projection`；Account/Trading 依赖位于 `AccountPublicPort`、`TradingInquiryPort`，生产 adapter 使用 300ms/800ms 超时，测试 adapter 可替换。搜索只读本地公开投影，不查询 Account 数据库。
- Trading 独占 `trade_orders`、`chat_conversations`、`chat_messages`、`chat_blocks` 及自己的 Inbox/Outbox。`TradingWorkflow` 负责购买意向与 Saga 状态机，`DirectChat` 负责会话、未读、屏蔽和消息；Account/Marketplace 通过 REST port 查询安全快照，商品预留、释放和售出通过 RabbitMQ 事件完成。
- Governance 独占 `content_reports`、`report_actions` 及自己的 Inbox/Outbox。`ContentGovernance` 区分管理员决定和动作交付状态；举报对象通过 Account/Marketplace 快照 port 读取，治理动作通过 RabbitMQ 交给数据所有者幂等执行。
- 工作项 6 已完成默认微服务运行拓扑：Compose/Kind 均包含 Gateway、四个业务服务、MySQL 四库四账号、Redis、RabbitMQ 和 Web。`scripts/dev/microservices.ps1` 会验证容器健康、Flyway、跨库拒绝和同源入口；`scripts/ci/kind-local.ps1` 负责本地 Kind 构建、部署与冒烟。
- `contracts/http/public-api-v1.tsv` 冻结当前公开 method + path；`contracts/events/*.v1.schema.json` 冻结 5 类 RabbitMQ 事件。事件必填字段不能在 v1 删除，消费者必须忽略新增元数据；`verify-event-contracts.mjs` 是对应门禁。
- `scripts/ci/verify-services.ps1` 是逐个验证五个工程独立构建的入口。任一工程失败立即返回非零。
- `.github/workflows/ci.yml` 已切换为微服务发布 seam：五服务独立 `mvn verify` → 契约/前端 → Compose Playwright → 统一 JSON/Markdown 测试报告 → 六个 `sha-xxxxxxx` GHCR 镜像 → main 分支 Kind 部署与冒烟。PR 只测试不发布，任一前置失败都会阻止镜像和部署。
- `scripts/ci/deploy-kind.sh` 是 CI 部署与诊断 seam：部署 Gateway、四服务和 Web，验证 readiness、liveness 与 `/actuator/info` 的不可变版本；无论成功失败都收集资源、Events、Pod describe、当前/上一容器日志和实际镜像。
- 所有 Java 服务使用 ECS JSON 控制台日志并公开 `APP_VERSION`/`GIT_COMMIT`。Gateway 负责建立 `X-Correlation-Id`，内部 REST 继续传递，交易和治理事件 envelope 保存同一关联标识。
- 完整实施顺序、数据库归属和通信规则见 `docs/roadmap/2026-08-microservices-migration.md`。

## 产品边界

校园二手交易平台首期只有三类使用者：游客、学生用户、管理员。学生用户在每笔订单中临时成为买家或卖家；不要创建永久“买家账号”或“卖家账号”。当前 Web 已可本地部署，Windows 与移动原生客户端尚未实现。

## 代码地图

```text
services/api-gateway/          Redis Session、CSRF、身份校验、内部 JWT 和公开路由
services/account-service/      注册登录、验证码、资料、账号安全与用户治理
services/marketplace-service/  商品、图片、搜索、公开问答和用户公开投影
services/trading-service/      购买意向、订单 Saga、交易工作台与私聊
services/governance-service/   举报决定、治理事件和追加式审计
backend/                       不再运行的单体行为/性能对比基线
frontend/                      HTML、CSS、JavaScript 与 Nginx 同源代理
deploy/                        Compose 四库、Redis、RabbitMQ 和服务编排
k8s/                           Kubernetes Base、CI Kind Overlay 与部署说明
scripts/dev/                   本地微服务启动与验收入口
scripts/ci/                    服务验证、测试报告和 Kind 本地部署入口
```

服务接口与数据表的正式对照基线见 `doc/服务接口清单与数据表归属方案.md`。该文档以当前 Controller、核心 interface 和 Flyway `V1`～`V6` 为准，明确模块 owner、跨模块读取方式和写入边界。

## 业务页面信息架构规则

- `doc/ui-style-preview.html` 是视觉方向与组件预览，不是实际页面模板。
- 实际 Web 页面必须直接进入搜索、数据、表单或管理任务；不放品牌口号横幅、欢迎介绍、功能宣传、发布技巧侧栏或整块交易流程教学。
- 可以保留页面标题、字段帮助、真实状态说明，以及会直接影响隐私、安全或操作结果的短提示；不要用大卡片重复解释平台功能。
- `frontend/tests/ui-design-contract.test.js` 会拒绝 `campaign-banner`、`auth-intro`、`editor-guide`、`order-page-intro`、`trade-process-card`、装饰性 kicker 等重新进入业务 HTML/JS。

## 关键 module 与 interface

### 验证邮件 module

- Seam：`EmailService.sendVerificationCode(to, code)`；验证码 module 只知道“发送验证码”，不理解阿里云主机、端口或 TLS 模式。
- Production adapter：Spring `JavaMailSender` 连接阿里云邮件推送；test adapter：Mockito `JavaMailSender`，测试同一个发送 interface 的发件人、失败和禁用行为。
- Local adapter：`deploy/docker-compose.mailpit.yml` 将同一个 `JavaMailSender` 指向 Compose 内部 `mailpit:1025`；只将收件箱 8025 绑定到宿主机 `127.0.0.1`，不得用于生产。
- `MAIL_ENABLED=false` 时应用可以启动，但验证码发送稳定返回 503；设为 true 时启动阶段要求合法 `MAIL_FROM` 以及非空 SMTP 用户名和密码，每封邮件都显式使用该地址。
- 阿里云推荐配置为 `smtpdm.aliyun.com:465` 隐式 TLS；受限网络可显式切换为端口 80 + STARTTLS。用户名、发件人必须使用控制台创建的发信地址，SMTP 密码只能保存在部署 Secret。
- SMTP 失败后当前验证码 challenge 会立即作废，客户端不会收到一个数据库仍可消费但实际从未送达的验证码。

### Web 会话 module

- Seam：后端 `/auth/login|logout|csrf`、`/users/me` 与前端 `api.js` 的 `session`/`request`。
- Interface 不变量：Web 凭据仅在 HttpOnly Session Cookie；写请求带 CSRF；本地用户对象只用于渲染。
- Adapter：Gateway Spring Session Redis；页面调用方不理解 Redis 或内部 JWT。
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
- 第十五轮页面采用桌面“会话列表 + 聊天房间 + 商品/交易上下文”三栏与移动两级布局；会话摘要返回真实商品价格与状态，前端不复制商品状态规则。会话支持搜索、未读优先、分页、sequence 历史游标、发送失败重试和连接状态反馈。
- 当前仍只实现文本私聊；结构化报价必须先建立独立消息类型、报价状态机和按报价形成购买意向的交易 interface，不得用自由文本或纯前端状态伪装报价。

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
- 举报：`OPEN` → `RESOLVED` 或 `DISMISSED`；处理历史只追加，不由学生修改或删除；私聊举报可保存经参与方校验的最近消息证据快照，管理员可查看，被举报方可在 `/reports/received` 查看治理结果和处理说明。
- 私聊：会话由商品、买家、卖家唯一确定；消息 sequence 只增不改，已读游标只前进不后退。
- 账号封禁：管理员治理说明写入用户状态原因并递增 `authVersion`；旧 Session 被拒绝时返回封禁提示，前端展示提示后清理身份状态。

## 当前部署事实

- Docker 服务包括 MySQL、Redis、RabbitMQ、Gateway、四个业务服务和 Web；只向宿主机暴露 Web，`media-data` 保存商品图片。
- 同一个 MySQL 服务器包含 `campus_account`、`campus_marketplace`、`campus_trading`、`campus_governance`；四个账号经 GRANT 限制不能跨库访问。
- Kubernetes Base 使用同一拓扑，每个 Java 服务都有独立 Deployment、ClusterIP Service 与三类探针；MySQL、Redis、RabbitMQ 和图片分别使用 PVC，CI Overlay 增加 Mailpit。
- 本地 Compose seam 是 `scripts/dev/microservices.ps1`；本地 Kind seam 是 `scripts/ci/kind-local.ps1`。后者通过 `kubectl port-forward` 做同源冒烟，避免 Windows NodePort 差异。
- 当前 `.github/workflows/ci.yml` 与 `scripts/ci/deploy-kind.sh` 已是微服务接口；只有 main 分支全绿的 GitHub Actions 运行记录，才能作为自动发布验收证据。
- Nginx 同源代理 `/api/` 到 Gateway；生产 TLS 需把 Session Cookie Secure 设为 true。
- Flyway 分别从四个空库建表；本项目没有历史生产库升级负担。旧 `database/seed.sql` 不兼容微服务四库，不能导入。

## 云原生实验公共 interface

- 统一入口是 `experiments/run.ps1 -Experiment smoke|hpa|fault|performance`。入口负责生成 runId、采集环境、调用实验 adapter、收集 Kubernetes 诊断、计算证据 SHA-256 并写入 `result.json`；HPA、故障隔离和性能实验不得各自复制这套流程。
- B/C/D 只需在各自目录提供参数一致的 `run.ps1`：`RunDirectory`、`Context`、`Namespace`、`BaseUrl`。实验失败必须抛出错误，由公共入口保留证据并返回非零退出码。
- Metrics Server 固定为仓库内校验过 SHA-256 的 v0.9.0 清单；安装器等待 `metrics.k8s.io` 返回真实数据，不使用固定 sleep。资源采样统一写 `resource-samples.csv`。
- k6 通过固定 digest 的官方 GHCR 镜像运行，宿主机无需安装 k6；控制台、机器可读 summary 和镜像 digest 都进入证据目录。
- `experiments/common/dataset` 使用固定 seed 生成 2,500 用户、20,000 商品和 50,000 公开留言的逻辑基准数据。D 负责在不改变逻辑 ID/内容的前提下将它适配为两个版本的导入 SQL。
- 完整运行产物位于被 Git 忽略的 `artifacts/cloud-native/<runId>/`，不提交本地环境或大体积原始结果。提交小型摘要前必须先执行 `verify-evidence.mjs`，且任何证据都不得包含 Secret、SMTP 密码或 `.env`。

## 已知非阻断债务

- 未被商品引用的上传图片暂未自动回收；后续可增加临时上传记录与定时清理。
- 文件系统 adapter 适合当前单机部署；多实例部署前应替换为 MinIO/S3 adapter。
- Windows/移动原生客户端的短 access token + rotation refresh token 尚未实现。
- 微服务工作项 1—8 的代码已完成；`microservices-end` 只能在 main 分支 CI 的 Kind 部署与冒烟全绿后创建。下一阶段是暂缓的 HPA、依赖故障隔离和单体/微服务性能对比实验。
