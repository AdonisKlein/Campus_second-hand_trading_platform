# AI 持续工作日志

本文件记录已经落地的事实，不记录未经确认的设想。每轮完成后在顶部追加结果，包含验证证据和提交号。

## 当前状态

第十三轮及其页面收口已完成：商品详情和订单工作台已经重构，实际业务页面已移除宣传性介绍区块。下一功能轮为第十四轮个人中心完整重设计；第十五轮为私聊页面完整重设计。本次只完成后续 Backlog 与本地部署文档，不提前实施计划功能。

## 已完成轮次

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

## 接手检查清单

1. `git status --short` 确认没有覆盖其他人的未提交修改。
2. `docker compose ... ps` 确认本地服务状态；当前验收入口为 `http://localhost`。
3. 阅读本文件“当前进行中”与最新提交，不重复已经完成的轮次。
4. 修改后同步测试、Postman（如契约变化）和本日志。
