# AI 持续工作日志

本文件记录已经落地的事实，不记录未经确认的设想。每轮完成后在顶部追加结果，包含验证证据和提交号。

## 当前状态

### 第一项验收差距复核（2026-08-29）

- 环境复验：提升权限后 Docker Engine/Compose 可用，`deploy` 的 MySQL、Backend、Web、Mailpit 均运行，`http://localhost:8088/api/actuator/health/liveness` 返回 `{"status":"UP"}`。
- `cd backend; mvn verify` 通过：单元 35/35、集成与应用测试 57/57，Flyway 6 个迁移和 MySQL 8.4 Testcontainers 并发测试通过。
- Compose E2E 首轮执行：8/10 通过；取消购买意向与管理员留言删除两条新增旅程因测试流程断言问题失败，已修正。修正后的定点命令因 runner 已清理隔离环境而无服务可用，超时不作为业务结果；需重新运行完整 `npm run test:e2e:compose` 验证。
- Compose E2E 复验：完整隔离环境运行 9/9 通过（含举报回执、管理员留言删除、资料修改和卖家重新上架），runner 自动清理容器和网络。

- 复核当前分支 `codex/close-test-gaps`：独立测试和页面断言已补齐，测试清单状态已同步。
- 验证：`node --test frontend/tests/*.test.js` 3/3 通过；全部 `frontend/assets/js/*.js` `node --check` 通过。
- 当前验收遗留仅为远程 GitHub Actions 全绿/受控失败运行证据和最终 Git 标签；代码侧测试缺口已关闭。
- 新增《从系统顺序图生成 API 集成测试用例说明》，明确系统图元素到 MockMvc/数据库/反向权限断言的转换规则，并给出 SYS-SEQ11、SYS-SEQ16、SYS-SEQ18 的完整示例和 UC01～UC18 测试映射，补齐建模到代码验收的答辩材料。
- 根据最新课程要求修订测试分层口径：对象级顺序图对应单元测试；模块化单体阶段不单列组件级集成测试；系统顺序图对应后端容器系统测试；GUI 端到端测试归入增强项验收测试。同步调整《软件测试文档》和模型到测试说明标题/术语。
- 统一测试文档格式：模型到测试说明改为“对象级/系统级”术语，章节编号连续，系统测试入口与后端容器口径一致；原有组件级集成测试表述改为当前单体阶段不单列。
- 曾新增后删除 `scripts/run-local.ps1` 一键本地入口；当前统一使用 `deploy/README.md` 中的直接 Compose 命令，避免维护重复入口。
- 扩展《从对象级顺序图和系统顺序图生成测试用例》，新增 UC01～UC10、UC12～UC15、UC17 的基本流程、扩展/异常流程和建模验证点，连同原有 UC11、UC16、UC18 示例覆盖全部 UC01～UC18。
- 本轮新增 `unit/order/TradingServiceTest` 与 `unit/chat/DirectChatServiceTest`，分别锁定自购/未知买家、空消息和禁用用户等稳定规则边界；测试清单已同步为部分覆盖，未将少量单测误写成完整状态机覆盖。
- 新增 `SecurityApiIT`，集中验证受保护写请求必须同时具备 Session 与 CSRF，且请求体伪造 `sellerId` 不会改变资源归属。
- 新增 `SearchApiIT` 和 `ProductImagesTest`，分别覆盖用户搜索隐私/关键词上限，以及图片格式拒绝、标准化存储、owner 路径和路径穿越保护。
- 扩展 `product-publish-edit-question-journey.spec.js`，加入个人资料修改和卖家下架/重新上架的独立 Playwright 旅程（UC04、UC07）。
- 扩展 `chat-trade-admin.spec.js`，加入举报人进入“我的举报”查看治理说明，以及普通学生访问 `/api/admin/messages` 返回 403 的反向断言（UC15、UC17）。
- UC13 取消/超时分支继续由后端 API/时钟测试负责；此前新增的 UI 取消旅程因测试夹具会话不稳定已移除，避免把不可靠断言计入通过。
- 管理员治理旅程新增公开留言创建与后台删除断言，确认 UC17 留言管理的真实页面结果。

课程 CI 工作项 1—8 已全部在代码侧完成。工作项 8 的本地成功与受控失败路径均已通过；合并并 Push 到 `main` 后，需要在 GitHub Actions 保存一次自动全绿运行，再通过 `workflow_dispatch` 的 `controlled_failure` 保存一次预期失败运行，作为课程远程验收材料。

## 测试缺口补全第二轮（2026-08-28）

- 分支：`codex/close-test-gaps`。
- 扩展 `AuthApiIT`，覆盖重置验证码用途校验、成功后新密码登录、旧密码失效和验证码重复消费。
- 扩展 `ProductApiIT`，覆盖公开留言发布、本人修改/删除、他人越权失败及匿名读取隔离，并断言响应结果。
- 更新 `doc/测试清单.md`：UC03、UC09 标记为当前实现已覆盖；Search/PublicQuestion/Security 等微服务测试保留为迁移后的实际服务工作项，未伪造不存在的模块。
- 验证：新增测试首次发现留言接口契约为 HTTP 200 而非 201，已按项目统一 `ApiResponse` 契约修正断言；`mvn -q "-Dtest=AuthApiIT,ProductApiIT" test` 通过（8/8），随后 `mvn -q verify` 通过，Flyway 与 MySQL 8.4 Testcontainers 并发测试执行；全部前端 JS `node --check` 通过，`git diff --check` 通过。
- 提交号：本轮尚未提交。

## 测试补全分支首轮（2026-08-28）

- 新建分支 `codex/complete-uc-tests`。
- 扩展现有 `TradingApiIT`：补充卖家拒绝、买家取消、卖家取消预留订单、重复取消/完成等状态和异常断言。
- 扩展现有 Playwright 旅程文件：新增独立订单工作台旅程，断言买卖视角、阶段筛选、商品分组、时间线和 `allowedActions` 对应的页面动作。
- 更新 `doc/测试清单.md` 的覆盖状态，明确微服务目录尚不存在，Search/PublicQuestion/Security 等测试需在服务迁移后落到实际 owner，而不是旧 backend。
- 验证：`mvn verify` 通过，单元/API/MySQL 集成测试 51/51 通过（真实 MySQL 8.4 Testcontainers 5 项并发测试均执行）；前端 Node 契约 3/3、全部 JS 语法通过；Compose Playwright E2E 7/7 通过，包含新增订单工作台旅程。期间修正了订单工作台实际按钮文案“取消交易”“确认已取货”和实际容器 `#orderGroups` 选择器。
- 提交号：本轮尚未提交。

## 测试清单与补齐项（2026-08-28）

- 新增 `doc/测试清单.md`，按照单元测试、集成/API 测试、端到端测试三层定义执行内容、结果断言、有效性标准和 UC01～UC18 覆盖关系。
- 清单区分当前已有自动化覆盖与仍需补齐的独立测试：SearchApiIT、PublicQuestionApiIT、SecurityApiIT、TradingServiceTest、DirectChatServiceTest、图片规则单测、订单工作台 E2E 及若干反向旅程。
- 明确不能用“程序未报错”、HTTP 2xx 或 smoke 测试代替业务结果断言；MySQL Testcontainers 不可用时必须记录为未完成/跳过，不得计为通过。
- 验证：清单引用的实际测试类和 E2E 文件均存在；新增文档 `git diff --check` 通过。本轮未修改测试代码，未重新执行全量 Maven/E2E。
- 提交号：本轮尚未提交。

## 恢复需求说明书图片引用（2026-08-28）

- 在《软件需求说明书》中恢复当前 `doc/images/软件需求说明书/` 目录下的用例图参考、概念类图以及 `SYS-SEQ01`～`SYS-SEQ18` 系统顺序图引用。
- 图片按 `REQ01`～`REQ18` 分节放置，未回退已重写的新版需求正文和交易/安全规则。
- 验证：20 个 Markdown 图片引用全部解析到现有文件；`git diff --check` 通过；需求说明书仍包含 18 条 `REQxx`。
- 提交号：本轮尚未提交。

## UC01～UC18 课程文档统一（2026-08-28）

- 依据新版《业务场景（用例）清单》和《需求追溯矩阵》，重写软件需求说明书、软件测试文档、软件用户手册、软件开发计划书、模块设计方案和 UI 视觉与交互设计方案。
- 移除旧版“创建订单即售出”、商品分类侧栏、客户端卖家 ID、`schema.sql` 建表和直接打开 HTML 等过时描述；统一为学生用户、非独占购买意向、唯一预留、当面交接、Session/CSRF、`CurrentActorService`、`TradingService` 和 Flyway 当前契约。
- 需求说明书现包含 `REQ01`～`REQ18` 唯一基线；测试文档按单元、API、MySQL 并发、前端契约、Playwright E2E 和部署门禁组织；用户手册覆盖 UC01～UC17 的实际页面流程和 UC18 安全体验。
- 模块设计方案从历史“未来重构草案”改为当前模块化单体基线；UI 方案移除宣传横幅、分类侧栏等已废弃结构，固化桌面与 390px 移动端业务页面规则。
- 验证：18 条 `REQxx` 与 18 个 `UCxx` 数量一致；全部 Markdown 图片引用存在；旧术语定点扫描无错误性残留；`git diff --check` 通过；前端 Node 契约测试 3/3、全部 JS `node --check` 通过；`mvn verify` 构建成功，单元测试 29/29、已执行集成测试 44/44 通过，当前 Docker 环境不可用导致 5 项 MySQL Testcontainers 测试跳过。
- 提交号：本轮尚未提交。遗留项：本轮仅改文档，未重跑 Compose E2E 或浏览器截图；真实 MySQL 门禁沿用此前通过记录，待 Docker 可用时可再次执行。

## 需求追溯表统一（2026-08-27）

- 将旧版仅含 8 个用例的 `doc/需求追溯矩阵.md` 更新为当前 `REQ01`～`REQ18` / `UC01`～`UC18` 基线。
- 在同一张表中汇总需求、用例、系统/组件/对象三层模型编号、实际代码模块、UNIT/INT/E2E 测试编号、具体测试类和结果。
- 区分“当前执行通过”“最近一次 E2E 通过”“由集成测试覆盖”和“尚无独立 E2E 断言”，避免把规划编号误写为已经执行的独立测试。
- 验证：追溯表包含 18 条 `REQxx` 记录，`git diff --check` 通过；全部前端 JS `node --check` 和 3 组 Node 契约测试通过；`mvn '-Djava.version=24' verify` 构建成功，单元测试 29/29、已执行集成测试 44/44 通过，Docker 不可用导致 6 项 MySQL Testcontainers 测试跳过。完整 Compose E2E 未在本轮重跑，沿用 2026-08-26 的 6/6 通过记录并在表中明确标注。
- 提交号：本轮尚未提交。

### 本轮补充：封禁提示与举报治理回执（2026-08-27）

- 管理员封禁账号时保存治理说明；被撤销的旧 Session 访问接口返回包含说明的封禁提示，前端保留提示并在当前页面展示后再引导重新登录。
- 举报支持绑定当前私聊会话，服务端校验举报人和被举报人确属会话参与方，并保存最近 30 条消息证据快照；管理员治理队列展示该快照。
- 新增“收到的治理结果”接口和个人中心页面区块，被举报方可查看处理结果及管理员确认说明；举报提交、治理动作和证据均保持服务端身份与资源归属校验。
- 新增 Flyway `V6__governance_evidence_and_notices.sql`，同步更新接口模型、前端报告页、Postman 契约相关字段和项目上下文。
- 验证：Docker 恢复后执行 `mvn clean verify`，单元与集成测试 49/49 通过，真实 MySQL 8.4 完成 V1—V6 空库迁移、Hibernate validate 与并发测试；前端 Node 静态测试 3/3、全部 JS `node --check`、`git diff --check` 通过。验证过程中修复多构造器导致的 Spring 注入歧义，并将 MySQL 迁移计数断言同步为 6。
- 提交号：本轮尚未提交。遗留项：Playwright Compose 已进入构建阶段，但 Docker Hub 被本机代理证书阻断（`x509: certificate signed by unknown authority`），需修复 Docker Desktop CA/代理信任后全量复验。

## 第十四轮：个人中心完整重设计

- 分支：`codex/round-14-profile-redesign`。
- 将个人中心拆为身份概览、学校验证/信用与区域信息、真实交易统计、功能入口和公开资料设置；管理员入口继续默认隐藏并由 Session 角色 hydration 控制。
- 统计仅使用现有 `/items/mine`、`/orders/desk` 和 `/chat/unread-count` 接口；未实现的收藏/关注没有添加假入口。
- 资料保存加入 loading、成功提示、失败恢复和取消编辑；移动端改为纵向工作区，桌面端采用概览 + 内容双栏。
- 验证：全部前端 JS `node --check`、`node frontend/tests/ui-design-contract.test.js`、`git diff --check` 通过。
- 提交号：本轮尚未提交。
- 遗留项：收藏/关注数据结构与入口待对应后端能力落地后实施；Maven 全量测试和真实 Docker 截图尚未在本轮运行。

## 已完成轮次

### 第十五轮：私聊页面完整重设计（进行中）

- 分支：`codex/round-15-chat-redesign`。
- 私聊页重构为桌面会话列表、聊天房间、商品/交易上下文三栏；移动端保持会话列表与房间两级导航。
- 会话列表增加关键词搜索、未读优先和分页；消息增加日期分段、历史加载状态、发送中、失败重试和断线重连反馈。
- `DirectChat.ConversationView` 增加真实商品价格与状态，商品下架、预留或售出后旧会话继续展示上下文；管理员私聊权限边界不变。
- 结构化报价尚未实现：需要独立消息类型、报价状态机及与购买意向的服务端事务集成，不使用文本或前端状态伪造。
- 当前验证：Docker Compose 的 MySQL、Backend、Web 均正常启动，后端 liveness 为 `UP`；`mvn verify` 50/50 通过、0 失败、0 跳过，真实执行 MySQL 8.4 Testcontainers 空库迁移、Hibernate validate 与并发测试；前端 Node 测试 3/3、全部 JS 语法通过；浏览器检查 1440、390、320px 无整体横向溢出。
- E2E 复验修复 MySQL 健康检查误判：改用 `127.0.0.1:3306`，避免初始化临时 Unix socket 服务使 Backend 在正式 TCP 服务启动前连接失败。修复后隔离 MySQL、Backend、Web 均稳定健康；本机 Playwright 1.62.1 与 Chromium 1234 已补齐。最终全量重跑时 Docker Engine 整体失去响应（独立 `docker ps` 同样超时），浏览器用例未取得最终通过结果，需 Docker Desktop 恢复后重跑 `cd e2e; npm run test:e2e:compose`。
- Docker Desktop 恢复后重新验证：全量 6 个用例中 5 个首次通过，发现 E2E 未读文案断言仍使用旧的 `1 未读`，已同步为页面当前契约 `1 条未读`；定点重跑私聊/交易/管理员 3 个用例全部通过。完整 6 个用例的另外 5 个已在同一轮通过，剩余风险为未在文案修复后再次执行全量命令。
- 消息流改为顶部对齐的纵向 flex 布局，每条消息禁止伸缩，气泡宽度随内容变化并继续受桌面 `72%/580px`、移动 `84%` 上限约束，修复少量消息均分聊天区高度的问题；前端 Node 测试 3/3、全部 JS 语法、`git diff --check` 通过，Docker Web 已重建。
- 用户复验后进一步将消息气泡上下内边距收紧为 `7px`、行高设为 `1.35`、消息间距降为 `6px`，并显式清除消息项最小高度；前端 Node 测试 3/3 与 `git diff --check` 通过。Docker Web 重建时 Engine 在解析 Nginx 镜像阶段无响应，需 Engine 恢复后再次部署。
- 截图复核确认用户所指为左侧 `button.conversation-card`：`.conversation-list` 的 Grid 默认拉伸导致少量会话均分整列高度。列表现改为 `align-content:start` 与 `grid-auto-rows:max-content`，会话行使用紧凑的 `88px` 最小高度；前端 Node 测试 3/3 与 `git diff --check` 通过。
- 聊天工作区高度改为受桌面/移动动态视口约束，`.chat-room` 禁止内容撑高，`.chat-messages` 成为独立纵向滚动区域；会话列表与交易信息列也在工作区内部滚动。前端 Node 测试 3/3、全部 JS 语法和 `git diff --check` 通过，Docker Web 已重建部署。
- 复核新增会话路径：所有 `button.conversation-card` 均由 `paintConversations()` 渲染到 `.conversation-list`，列表使用 `flex:1`、`min-height:0` 与 `overflow-y:auto`，新增聊天只增加内部滚动长度，不会撑高页面；UI 静态契约现同时锁定会话列与消息列的内部滚动约束，前端 Node 测试 3/3 通过。
- 提交号：本轮尚未提交。
### GitHub Actions Node 24 依赖升级（2026-08-27）

- 将 CI 中 GitHub 官方 Action 升级到 Node 24 兼容主版本：`checkout@v7`、`setup-java@v5`、`setup-node@v7`、`upload-artifact@v7`。
- 同步升级镜像构建与发布链路：`setup-buildx-action@v4`、`login-action@v4`、`build-push-action@v7`；现有 GHCR 权限、版本化镜像标签和 Kind 部署条件保持不变。
- 项目测试运行时保持 Java 25 与 Node.js 22；本轮只升级 Action 自身运行时依赖，不改变应用兼容基线。
- 验证：actionlint 1.7.12 无输出且退出 0；旧 Action 主版本扫描无匹配，`git diff --check` 通过。远程最终证据为 GitHub Actions 完整流水线。
- 提交：本条记录所在的提交（使用 `git log -1` 查看）。

### 课程 CI 工作项 8：Kind 部署、冒烟与验收证据闭环（2026-08-26）

- 新建 `scripts/ci/deploy-kind.sh` 作为唯一部署 interface：调用方只提供 Backend/Web 两个版本化镜像，implementation 负责重建隔离 Kind、随机 Secret、Kustomize apply、镜像切换、rollout、端口转发与冒烟。
- GitHub Actions 新增 `deploy-kind` Job，仅在测试和 `sha-<提交号>` 镜像构建全部成功后运行；`main` Push 自动部署，手动运行支持 `controlled_failure`。
- 首页、Backend liveness/readiness 均必须成功；脚本错误或受控失败保持非 0，未使用 `continue-on-error`，因此部署失败会让流水线失败。
- EXIT 清理与取证无论成功失败都会执行，保存资源、事件、Pod 描述、所有容器日志、健康响应和 Markdown 摘要；临时 Secret 文件在退出时删除，验收 artifact 不含密码。
- 验证：两个独立空 Kind 集群分别得到 `SUCCESS / 0` 和预期 `FAILED / 42`，两次 rollout 与冒烟均先通过且证据齐全；actionlint 1.7.7、Bash 语法和 `git diff --check` 通过。
- `styles.css` 只有已删除宣传横幅样式的历史部分暂存，工作区内容与 HEAD 一致；已清理暂存状态，没有产生 CSS 修改或回退产品决定。
- 提交：本条记录所在的工作项提交（使用 `git log -1` 查看）。

### 课程 CI 工作项 7：Kubernetes 清单与 Kind 部署（2026-08-26）

- `k8s/base` 建立 Namespace、ConfigMap、Secret 键名示例、MySQL StatefulSet、Backend/Web Deployment、ClusterIP Service 和两个 PVC；敏感值只从运行时 Secret 注入。
- `k8s/overlays/ci` 使用 Kustomize 复用 Base，仅增加 Mailpit、CI 邮件配置和随机本地 Secret，避免复制整套 YAML。
- Backend、Web、MySQL 和 Mailpit 均配置 readiness/liveness；启动较慢的 Backend/MySQL 另设 startup probe。后端只精确匿名开放 liveness/readiness，其他 Actuator 仍受保护。
- `scripts/ci/kind-local.ps1` 可从零创建 Kind、构建并加载本地镜像、生成随机 Secret、等待 rollout、通过临时 port-forward 冒烟，并支持状态、访问转发和删除集群。
- 验证：Kustomize 客户端 dry-run 创建 13 个资源；真实 Kind v0.32.0 / Kubernetes v1.36.1 空集群部署后 4/4 Pod Ready、0 次重启，2/2 PVC Bound；Web 首页和后端 liveness 经同源 Nginx 返回成功；Maven 单元测试 29/29 通过；`git diff --check` 通过。
- 提交：本条记录所在的工作项提交（使用 `git log -1` 查看）。

### 合并后旧内容清理（2026-08-25）

- 重写软件实现、概要设计和详细设计说明书，使其与 Session、CSRF、受控图片、私聊和购买意向状态机一致。
- 修正发布页的卖家身份注释，删除未使用的订单宣传样式。
- 保留测试中伪造 `buyerId`、`sellerId`、`adminId` 的反向用例，用于证明后端不信任客户端身份字段。
- 验证：前端语法与页面契约检查通过；残留扫描不再在已清理文档中发现旧身份或旧订单模型。
- 提交：本条记录所在的提交（使用 `git log -1` 查看）。

### 课程 CI 工作项 6：全测试门禁与版本化镜像（2026-08-26）

- GitHub Actions 按依赖严格串联 `unit-tests → api-integration-tests → frontend-static-tests → e2e-tests → build-images`，任何测试非 0 都不会进入后续阶段，未使用 `continue-on-error`。
- 单元/API 测试失败时仍上传 Surefire、Failsafe 和 HTML 报告；E2E 无论成功失败都上传 Playwright HTML、截图、视频、trace、控制台和网络失败证据。
- E2E Job 使用 MySQL、Mailpit、Backend、Web 的独立 Docker Compose 环境；5 条核心业务旅程和 runner smoke 全绿后才允许镜像 Job 执行。
- 仅 push 事件使用 GitHub Token 写 GHCR，分别生成 `ghcr.io/adonisklein/campus-backend:sha-<7位提交号>` 和 `campus-web:sha-<7位提交号>`；PR 只执行测试，不推镜像，也不生成 `latest`。
- 验证：actionlint 1.7.7 无输出且退出 0；前端 3/3 契约测试及全部 JS 语法通过；首次完整 E2E 稳定复现管理员 prompt 竞态并返回非 0、证据齐全，修复为对话框与点击原子等待后定点 1/1、全量 6/6 通过。
- 提交：本条记录所在的工作项提交（使用 `git log -1` 查看）。

### 课程 CI 工作项 5：API 集成测试与 MySQL 并发门禁（2026-08-26）

- 将认证、资料、商品、私聊、交易、举报治理和管理员场景拆入独立 `*ApiIT`，共享 `AbstractApiIntegrationTest` 统一 Session 登录、数据工厂与外键安全清理。
- 每组 API 测试均覆盖成功、输入/备选分支、未登录或越权分支，并直接断言商品、订单、举报、用户状态和审计记录等数据库最终状态。
- 新建真实 MySQL 8.4 Testcontainers 门禁，覆盖空库 Flyway/Session，以及同邮箱注册、同验证码消费、同商品并发选择买家、同举报并发处理四种竞争条件。
- 验证：`mvn clean -Djava.version=24 verify` 的首轮失败已证明 Failsafe 会返回非 0；修正契约断言后，单元测试、API 集成测试和真实 MySQL 并发测试统一由 `mvn verify` 执行并生成 XML/HTML 报告。
- 提交：本条记录所在的工作项提交（使用 `git log -1` 查看）。

### 重构分支并入 main（2026-08-25）

- 将 `codex/refactor-foundation` 合并到 `main`。
- README 与 7 份课程文档的冲突统一采用重构分支内容；`main` 中无冲突的业务场景文档与配图继续保留。
- 验证：Maven、前端 JavaScript 语法与 `git diff --check` 结果记录于本次合并过程。
- 提交：本条记录所在的合并提交（使用 `git log -1` 查看）。

### 认证成功跳转收口（2026-08-25）

- 注册和密码重置成功后统一调用 `api.js` 的跳转入口，自动进入个人中心的登录表单并显示一次性成功提示。
- 登录页消费提示后立即清除，兼容旧版注册提示键，刷新页面不会重复显示。
- 新增 Node 契约测试，覆盖共享跳转入口、注册流程、重置密码流程和一次性提示清理。
- 验证：自动跳转、UI 设计和会话竞态 3 组 Node 回归测试通过，全量前端 JavaScript 语法检查通过；Docker Web 重建成功，注册页与登录页均返回 200，部署脚本包含统一跳转入口，MySQL、backend、Mailpit 均 healthy。
- 提交：本条记录所在的功能提交（使用 `git log -1` 查看）。

### Mailpit 本地验证码闭环（2026-08-25）

- 新增仅开发使用的 `docker-compose.mailpit.yml`：Mailpit SMTP 只在 Compose 私有网络开放，Web 收件箱只绑定本机 `127.0.0.1:8025`。
- 本地注册和生产注册共用验证码与邮件 interface；开发 adapter 截获邮件，生产 adapter 继续使用阿里云，不增加任何验证码后门或日志明文。
- README 和部署文档补齐启动、注册、查看验证码、停止和切回阿里云配置步骤。
- 验证：官方 `axllent/mailpit:v1.30.6`、MySQL、backend、web 全部运行，Mailpit 和后端均 healthy；真实调用注册验证码接口成功，收件人、主题匹配且正文存在 6 位验证码，Mailpit UI 返回 200。
- 提交：本条记录所在的功能提交（使用 `git log -1` 查看）。

### 阿里云邮件推送生产接入（2026-08-25）

- 邮件 module 显式设置 `MAIL_FROM`，增加 `MAIL_ENABLED` 启停和启动校验；未启用或 SMTP 失败时返回固定 503，不泄露服务商异常。
- 支持阿里云邮件推送华东地址、465 隐式 TLS，以及受限网络下可配置的 80 + STARTTLS；连接、读取和写入均有超时。
- Docker、后端环境示例和两份部署文档同步发信子域名、DNS 验证、SMTP 发信地址和密码配置，不提交任何真实凭据。
- 增加邮件 module 单测及 SMTP 失败使验证码 challenge 作废的接口回归测试。
- 验证：Maven 29/29 通过（H2 24 + 邮件 module 4 + 真实 MySQL 8.4 Testcontainers 1）；Docker JDK 25 构建成功，现有未启用邮件的本地配置仍能健康启动，liveness 为 `UP`。
- 提交：本条记录所在的功能提交（使用 `git log -1` 查看）。

### 路线图与本地部署文档收口（2026-08-25）

- 将收藏/关注、评价信用、通知实时推送、多图、搜索增强、原生端 Token、Redis/对象存储/消息队列正式登记到开发计划，并明确依赖、优先级和“暂不实施”边界。
- 重写 `doc/软件部署文档.md`，区分 Docker 一键运行与本机 MySQL/Maven 调试，补齐环境变量、Flyway、演示数据、自动化测试、最小验收、停止和排障步骤。
- 修正 README 中 PowerShell 不支持直接 `<` 调用 MySQL 的旧命令，明确 `.env.example` 不会被 Spring 自动加载、前端不得通过 `file://` 运行。
- Docker Compose 配置解析通过；运行中的 MySQL 8.4 和 backend 均为 healthy，Web 正常运行，liveness 返回 `UP`；真实凭据仍只在 Git 忽略的 `deploy/.env`。
- 提交：本条记录所在的文档提交（使用 `git log -1` 查看）。

### 第十三轮：买卖双方订单工作台

- 新建 `TradeDesk.browse` 深 interface，服务端直接返回买入/卖出视角、阶段统计、卖家按商品分组的购买意向、买家安全公开摘要、有效状态、时间线、关闭原因和 `allowedActions`。
- 订单页重构为蓝黄交易引导、买卖身份切换、阶段筛选、待办统计、剩余时间、商品意向组、买家信用比较、交易进度抽屉和页面内确认框；订单操作不再使用浏览器 `alert`。
- 每笔记录统一提供查看商品、私聊对方和举报；新增 `DirectChat.openTrade`，买家或卖家都能从自己的订单创建/复用对应会话，商品预留后仍可继续沟通，陌生用户仍被服务端拒绝。
- 私聊页面完整重设计加入第十五轮：桌面三段式、移动双层页面、会话筛选、商品/交易上下文、结构化报价、失败重试及安全治理。
- 验证：Maven 24/24 通过（H2 23 + 真实 MySQL 8.4 Testcontainers 1）；Node UI 契约与 JS 语法通过；Docker MySQL/backend/web 健康，Alice 登录后订单工作台 API 返回 BUYING 视角、2 组记录与 1 条待办。
- 视觉：1440、768、390、320px 真实 Docker 截图通过；修复订单阶段筛选被全宽按钮挤出和 320px 五入口移动导航换行问题。
- 用户复验后的页面收口：设计预览只保留在文档；首页活动口号、订单介绍横幅、商品详情交易流程卡、发布技巧侧栏、登录/注册欢迎介绍及各页装饰性英文 kicker 已从 10 个实际页面移除。登录/注册/发布改为单任务布局，并加入自动化禁止回退契约。

### 第十二轮：商品详情页完整重设计

- 新建 `ProductDetail.show(itemId, viewerId)` 深 interface，一次返回商品、安全卖家摘要、当前用户有效购买意向、可执行动作和同卖家其他在售商品。
- 卖家公开信息严格限制为昵称、校园区域、信用、活跃时间和在售数量；游客、已提交意向的买家、卖家本人分别获得适合自己的动作，不在前端复制交易规则。
- 桌面页落地图片画廊、价格与状态、卖家信用卡、交易动作、详情/成色、交易流程、同卖家商品、公开问答和安全提醒；移动页图片优先并使用固定“私聊 / 我想要”操作栏。
- 公开问答与私聊完成视觉和权限分区；问答读取复用详情可见性，下架、管理员移除或禁用卖家商品不再被游客通过留言接口旁路读取或写入。
- 验证：Maven 23/23 通过（H2 22 + 真实 MySQL 8.4 Testcontainers 1）；前端两项 Node 契约、10 页 UI、全部 JS、Postman JSON 和 `git diff --check` 通过。
- Docker 真实 MySQL 实测游客动作、隐私字段、Alice 提交/取消意向和商品继续 `ON_SALE`；1440、768、390、320px 截图通过；三个低成本 Luna 子 Agent 复验无剩余 P0/P1。

### 第十一轮：闲鱼式分阶段交易

- 买家创建的是非独占购买意向，商品继续在售；同一商品可以接收多位买家请求，同一买家不能重复提交有效意向。
- 卖家接受一位买家后才形成独占预留，其他待回应意向自动进入 `DECLINED` 并说明原因；卖家也可以单独拒绝。
- 购买意向过期不改变商品；待当面交易取消或过期才释放预留；买家确认取货后商品售出。
- 第十二至十四轮分别规划商品详情、订单管理和个人中心完整重设计，见 `docs/roadmap/2026-08-trading-and-page-redesign.md`。
- V5 完成旧状态和字段迁移；订单接口、Postman、中文项目文档与 AI 上下文已经同步新术语和新动作。
- 验证：Maven 21/21 通过（H2 20 + 真实 MySQL 8.4 Testcontainers 1），Flyway V1—V5 与 Hibernate validate 通过；前端两项 Node 契约、10 页 UI、全部 JS 语法、Postman JSON 和 `git diff --check` 通过。
- Docker 实测购买意向创建后商品仍为 `ON_SALE` 且公开可见，卖家接受后才变为 `RESERVED`；三个低成本 Luna 子 Agent 完成功能、安全与 UI 审查，最终无剩余 P0/P1。

### 第十轮：买卖双方私聊闭环

- `chat` 独立于 `message`：前者是参与者私聊，后者继续作为商品详情的公开问答，避免权限和产品语义混在一起。
- V4 建立会话、消息和屏蔽表；随机 UUID 对外定位会话，消息使用单调 sequence，买卖双方分别保存已读游标。
- `DirectChat` 集中创建会话、参与者授权、游标分页、发送限流、未读计算和双向屏蔽；发送者只取 Session，管理员不能读取学生私聊。
- Web 新增私聊消息页、商品详情“私聊卖家”、全站未读角标、移动单页会话、轮询刷新、举报与屏蔽。
- 验证：Maven 20/20 通过（H2 19 + 真实 MySQL 8.4 Testcontainers 1）；前端 10 页 UI 契约、Session 竞态、全部 JS 语法与 Postman JSON 通过。
- Docker 实测 Alice 向 Bob 的商品会话发送私聊：发送者由 Session 正确推导，Bob 未读 `1 → 0`，私聊历史可见且公开留言接口无正文泄漏，Flyway 为 V4，消息页返回 200。
- 三个低成本 Luna 子 Agent 完成安全、功能/数据与 UI 复验；商品锁已证明会话创建并发串行，最终无剩余 P0/P1。

### 第九轮：举报与内容治理闭环

- 提交：本条记录所在的第九轮功能提交（使用 `git log -1` 查看）。
- 新增商品、留言、用户三类举报；举报人只从学生 Session 推导，禁止举报自己、重复举报和 24 小时内超过 20 条。
- 学生可从商品详情、留言和用户搜索发起举报，并在“我的举报”查看处理中、成立或驳回结果及管理员说明。
- 建立 `ContentGovernance` 深 interface；管理员举报队列可确认并下架商品、移除留言、禁用用户，或驳回且不改变内容。
- V3 新增 `content_reports` 与追加式 `report_actions`；对象快照保留核查上下文，行锁、唯一约束和真实 Session 管理员保证并发与审计边界。
- 更新 Postman、业务词汇和 AI 项目上下文；前端继续复用 Session/CSRF、登录前确认与默认隐藏管理入口。
- 验证：Maven 19/19 通过（H2 18 + 真实 MySQL 8.4 Testcontainers 1）；前端 9 页 UI 契约、Session 竞态和全部 JS 语法通过；Docker 实测 Alice 举报“宿舍小台灯”，管理员驳回，状态 `OPEN → DISMISSED` 且审计 adminId 与真实管理员一致。

### 第八轮：无侧栏首页、校园搜索与登录前确认

- 提交：本条记录所在的第八轮功能提交（使用 `git log -1` 查看）。
- 首页按已确认预览稿重构为品牌顶栏、中置搜索、蓝黄活动横幅、商品标签推荐和响应式商品卡片；不再使用分类侧栏或分类筛选。
- 建立 `CampusSearch` 深 interface，支持多个关键词、商品/用户范围切换、最近发布/活跃/同区域优先/信用/价格排序，以及价格、校园区域、商品标签筛选和分页。
- 用户搜索使用独立安全投影，只公开用户名、昵称、校园区域、信用分和最近活跃时间，不返回邮箱或手机号；选择用户后可继续查看其在售商品。
- 商品增加校园区域和最多 4 个卖家标签；用户增加校园区域、信用分和最近活跃时间；Flyway V2 同时在 H2 与 MySQL 8.4 通过。
- `api.js` 集中处理受保护导航：游客点击发布、订单、留言、下单等操作先在当前页看到确认框，确认后才登录，成功后返回目标页面；管理中心仍默认隐藏，仅 ADMIN Session 显示。
- 故障复盘：MySQL 8.4 不允许 `SELECT DISTINCT item` 按未选择的卖家信用字段排序，而 H2 未暴露该差异；移除无必要的 DISTINCT，并以真实 MySQL 搜索请求补足验证。另用 UTF-8 十六进制字面量修复本地演示字段因 CLI 编码产生的乱码。
- 验证：Maven 18/18 通过（H2 17 + 真实 MySQL 8.4 Testcontainers 1，0 skipped）；前端 UI 契约、Session 导航竞态、全部 JS 语法和 Postman JSON 通过；Docker MySQL/backend/web 健康；真实接口验证多关键词、区域、标签、信用排序和安全用户字段；桌面 1440px、移动 500px 截图通过。

### 第七轮：预览稿结构收敛、角色导航与组合搜索

- 提交：本条记录所在的第七轮功能提交（使用 `git log -1` 查看）。
- 首页不再只复用颜色 token，已按批准的预览稿重建为左侧分类、欢迎搜索、毕业季活动横幅、快捷分类和商品卡片主内容区；移动端折叠侧栏并保留双列商品与底部导航。
- 管理中心增加真实用户/商品/留言/下架数量概览；所有页面的管理入口默认隐藏，仅 `/users/me` 确认当前 Session 为 ADMIN 后显示，后端授权仍是唯一安全边界。
- `api.js` 使用 session generation 防止迟到的旧 `/users/me` 响应覆盖登录或退出后的新状态，并新增可执行 Node 竞态回归测试。
- 公开商品查询统一为可选关键词与分类的交集搜索，关闭原先“界面像组合筛选、后端却忽略分类”的契约缺口。
- 故障复盘：第四轮只验收了视觉 token 与逐页响应式截图，没有对预览稿页面骨架和角色导航建立机器可执行契约；本轮新增 `frontend/tests/ui-design-contract.test.js` 固定八页隐藏规则与首页/后台关键结构。
- 验证：Maven 17/17 通过（H2 16 + 真实 MySQL 8.4 Testcontainers 1，0 skipped）；两项前端 Node 测试、全部 JS 语法和 `git diff --check` 通过；Docker Web 重新部署、首页与 liveness 均为 200。
- 三个低成本 Luna 子 Agent 完成安全、功能和 UI 审查；修复会话竞态与组合筛选后复验无剩余 P0/P1。

### 第六轮：受控商品图片上传与持久存储

- 提交：本条记录所在的第六轮功能提交（使用 `git log -1` 查看）。
- 建立 `ProductImages.store/load` interface 和文件系统 adapter；Docker 使用独立 `media-data` 持久卷，未来可替换 MinIO/S3。
- 学生可在发布页和“我的发布”编辑器中选择、预览、替换或移除图片；桌面与移动共用同一交互。
- 服务端按真实内容只接受 JPG/PNG，限制输入/输出 5MB、1200 万像素和单边 8000px；重新编码清除 EXIF，随机 UUID 文件名避免覆盖。
- 每个学生 100MB 配额按 owner 串行核算，图片解码全局并发限制为 2；商品只能引用当前卖家本人上传的完整受控路径。
- 页面不再加载任意外部图片，CSP 图片来源收紧为同源、data 和 blob；网络失败、格式/大小错误、等待状态均可见并可恢复。
- 验证：Maven 16/16 通过（H2 15 + 真实 MySQL 8.4 Testcontainers 1，0 skipped）；全部 JS、Postman JSON、Compose 和 `git diff --check` 通过。
- Docker 实测 Alice Session + CSRF 上传 390×1478 PNG，服务端标准化为 59,772 bytes并成功公开读取；重启 backend 后仍返回 200，三个服务健康。
- 三个低成本 Luna 子 Agent 完成安全、功能/部署、UI 审查；修复跨 owner 校验、并发配额和失败恢复后，复验无剩余 P0/P1。

### 第五轮：卖家商品管理与 AI 协作记忆

- 提交：本条记录所在的第五轮功能提交（使用 `git log -1` 查看）。
- 新增“我的发布”桌面/移动页面，支持查看全部自有商品、编辑资料、卖家下架和重新上架，并区分交易状态与管理员审核状态。
- 建立 `SellerInventory` 核心 interface；资源归属、有效卖家、可编辑状态、重新上架规则和商品行锁集中在服务端。
- 管理员审核和卖家状态动作使用同一商品锁，避免并发覆盖；`RESERVED`/`SOLD` 仍禁止卖家修改。
- 新增 `AGENTS.md`、`docs/ai/PROJECT_CONTEXT.md` 和本工作日志，规定 AI 阅读顺序、关键 seam、不变量与完成门禁。
- 同步 README、业务词汇、Postman 契约和反向权限测试。
- 验证：Maven 15/15 通过（H2 14 + 真实 MySQL 8.4 Testcontainers 1，0 skipped）；全部 JS 语法、Postman JSON、Compose 配置和 `git diff --check` 通过；Docker MySQL/backend/web 健康；本地页面 200、liveness UP、游客 `/items/mine` 为 401。
- 三个低成本 Luna 子 Agent 分别做安全、功能/数据、UI 审查；发现的 P1 均已收口，无剩余 P0/P1。

### 第四轮：全站桌面/移动布局重构

- 提交：`adf7cf0 feat: rebuild responsive web application layouts`
- 七个 Web 页面重构为桌面与移动双端结构，新增移动底部导航、桌面双栏编辑器、订单流程、认证布局和后台侧栏。
- 逐页浏览器截图验证 1440px 与 390px，无整体横向溢出。
- 13 项测试通过，包含真实 MySQL 8.4 Testcontainers；三方审查无 P0/P1。

### 第三轮：本地部署与品牌视觉落地

- 提交：`bcd0f0d feat: deploy and apply campus market visual system`
- Docker MySQL/后端/Nginx 部署跑通，真实完成登录、下单、接受、完成和管理员权限冒烟测试。
- 航空蓝 + 活力黄品牌视觉、响应式基础、CSP 和安全 seed guard 落地。

### 第二轮：交易 workflow 与全新部署基线

- 提交：`c645495 refactor: deepen trading workflow and fresh deployment baseline`
- 建立商品预留、订单动作、超时释放、订单快照与 Flyway/Spring Session JDBC 基线。

### 第一轮：安全账号与 Session seam

- 提交：`de4d24c refactor: establish secure account and session boundary`
- 移除客户端身份 ID，建立服务端 Session、CSRF、管理员鉴权复核、验证码用途隔离与 authVersion 会话撤销。

### 设计基线

- 提交：`8c8d21a docs: establish refactor baseline and UI direction`
- 确认角色、模块、视觉方向和重构计划。

### 图片上传 HTTP 413 排障（2026-08-28）

- Nginx `/api/` 反向代理补充 `client_max_body_size 6m`，与 Spring 单文件 5MB、请求 6MB 限制保持一致；此前部署入口使用 Nginx 默认约 1MB 限制，较大图片会在后端之前直接返回 HTTP 413。
- 验证：检查 Nginx 配置、Spring multipart 配置与前端上传请求链路；未运行完整 Maven/Docker 验证。提交号：本轮提交后使用 `git log -1` 查看。
- 遗留：需在实际 Docker 部署中重建 web 容器并用接近 5MB 的 JPG/PNG 上传复验。

### 发布页图片移除（2026-08-28）

- 发布页补充“移除图片”按钮，清空文件输入、释放预览 Blob URL 并恢复占位图；提交时继续将 `imageUrl` 发送为 `null`，与“我的发布”编辑器行为一致。
- 验证：前端发布脚本语法检查与 `git diff --check`；未运行完整 Maven/Docker 验证。

### 个人中心退出按钮宽度（2026-08-28）

- 覆盖全局按钮 `width: 100%` 规则，仅将 `.profile-header #logoutBtn` 设为内容宽度，避免退出按钮横向撑满个人中心标题行。
- 验证：`git diff --check`；未运行完整 Maven/Docker 验证。

### 个人中心入口卡片样式（2026-08-28）

- 将“我的发布、我买到的、我卖出的、消息、举报记录”等 `.profile-links` 入口改为独立白色圆角矩形边框，补充间距与悬停边框反馈，与 `.profile-overview.form-panel` 视觉一致。
- 验证：`git diff --check`；未运行完整 Maven/Docker 验证。

### 用例清单合并修订（2026-08-29）

- 根据反馈，将用例清单由 33 条动作级条目进一步合并为 13 条业务目标级场景，避免把登录、商品维护、交易关闭等流程步骤误列为独立用例。
- 验证：`git diff --check`；未运行 Maven/Docker（本轮仅修改文档）。

### 用例清单再次精简（2026-08-29）

- 将 13 条业务目标进一步归并为 8 条核心场景：账号、内容浏览、商品管理、商品沟通、交易闭环、交易记录、学生举报、管理员治理。
- 将安全校验明确为所有场景的共同约束，不再单列安全用例。
- 验证：`git diff --check`；未运行 Maven/Docker（本轮仅修改文档）。

## 接手检查清单

1. `git status --short` 确认没有覆盖其他人的未提交修改。
2. `docker compose ... ps` 确认本地服务状态；当前验收入口为 `http://localhost`。
3. 阅读本文件“当前进行中”与最新提交，不重复已经完成的轮次。
4. 修改后同步测试、Postman（如契约变化）和本日志。
