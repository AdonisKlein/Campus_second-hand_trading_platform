# 校园二手平台微服务改造路线图

更新日期：2026-08-31
实施分支：`codex/microservices-refactor`  
单体基线：`monolith-start` → `9be9e6502af2642235049698b1f2a8f55da9611b`

> 本文是实施路线图，不是完成报告。只有状态标记为“已完成”并附有测试和提交号的工作项，才代表代码已经落地。

## 1. 当前状态

- Account、Marketplace、Trading 与 Governance 四个业务服务均已提取，Gateway 已删除单体兜底路由。
- 微服务版已经具备独立 Maven 验证、API/MySQL/Redis/RabbitMQ 集成测试、8 个用例追溯、Playwright E2E、版本化事件契约、统一测试报告、版本化镜像、Kind 部署、健康检查和失败证据收集。
- 业务微服务代码完成度为 `4/4`；工作项 1—8 的代码已落地。HPA、故障实验和性能对比尚未开始。
- `monolith-start` 是不可移动的改造前版本标记；微服务工作项 1—8 全绿后创建 `microservices-end`。

## 2. 最终服务划分

| 业务服务 | 负责什么 | 独占的业务表 |
| --- | --- | --- |
| Account Service | 注册、验证码、登录验证、密码、个人资料、账号状态与角色 | `users`、`email_verification` |
| Marketplace Service | 商品、图片、标签、搜索、商品详情、公开留言、卖家商品管理 | `items`、`item_tags`、`messages`、`searchable_user_projection` |
| Trading Service | 购买意向、订单状态机、交易工作台、私聊、未读和屏蔽 | `trade_orders`、`chat_conversations`、`chat_messages`、`chat_blocks` |
| Governance Service | 举报、治理决定和追加式审计 | `content_reports`、`report_actions` |

API Gateway、前端、MySQL、Redis 和 RabbitMQ 不计入四个业务服务。每个业务服务还可以拥有自己的 `outbox_events`、`inbox_events` 和 `flyway_schema_history`。

### 数据规则

- 一个 MySQL 服务器创建四个独立数据库和四个最小权限账号。
- 不保留跨服务外键，不跨数据库联表，不读取其他服务的 Repository。
- 跨服务 ID 仅作为普通字段和快照保存。
- Marketplace 通过账号事件维护用户公开搜索投影。
- Trading 保存商品和参与者快照；Governance 保存举报对象快照。

## 3. 认证、路由与通信

### Web 身份

- 浏览器继续使用 HttpOnly Session Cookie 和 CSRF，不把 JWT 放进 localStorage。
- Session 由 Gateway 存入 Redis。
- Gateway 删除客户端伪造的身份头，向 Account 检查账号状态和 `authVersion`，再向内部服务发送短时 JWT。
- 内部服务只接受 Gateway 签发的身份；真实业务权限仍由各服务结合资源归属检查。

### 公开路由

| 路径 | 目标 |
| --- | --- |
| `/api/auth/**`、`/api/users/**`、`/api/admin/users/**` | Account |
| `/api/items/**`、`/api/media/**`、`/api/messages/**`、`/api/search`、`/api/admin/items/**`、`/api/admin/messages/**` | Marketplace |
| `/api/orders/**`、`/api/chat/**` | Trading |
| `/api/reports/**`、`/api/admin/reports/**` | Governance |

前端路径保持兼容。迁移期间 Gateway 将尚未提取的路径转发到单体；最后删除全部单体兜底路由。

### 跨服务通信

- 查询使用 REST：连接超时 300ms、响应超时 800ms，仅幂等 GET 允许重试一次。
- 依赖不可用时返回固定 `503 SERVICE_UNAVAILABLE` 和 `Retry-After`，不能产生半条业务记录。
- 跨数据库状态变更使用 RabbitMQ + Transactional Outbox + Inbox 幂等消费。
- 消费失败按退避策略重试，最终进入死信队列；需要时发送补偿事件。
- 事件 envelope 固定包含 `eventId`、类型、版本、产生时间、生产服务、correlationId 和 payload。

首批事件包括：

- `UserPublicProfileChanged`、`UserSecurityStateChanged`
- `ItemReservationRequested/Reserved/Rejected`
- `ItemReleaseRequested/Released`
- `ItemSoldRequested/Sold`
- `GovernanceActionRequested/Applied/Failed`

## 4. 实施工作项

### 工作项 1：冻结单体行为并建立迁移基线 — 已完成

- 升级受支持的 Spring Boot 技术基线并跑通单体回归。
- 建立 Gateway 与四个业务服务的独立 Maven 工程骨架。
- 冻结当前公开 HTTP interface 清单和契约回归测试。
- 验收：单体回归全绿；五个新工程均可单独 `mvn verify`。
- 验收结果：Spring Boot `4.0.8` 单体单元测试 29/29、API 与真实 MySQL 集成测试 50/50 通过；五个独立工程各 1/1 通过。
- 提交：`refactor: establish microservice migration baseline`（使用 `git log -1` 查看提交号）。

### 工作项 2：Gateway 与 Account Service — 已完成

- Gateway 接管 Redis Session、CSRF、登录入口、身份清洗和内部 JWT。
- Account 独占用户及验证码数据；其他模块不再读取 `UserRepository`。
- 验收注册、登录、改密、会话恢复、账号禁用与身份伪造反向测试。
- 迁移期尚未提取的业务由 Gateway 转发给单体；单体只接受 Gateway 签发的短时 JWT 或原有直连 Session，因此现有页面契约保持兼容。
- 验收结果：Gateway 6/6、Account 8/8、兼容单体单元 30/30 与 API/真实 MySQL 50/50、五个独立工程统一验证及前端契约 3/3 全绿。
- 提交：`refactor: extract account service and gateway authentication`（使用 `git log -1` 查看提交号）。

### 工作项 3：Marketplace Service — 已完成

- 提取商品、图片、标签、搜索、详情、公开留言和卖家商品管理。
- 通过事件维护用户公开搜索投影，不读取 Account 数据库。
- 验收 UC05—UC09 和数据库隔离。
- 实现结果：Marketplace 独占 `items`、`item_tags`、`messages` 与 `searchable_user_projection`；商品详情通过 Account/Trading port 查询，不出现跨服务 Repository。用户公开投影消费版本化 `UserPublicProfileChanged`，旧事件不会覆盖新资料；工作项 4 已加入交易 Saga 的 Inbox/Outbox 与 RabbitMQ adapter，RabbitMQ 容器和完整部署拓扑留在工作项 6 接线。
- Gateway 已将 Marketplace 所有公开路径从单体兜底切到 `8082`，浏览器仍使用原 `/api` 路径；学生身份只取 Gateway JWT subject，管理员角色由 JWT authority 与资源规则共同校验。
- 验收结果：Marketplace 14/14、Account 9/9、Gateway 6/6 通过；Flyway 从空 H2 建立 Marketplace 独立结构并由 Hibernate validate；静态扫描确认 Marketplace 不引用 `UserRepository`、`TradeOrderRepository` 或其他服务数据库表。
- 提交：`refactor: extract marketplace service`（使用 `git log -1` 查看提交号）。

### 工作项 4：Trading Service — 已完成

- 提取购买意向、订单、交易工作台、私聊、未读与屏蔽。
- 通过 RabbitMQ Saga 完成预留、释放和售出，不使用跨库事务。
- 验收 UC10—UC14、并发预留、重复事件和依赖故障。
- 实现结果：购买意向不锁商品；卖家接受后由 Trading Outbox 发起预留，Marketplace 以商品行锁裁决并通过 Inbox/Outbox 返回结果。取消、完成和超时分别使用释放/售出事件，消息重复消费不会重复改变状态。
- 私聊以 `DirectChat` 集中参与者授权、稳定 sequence 分页、买卖双方独立已读游标、未读统计、双向屏蔽和发送限流；会话只保存用户、商品和订单快照，不读取其他服务 Repository。
- Gateway 已将 `/api/orders/**` 与 `/api/chat/**` 从单体兜底切到 Trading `8083`。Trading 只拥有订单、会话、私聊、屏蔽和自己的 Inbox/Outbox 表；Marketplace V2 只增加商品预留关联字段及自己的 Inbox/Outbox。
- 验收结果：Trading 16/16、Marketplace 17/17 通过；依赖失败不留下半条订单、Saga Pending 不会被普通过期覆盖、陌生人越权、重复事件、第二订单抢占预留、未读与屏蔽均有断言。RabbitMQ 容器及完整部署接线仍属于工作项 6。
- 提交：`refactor: extract trading and direct-chat service`（使用 `git log -1` 查看提交号）。

### 工作项 5：Governance Service 与退役单体 — 已完成

- 提取举报和治理审计；治理动作由数据所有者幂等执行。
- 删除 Gateway 的单体兜底路由，旧 `backend` 不再参与构建部署。
- 验收 UC15—UC17、四服务独立构建以及零跨服务 Repository。
- 实现结果：Governance 独占 `content_reports`、`report_actions` 和自己的 Inbox/Outbox；Account 与 Marketplace 通过内部快照 port 验证举报对象，不存在跨库查询。`ContentGovernance` 集中举报限流、防重复、管理员决策、失败重试和追加审计。
- 管理员决定与远端执行状态分离：决定可为 `RESOLVED`，动作状态单独经历 `PENDING/APPLIED/FAILED`。治理命令由 Outbox 发布，Account 负责禁用用户，Marketplace 负责下架商品或公开留言；消费者用 Inbox 幂等，旧回执不能覆盖新重试。
- Gateway 已将 `/api/reports/**` 与 `/api/admin/reports/**` 直达 Governance `8084`，并完全移除单体 URI 与兜底路由。旧 `backend/` 仅作为 `monolith-start` 行为基线保留，不再是微服务运行路径；Compose/Kind 的物理切换属于工作项 6。
- 验收结果：Gateway 6/6、Account 13/13、Marketplace 19/19、Trading 16/16、Governance 12/12，共 66/66 通过；前端契约 3/3 与全部 JS 语法通过。
- 提交：`refactor: extract governance service and retire monolith`（使用 `git log -1` 查看提交号）。

### 工作项 6：独立数据库与部署环境 — 已完成

- Compose/Kind 加入 Gateway、Redis、RabbitMQ 和四个业务服务。
- 每服务拥有独立数据库账号、Flyway、Dockerfile、Deployment、Service 和 probes。
- 项目没有生产数据迁移负担，本地旧 volume 可重建，不设计历史库升级路径。
- 本地入口：`scripts/dev/microservices.ps1`；Kind 入口：`scripts/ci/kind-local.ps1`。
- 验收证据：Compose 全部服务健康、四库 Flyway 可读且跨库访问被拒；Kind 中全部 Pod Running、PVC Bound、Gateway/Web 冒烟通过。
- 提交：`ops: deploy isolated microservice data and messaging infrastructure`

### 工作项 7：微服务测试闭环 — 已完成

- 每服务已补齐单元、API、MySQL/RabbitMQ/Redis Testcontainers 与跨服务契约测试。
- 45 个公开 method + path 已建立成功、参数错误、未登录或越权覆盖追溯。
- 按最新版 `doc/业务场景用例清单` 的 UC01—UC08 建立机器可读映射和运行证据门禁；本地最终报告 92/92（单元 44、API/集成 33、E2E 15）。
- 提交：`7e71804 test: cover microservice contracts and all business journeys`

### 工作项 8：独立 CI/CD 与可观测性 — 实现完成，待主分支 CI 验收

- 流水线已经按五个独立 Maven 工程 → 契约/前端 → 微服务 E2E → 六个 SHA 镜像 → Kind 全拓扑 → 健康/版本冒烟建立严格 `needs` 门禁。
- Gateway、Account、Marketplace、Trading、Governance 分别发布 `health/info`，镜像和运行环境暴露提交 SHA，控制台使用 ECS JSON 结构化日志。
- Gateway 生成或校验 `X-Correlation-Id`，REST 调用继续传递；交易/治理 Outbox envelope 保存 correlationId。失败诊断总是上传资源、事件、Pod 描述、当前与上一容器日志、镜像和健康结果。
- 5 类 RabbitMQ v1 事件已有 JSON Schema 和 CI 门禁；业务命令使用独立 `commandEventId` 做结果匹配，`correlationId` 只做跨 REST/消息日志追踪，消费者允许增加未知元数据以支持滚动升级。
- 本地完整 Compose 回归 15/15 通过，UC01—UC08 运行证据 8/8；三方安全、CI/追溯和整体审查指出的问题已经收口，无剩余 P0/P1。统一报告在失败时也会上传已有测试统计和失败摘要。
- 分支首次运行暴露私聊旅程仍使用 5 秒默认断言超时；trace 确认 Session Cookie 正常、请求只是受冷启动负载影响尚未完成。现已改为该旅程 120 秒总预算和 30 秒 Session 恢复断言，最小回归 1/1、完整 Compose 15/15 再次通过，等待新提交的 GitHub Actions 复验。
- 主分支全绿后再创建 `microservices-end`，当前不得提前移动或创建该标记。
- 提交：`ci: build test and deploy independent microservices`（使用 `git log -1` 查看提交号）

## 5. 暂缓的实验工作项

工作项 9—11 只有在微服务工作项 1—8 全绿后开始。

### 工作项 9：HPA 自动扩缩容

- Marketplace 图片 adapter 先切换 MinIO，使实例无本地状态。
- Metrics Server + `autoscaling/v2` HPA，副本 1—5，CPU 目标约 60%。
- k6 对搜索接口加压并记录副本、吞吐、平均/P95、错误率、CPU 和内存。

### 工作项 10：依赖故障隔离

- 停止 Marketplace 或注入延迟。
- Trading 使用超时、限次重试和熔断，返回 `503 PRODUCT_SERVICE_UNAVAILABLE` 且不创建订单。
- 证明 Account、Governance 和既有交易读取不跟随崩溃。

### 工作项 11：单体与微服务性能对比

- 比较 `monolith-start` 与 `microservices-end`。
- 同一机器、数据、Kind 版本、资源限制和 k6 脚本。
- 接口：`GET /api/items`、`GET /api/items/{id}`、`GET /api/messages/item/{itemId}`。
- 并发 10/50/100，每档每种架构至少运行 3 次；保存原始数据，不预设微服务一定更快。

## 6. 每项完成定义

- 业务规则和反向权限测试通过。
- 对外 interface、事件 Schema 与调用方契约同步。
- 服务没有访问其他服务的数据库或 Repository。
- `mvn verify`、前端静态测试、相关 E2E 与 `git diff --check` 通过。
- `docs/ai/PROJECT_CONTEXT.md` 和 `docs/ai/WORKLOG.md` 更新实际完成状态、测试证据和提交号。
- 一个工作项对应一个可回滚提交；工作项未通过验收时不开始下一个。
