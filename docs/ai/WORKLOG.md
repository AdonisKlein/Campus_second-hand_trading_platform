# AI 持续工作日志

### 公开问答卖家回复（2026-09-01）

- 公开留言接口支持 `replyToId`，仅对应商品发布者可回复，服务端校验问题归属并将回复发送给提问者。
- 商品详情页为发布者展示“回复”操作，回复成功后刷新公开问答列表；普通用户仍只能提问、编辑和删除自己的留言。
- 验证：Marketplace `mvn test -q -DskipTests` 编译通过，`git diff --check` 待复核。
- 遗留：无。

### 重置密码正则提示优化（2026-09-01）

- 重置密码表单补充与后端一致的 6-12 位字母数字校验、长度约束和中文帮助提示。
- 提交前在前端拦截不符合规则的密码，避免直接展示 Spring 原始 `newPassword` 正则错误，并自动聚焦密码输入框。
- 验证：`node --check frontend/assets/js/profile.js`、`git diff --check` 通过。
- 遗留：无。

本文件记录已经落地的事实，不记录未经确认的设想。每轮完成后在顶部追加结果，包含验证证据和提交号。

## 当前状态

### 阿里云共享主机 CD 替换本机 Kind CD（2026-09-03）

- 撤销 `9081863` 前后引入的开发者电脑 self-hosted Runner 自动部署路径，删除 `deploy-local-kind` Job 与专用更新脚本；手动 `kind-local.ps1` 和 GitHub 托管 Runner 临时 Kind 验收保留。
- 新增可显式开关的 `deploy-cloud` Job：只有服务测试、契约/前端、E2E、SHA 镜像和临时 Kind 全绿后，才上传不可变部署 release 并通过固定 host key 的 SSH 发布到阿里云。
- Compose 应用镜像已参数化；生产 Overlay 约束容器内存，Web 只绑定 `127.0.0.1:18080`。部署脚本强制 HTTPS CORS 与 Secure Cookie，检查 readiness/版本，失败时收集证据并尝试恢复上一组应用镜像。
- `WEB_BIND_ADDRESS` 与 `WEB_PORT` 已拆成两个变量；E2E Compose 同步改为回环地址和纯数字端口，避免把旧 `127.0.0.1:18080` 端口值与新绑定地址拼成非法 IP。
- 云端实机准备：`campus.derawaze.top` A 记录已解析到目标服务器；新增 4 GiB `/swapfile` 并持久化，安装 Docker Engine 26.1.3、Compose 2.27.0 与 Certbot 1.22.0。Docker 官方源在该主机发生 TLS 连接失败，备份 repo 后改用已验证可达的阿里云 Docker CE 镜像完成安装。
- 宿主机 Nginx 已备份并加载独立 `campus.derawaze.top` 虚拟主机，博客域名仍返回 200；Let's Encrypt HTTPS 证书已签发并通过公网 TLS 校验，平台未启动时子域名按预期返回 502。生产 `.env` 由服务器本地脚本生成强随机值且权限为 `600`，邮件在 SMTP 凭据配置前保持关闭。
- 创建非 root 的 `campus-deploy` 发布账号并验证其可通过专用无口令密钥登录、运行 Compose 和读取生产配置；GitHub 的 `CLOUD_SSH_KEY` 已替换为该专用密钥，本机临时私钥随后删除。首次错误密钥因生成命令把引号写成了口令而无法用于 BatchMode，已用真实无人值守 SSH 验证锁定并修复。
- 分支首次真实 CD 的全部测试、六个应用镜像和 Kind 部署均通过；云端阶段在 `compose pull` 暴露服务器无法连接 Docker Hub。生产 Overlay 随后改为由 CI 将固定版本 MySQL、Redis、RabbitMQ 原样镜像至 GHCR，使服务器部署只依赖已验证可达的 GHCR，不修改全机镜像源。
- 同一提交重跑后镜像拉取成功，云端冒烟进一步暴露 1.8 GiB 主机全局 OOM：容器 MySQL 退出码 137、`OOMKilled=true`，Trading/Governance 因数据库消失而不健康。课程演示 Overlay 已缩小 JVM 堆/元空间、Hikari 连接池、Tomcat 线程与 MySQL 缓冲，并串行启动后两个 JPA 服务；MySQL 健康检查改走 3306/TCP，防止把初始化临时 socket 误判为正式就绪。
- `sha-8409a09` 首次修复将 Metaspace 误压到 96 MiB，Account 日志明确报 `OutOfMemoryError: Metaspace`；提高后又通过内核 `constraint=CONSTRAINT_NONE, global_oom` 证实五个 JVM 同驻时仍有物理内存启动峰值。最终生产 Overlay 使用 80 MiB Java 堆、112 MiB Metaspace、受限代码缓存/直接内存、Tier 1 JIT、JPA 延迟初始化和 64 MiB MySQL Buffer Pool，从保留数据卷的干净容器状态复验成功。
- 新增 `CLOUD_DEPLOY_REF` 仓库变量作为唯一部署分支选择器，临时验收可只部署 `codex/cloud-server-deployment`，以后切换 `main` 无需改工作流。
- 初始盘点发现 2 vCPU、1.8 GiB RAM、无 Swap、Docker 未安装，Nginx 80 与 MySQL 3306 正在服务博客；上述一次性演示前置项现已补齐。正式发布触发前仅需完成本分支验证提交，并将 `CLOUD_DEPLOY_ENABLED` 打开。
- 验证：生产 Compose 合并配置、工作项 8 静态契约和 `git diff --check` 通过；GitHub run `33754743837` 的五服务测试、契约/前端、Playwright E2E、统一报告、九个 SHA 镜像和 Kind 部署通过，云端失败根因已由实机日志锁定。修正参数后使用同一 `sha-8409a09` 镜像原地复验：九个容器全部 Healthy、五个 Java 容器重启数均为 0，公网 readiness 为 UP、info 返回完整提交号、首页与 `/api/items` 均为 200；Swap 余量约 3.9 GiB，博客虚拟主机仍返回 200。
- 提交号：本轮提交后使用 `git log -1` 查看。

### 文档去重与用例口径收敛（2026-08-31）

- 将旧《测试文档》从重复的测试建设规划压缩为历史索引，明确《软件测试文档》《测试清单》《校园二手交易平台测试文档》《全量测试文档》和《需求追溯矩阵》的唯一职责。
- 将测试策略与清单中的 18 条细粒度检查统一标为 `REQ01～REQ18`，业务场景统一为 `UC01～UC08`；REQ18 作为所有场景的共同安全约束，不再误列为独立业务用例。
- 重写《从顺序图生成测试用例》，由旧 18 套重复展开收敛为转换规则、8 场景映射、交易示例和共同安全约束。
- 同步用户手册、UI 方案、开发计划和模块设计中的用例编号；概要、详细和实现说明书增加职责边界与权威文档引用，并将 Flyway 当前基线修正为 V1～V6。
- 验证：`doc/*.md` 中无旧 `UC09～UC18`、`SYS-SEQ09～SYS-SEQ18` 或 Flyway V1～V5 现行口径残留；`git diff --check` 通过。本轮只修改文档，未运行 Maven、前端或 Docker 测试。
- 提交号：本轮尚未提交。

### 追溯表与需求文档基线同步（2026-08-29）

- 将 `doc/需求追溯矩阵.md` 的用例和系统顺序模型从旧的 `UC01～UC18` / `SYS-SEQ01～SYS-SEQ18` 统一为当前 8 个业务场景 `UC01～UC08` / `SYS-SEQ01～SYS-SEQ08`，并按需求组完成映射。
- 修正《软件需求说明书》中的 Flyway、商品字段和私聊旧描述。
- 清理本轮文档行尾空格和多余 EOF 空行。
- 验证：`git diff --check` 通过；追溯表仅包含 `UC01～UC08` 和 `SYS-SEQ01～SYS-SEQ08`。
- 提交号：本轮尚未提交。

### 测试用例与测试报告整理（2026-08-29）

- 统一《测试清单》与《校园二手交易平台测试文档》的职责：前者用于执行勾选，后者作为正式测试报告，《全量测试文档》保留环境和命令说明。
- 将清单口径同步为当前 8 个业务场景；已完成的 `SecurityApiIT`、交易/私聊/图片单元测试和 Playwright 主旅程不再列为待补齐事项。
- 正式报告补充本轮证据：后端单元 35/35、API/应用 57/57、前端 3/3、Compose E2E 9/9，合计 104 项通过，0 失败，0 跳过；Docker liveness 为 `UP`。
- 修正前端 Session 竞态测试入口文件名为 `frontend/tests/session-navigation-race.test.js`。
- 遗留项：接近 5MB 图片的 Docker 重建上传复验、远程 GitHub Actions 全绿证据和最终 Git 标签。
- 验证：待执行 `node --test frontend/tests/*.test.js`、全部 JS `node --check` 和 `git diff --check`；本轮未修改代码、接口或 Postman 契约。
- 提交号：本轮尚未提交。

### 服务接口清单与数据表归属方案（2026-08-29）

- 新增 `doc/服务接口清单与数据表归属方案.md`，汇总当前 11 组 HTTP 入口、9 个核心业务 interface、Flyway `V1`～`V6` 全部业务/Session 表及其模块 owner。
- 明确外键不等于跨模块写权限；交易状态统一由 `TradingService` 修改，当前身份统一由 `CurrentActorService` 取得，Spring Session 表由 adapter 管理。
- 同步 `docs/ai/PROJECT_CONTEXT.md` 文档入口；未修改用户本轮正在调整的课程文档、图片和 Postman 契约。
- 验证：依据 Controller、Java interface 和 Flyway 迁移逐项核对；待落盘后执行 `git diff --check`。本轮无代码或接口行为变更。
- 提交号：本轮尚未提交。

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

### 微服务工作项 5：Governance Service 与单体运行路径退役（2026-08-28）

- Governance 成为第四个提取的业务服务，独占举报、治理决定、追加式动作审计和本服务 Inbox/Outbox；Gateway 将 `/api/reports/**`、`/api/admin/reports/**` 直达端口 8084，并删除单体兜底及 `MONOLITH_URI`。
- `ContentGovernance` 集中举报快照、24 小时限流、防自举报、防重复、管理员决定和失败重试。举报 `RESOLVED/DISMISSED` 与治理动作 `NONE/PENDING/APPLIED/FAILED` 分开表达，页面不会把“已决定”误报成“远端已执行”。
- Governance 只经 Account/Marketplace 内部 port 获取安全快照；Account 幂等执行用户禁用，Marketplace 幂等执行商品/留言下架。命令与结果通过 Outbox/Inbox 和 correlationId 串联，重复结果被忽略，旧回执不能覆盖新重试。
- 旧 `backend/` 仅作为 `monolith-start` 行为与性能比较基线保留，不再是 Gateway 运行路径；默认 Compose 的四库、Redis、RabbitMQ 和五应用接线将在工作项 6 完成。
- 验证：Gateway 6/6、Account 13/13、Marketplace 19/19、Trading 16/16、Governance 12/12，共 66/66；前端契约 3/3、全部 JS 语法和 `git diff --check` 通过。
- 提交：`refactor: extract governance service and retire monolith`（使用 `git log -1` 查看）。

### 微服务工作项 4：Trading Service 与私聊（2026-08-28）

- Trading 成为第三个提取的业务服务，独占购买意向/订单、私聊会话、消息、屏蔽和本服务 Inbox/Outbox；Gateway 将 `/api/orders/**`、`/api/chat/**` 直接转发端口 8083。
- `TradingWorkflow` 保持闲鱼式流程：买家意向不锁商品，卖家接受后才经 Outbox 请求 Marketplace 预留；取消、完成和超时使用释放/售出 Saga，不做跨库事务。
- Marketplace 以商品悲观锁保证一个商品最多归一个订单预留；命令和结果都以 eventId 幂等，事件包含 correlationId。Broker 暂不可用时未发布 Outbox 保留等待重试。
- `DirectChat` 集中参与者校验、会话复用、sequence 游标分页、双方独立已读游标、未读统计、双向屏蔽和发送限流；只保存跨服务快照，不读取 Account/Marketplace 数据库。
- 修复验收发现的 Pending Saga 被普通过期覆盖风险；Saga 未完成时不会被定时任务改写，第二订单也不能抢占 Marketplace 已有预留。
- 验证：Trading 16/16、Marketplace 17/17 通过；三路低成本子 Agent 做 Saga/并发、安全和 API/边界只读验收，最终无剩余 P0/P1。
- 提交：`refactor: extract trading and direct-chat service`（使用 `git log -1` 查看）。

### 微服务工作项 3：Marketplace Service（2026-08-28）

- Marketplace 成为第二个提取的业务服务，独占商品、标签、受控图片、公开问答和用户公开搜索投影；Gateway 保持浏览器 `/api/items|media|messages|search|admin` 契约并改为直达 Marketplace。
- 核心 interface 为 `CampusSearch`、`ProductDetail`、`SellerInventory`、`ProductImages`、`PublicQuestions`；服务内没有 Account/Trading Repository，跨服务查询集中在 `AccountPublicPort` 与 `TradingInquiryPort`。
- `UserPublicProfileChanged` 消费端按 source version 幂等维护投影，Account 不可用时已有投影仍可搜索；交易 Saga 的 RabbitMQ/Inbox/Outbox adapter 已在工作项 4 加入，容器部署接线仍留到工作项 6。
- 内部 JWT 只由 Gateway 生成，Marketplace 精确公开游客 GET，其他接口要求身份；角色 claim 映射 `ROLE_*`，请求体中的 sellerId/receiverId 不参与身份或资源归属判断。
- 图片 adapter 按真实文件内容识别 JPG/PNG、重新编码清除元数据、限制体积/像素/配额，并只生成 ownerId + UUID 平台路径。
- 验证：Marketplace 14/14、Account 9/9、Gateway 6/6 通过；Flyway/Hibernate 空库校验、JWT API、权限反向测试、游客详情、投影并发乱序、搜索相关度、图片真实内容均有断言。
- 提交：`refactor: extract marketplace service`（使用 `git log -1` 查看）。

### 微服务工作项 2：Gateway 与 Account Service（2026-08-27）

- Gateway 接管浏览器 Redis Session、CSRF、登录/退出、精确 CORS 和身份头清洗；每个受保护请求向 Account 复核 `status`、`role` 与 `authVersion`，再签发 60 秒内部 JWT。
- Account 成为首个真正提取的业务服务，只拥有 `users` 与 `email_verification` 及独立 Flyway V1；注册、验证码、密码重置、资料和管理员账号状态接口不再依赖单体数据库。
- 内部密码验证与安全状态查询由 `AccountClient` 深 interface 隔离：生产使用 300ms 连接/800ms 响应的 HTTP adapter，测试使用 mock adapter；仅安全 GET 重试一次，依赖失败固定返回 503 与 `Retry-After`。
- 浏览器仍只持有 HttpOnly Session Cookie 与 CSRF token；内部 JWT 不暴露给前端。未迁出的业务暂时由 Gateway 转发给单体，单体通过兼容 JWT actor 继续执行原有资源权限规则。
- 验证：Gateway 6/6、Account 8/8、兼容单体单元 30/30 与 API/真实 MySQL 50/50、五个独立工程统一 `mvn verify`、前端契约 3/3 全绿。
- 提交：`refactor: extract account service and gateway authentication`（使用 `git log -1` 查看）。

### 微服务工作项 1：冻结单体行为并建立迁移基线（2026-08-27）

- 单体从 Spring Boot 3.5.14 升级到 4.0.8，并适配 Boot 4 的 WebMVC 测试、Flyway、JDBC Session 和 Jackson 3 模块坐标；浏览器 Session + CSRF 安全边界保持不变。
- 建立 `api-gateway` 与 Account、Marketplace、Trading、Governance 四个独立 Maven/Spring Boot 工程骨架。每个工程可独立编译、测试和生成可执行 JAR；骨架尚不代表业务完成迁移。
- 新增 `contracts/http/public-api-v1.tsv` 冻结 45 个公开 method + path 及未来服务归属，并以 `PublicApiContractIT` 自动比对真实 Controller 映射。
- 新增 `scripts/ci/verify-services.ps1`，逐个验证五个新工程，任一个失败均停止并返回非零。
- 验证：Java 24 兼容覆盖项目声明的 Java 25；单体 `mvn clean -Djava.version=24 verify` 为单元 29/29、API/真实 MySQL 50/50 全绿；五个新工程各 1/1 全绿。
- 提交：`refactor: establish microservice migration baseline`（使用 `git log -1` 查看）。

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

### 微服务工作项 6：四库隔离与完整运行拓扑（2026-08-29）

- 默认 Compose 已切换为 API Gateway、Account、Marketplace、Trading、Governance、MySQL 四库四账号、Redis、RabbitMQ 和 Web；旧 `backend` 不再参与运行。
- 五个 Java 工程分别拥有 Dockerfile，可独立构建；浏览器只访问 Web/Gateway，内部服务保持私网可见。
- `scripts/dev/microservices.ps1` 统一启动、状态、验收和停止操作；验收会检查全部容器健康、四个 Flyway schema、跨库访问拒绝及 Gateway/Web。
- Kubernetes Base 与 CI Overlay 已同步相同拓扑，每个服务有独立 Deployment、Service、Secret 配置和三类探针；本地 Kind 脚本构建并加载六个镜像后执行 rollout 与冒烟。
- Compose 实测全部服务健康，四个账号只能读取自身数据库；Kind 实测全部 Pod 1/1 Running、PVC Bound、同源 liveness 与首页通过。
- 本地 JDK 24 兼容回归入口为 `scripts/ci/verify-services.ps1 -JavaVersion 24`；发布镜像仍使用 Docker 内的 Java 25。
- 下一工作项：补齐微服务 API、事件、基础设施集成和最新版 UC01—UC08 追溯测试；现有旧单体 CI 自动部署脚本将在工作项 8 替换。

### 微服务工作项 7：测试与业务用例闭环（2026-08-31）

- 提交：`7e71804 test: cover microservice contracts and all business journeys`。
- Gateway 与四个业务服务的单元、API、MySQL、Redis、RabbitMQ 测试可独立执行；公开 API 清单共 45 个 method + path，已建立反向权限/参数/成功路径追溯。
- 按最新版业务清单 UC01—UC08 建立机器可读静态映射和 Playwright 运行证据门禁，不再沿用旧 UC01—UC18 编号。
- 最终统一报告 92/92：单元 44、API/集成 33、E2E 15；三轮只读验收无剩余 P0/P1。

### 微服务工作项 8：独立 CI/CD 与可观测性（2026-08-31）

- `.github/workflows/ci.yml` 不再构建退役单体，改为五个服务独立 `mvn verify`、契约/前端门禁、Compose 微服务 E2E、六个 GHCR SHA 镜像、main 分支 Kind 部署和冒烟。
- 新增改动范围分类器与工作项 8 静态契约检查。基础设施、契约或 E2E 变化标记全部服务受影响；当前为保证发布一致性仍执行全量测试和同一 SHA 的整套镜像。
- `scripts/ci/deploy-kind.sh` 改为部署 Gateway、Account、Marketplace、Trading、Governance 和 Web；检查每个 Java 服务的 readiness 与版本信息。
- 失败证据固定包含 Pod/Service/PVC、Events、Pod describe、当前和上一次容器日志、实际部署镜像、健康/版本响应和总结；保留受控失败入口供课程现场演示。
- 五个 Java 服务公开 liveness/readiness/info，镜像含 OCI version/revision，日志为 ECS JSON；Gateway 建立 `X-Correlation-Id`，内部 REST 与交易/治理事件继续传递。治理结果另用 `commandEventId` 匹配原命令，避免把日志追踪号误作业务关联键。
- 新增 5 份 `contracts/events/*.v1.schema.json` 和可执行兼容门禁：v1 必填字段不能删除，消费者允许新增元数据。修复 Account 资料事件新增 `correlationId/producer` 后 Marketplace 消费失败，以及治理回执因关联键复用而长期停在 PENDING 的真实 E2E 故障。
- CI 聚合 Maven 与 Playwright JUnit 为统一 JSON/Markdown 报告；报告、截图、trace、Compose 日志和 Kubernetes 诊断均在失败时上传，任何失败仍以非零退出并阻止镜像/部署。
- 本地验收：五个服务完整 `mvn verify` 全绿（含真实 MySQL 8.4、Redis、RabbitMQ Testcontainers）；前端 3/3；最终 Compose Playwright 15/15、UC01—UC08 运行证据 8/8；事件/CI 静态门禁、Bash 语法和 `git diff --check` 通过。三方只读复验无剩余 P0/P1。
- 提交：`ci: build test and deploy independent microservices`（使用 `git log -1` 查看提交号）。主分支 CI 全绿后再创建 `microservices-end`，本地提交阶段不提前打标记。

### 工作项 8：分支 CI E2E 假失败修复（2026-08-31）

- GitHub Actions 运行 `33369355285` 的五个服务测试和契约门禁均通过，失败集中在 `Run isolated microservice environment`。
- 本地用相同 Compose 命令复现：`私聊→未读→屏蔽` 在详情页用默认 5 秒等待 Session 恢复，冷启动时商品/消息接口单次约 3.3 秒，`/users/me` 尚未完成便被测试取消；trace 证明登录已设置 `SESSION` Cookie，不是会话丢失。
- 私聊旅程总预算调整为 120 秒，两个详情页 Session 断言改为 30 秒分段轮询，仍严格断言买家 ID，不删除安全断言，也不把失败步骤设为可忽略。
- 最小回归 `1/1` 通过（50.9 秒）；完整隔离 Compose 回归 `15/15` 通过（3.6 分钟），UC01—UC08 运行证据 `8/8`。

### 工作项 8：MySQL 8.4.11 初始化失败修复（2026-08-31）

- GitHub Actions 运行 `33373434134` 在隔离微服务环境启动阶段失败；artifact 中 MySQL 的首个错误为 `/usr/local/bin/docker-entrypoint.sh: line 341: MYSQL_ONETIME_PASSWORD: unbound variable`，RabbitMQ 启动日志和后续容器删除只是正常启动与失败清理。
- 根因是 `deploy/mysql/init/01-databases.sh` 会被 MySQL 官方入口脚本 source，脚本内 `set -eu` 将 `nounset` 泄漏到父入口脚本，使官方未配置的可选变量变成致命错误；改为只启用 `errexit`。
- 新增 MySQL sourced-init 安全契约门禁，禁止初始化脚本再次启用 `nounset`；Compose E2E 的阶段记录也改为仅在实际执行阶段时更新，避免 readiness 失败被误记成 database-seed。
- 真实 Docker 复验使用全新 MySQL 8.4.11 数据卷：MySQL、RabbitMQ、Redis、Gateway、四个业务服务和 Web 全部 Healthy，数据库 seed 成功，Playwright `15/15`、UC01—UC08 运行证据 `8/8`，命令退出码为 0。

### 工作项 8：Kind 重叠 rollout 超时修复（2026-09-01）

- main CI `33377973124` 的测试、E2E 和六个镜像构建均通过，Kind 部署连续两次在 Account rollout 报“旧副本待终止”并超时。
- 用相同 Kind v0.32.0、相同 `sha-7777c59` 镜像和独立集群复现；实时资源证明每个 Java 服务同时存在两个同镜像、不同环境版本的运行 ReplicaSet，另有一次 `:dev` 镜像 ReplicaSet，根因是 `apply`、`set image`、`set env` 连续触发三次 Deployment rollout。
- CI Overlay 将六个应用 Deployment 初始副本设为 0；镜像和版本环境一次性配置完后统一扩为 1，基础设施仍正常启动。CI 与 Windows 本地 Kind 脚本共用相同启动约束，并加入静态顺序门禁，避免单节点上重复冷启动 Java Pod导致资源争抢和旧副本终止超时。
- 最终用 Kind v0.32.0/Kubernetes v1.36.1 和原失败版本的六个 `sha-7777c59` GHCR 镜像从空集群复验：先等待 MySQL、Redis、RabbitMQ、Mailpit，再启动六个应用；10 个 Pod 全部 `1/1 Running`、应用无重启，rollout、readiness、版本号与 Web/Gateway 冒烟全部通过，脚本退出码 0。

### 本地运行入口与历史 Kind 集群整理（2026-09-01）

- 检查确认 `campus-ci` 是旧单体 Kind 集群，运行 `campus-backend`、Web、MySQL 和 Mailpit，不是当前 Gateway + 四业务微服务拓扑；确认无业务数据后已永久删除集群及 PVC。
- 重写 `doc/软件部署文档.md`，以 Compose 微服务 + Mailpit 为日常推荐入口，新增独立 `campus-microservices` Kind 启动、访问、暂停、恢复、删除、测试和故障排查步骤；移除旧单体、单库和旧 seed 作为当前启动方式的错误说明。

### 工作项 8：Kind MySQL 临时 socket 初始化修复（2026-09-01）

- main CI `33462175148` 的服务测试、契约测试、Playwright E2E 和六个镜像构建全部通过，Kind 部署中的 Account 因数据库 `1045 Access denied` 进入 CrashLoopBackOff。
- Kubernetes 诊断 artifact 的 MySQL 上一次容器日志证明，自定义初始化脚本默认连接 `/var/run/mysqld/mysqld.sock`，但 Kind 首次初始化临时服务实际监听 `/var/lib/mysql/mysql.sock`；脚本以 `ERROR 2002` 退出并留下未创建四个业务账号的半初始化 PVC。
- Compose 与 Kind 两份初始化脚本统一显式使用 `--protocol=socket --socket=/var/lib/mysql/mysql.sock`；`.gitattributes` 固定所有 Shell 脚本为 LF，避免 Windows 检出产生 `/bin/sh^M`；工作项 8 静态门禁新增 socket 契约。
- 本地使用独立 `campus-socket-fix` Kind 集群从空 PVC 复验：MySQL 和六个应用 Pod 均零重启，全部 rollout 成功，四库 Flyway、跨库权限拒绝、Web/Gateway 冒烟通过；临时集群随后已删除。
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

### 修复验证码服务测试包路径（2026-08-31）

- 将 `VerificationServiceTests` 移至与其 `com.campus.secondhand.user` 包声明一致的测试目录，避免增量/干净构建时 Surefire 找不到 `EmailVerificationRepository`。
- 验证：`mvn test` 通过，35/35；前端 JavaScript 语法与 Node 契约测试此前已通过。
- 遗留：无。

### 本地测试数据导入脚本（2026-09-01）

- 新增 `scripts/dev/demo-seed.sql`：为四库微服务环境导入 1 个管理员、2 个学生用户、3 个在售商品及搜索投影/标签。
- 所有测试账号密码为 `abc123`；脚本仅适用于本地/测试环境，并在账号库或商品库已有业务数据时拒绝执行。
- 验证：`git diff --check`；未运行 Maven/Docker（仅新增导入脚本）。
- 遗留：无。

### 部署文档补充测试数据导入说明（2026-09-01）

- 在 `doc/软件部署文档.md` 末尾补充 PowerShell/Compose 导入命令、`utf8mb4` 字符集要求、乱码重导提示，以及测试账号和商品清单。
- 验证：`git diff --check`；未运行 Maven/Docker（仅修改文档）。
- 遗留：无。

### 同步部署文档测试数据到导入脚本（2026-09-01）

- 根据部署文档最新清单，将导入脚本账号用户名/昵称及 Marketplace 公开投影同步为 `admin`、`alice`、`bob` 与 `Admin`、`小艾`、`小博`。
- 保留文档指定邮箱、商品、价格、标签和测试 ID。
- 验证：`git diff --check`；未运行 Maven/Docker（仅调整测试数据脚本）。
- 遗留：无。

### 登录 403 恢复（2026-09-01）

- 修复 `frontend/assets/js/api.js`：CSRF 获取失败时显式报错；写请求收到网关“请求校验失败”403 时清除缓存 token 并自动重新获取后重试一次，覆盖网关重启、Session/Redis 清理和多实例切换后的旧 token 情况。
- 验证：网关 `mvn test` 通过；待运行前端 Node 契约与 JavaScript 语法检查。
- 遗留：无。

### 演示账号邮箱更新（2026-09-01）

- 更新 `scripts/dev/demo-seed.sql`：仅删除旧演示账号/商品/投影/标签后重建，保留其他业务数据；邮箱改为 `alice@example.com`、`bob@examplee.com`、`admin@example.com`。
- 同步部署文档中的账号清单和脚本清理说明。
- 验证：`git diff --check`；未执行 Docker 导入，避免修改当前本地数据库。
- 遗留：无。

### 微服务公开接口测试闭环（2026-09-03）

- 以 `contracts/http/public-api-v1.tsv` 为唯一公开接口清单，新增 `contracts/testing/public-api-coverage.json`，为全部 45 个 method + path 分别登记成功、备选/参数异常、未登录/越权三类测试证据。
- 新增 `scripts/ci/verify-public-api-coverage.mjs` 并接入 CI；接口增删、证据文件/测试名失效或任一证据类别缺失时，流水线在镜像构建和部署前失败。
- 补齐 Gateway、Account、Marketplace、Trading、Governance 的公开接口测试，覆盖验证码邮件、注册/重置事务结果、资料与账号治理、商品库存和图片、留言管理、订单、私聊及举报治理，并对关键写操作断言身份来源和数据库最终状态。
- 本地完整验证：五个服务独立 `mvn verify` 全绿；45/45 公开接口三类证据门禁通过；UC01—UC08 追溯通过；Compose 微服务 + Mailpit 的 Playwright E2E `15/15` 通过。
- 独立审查后继续补入 Trading/Governance 的公开 HTTP → 真实业务服务 → H2/Flyway 持久化测试，断言订单、举报、治理审计及 Outbox 最终状态；覆盖门禁同时加强为校验 HTTP method、请求调用和可执行断言。复审无未处理 P0/P1。
- 统一报告为单元测试 `44/44`、API/集成测试 `59/59`、E2E `15/15`，总计 `118/118`，失败/错误/跳过均为 0；本地报告目录说明已补入 `scripts/ci/README.md`。
- 提交号：本轮提交后使用 `git log -1` 查看。遗留：公开接口矩阵验证的是测试证据存在性和路由引用，业务断言质量仍需代码审查维护，不能以矩阵替代测试本身。

### 根 README 部署验收信息补齐（2026-09-03）

- 根 README 补齐项目固定/验证环境版本、宿主机与容器端口、`.env` 必填约束、Compose 启停、公开和内部健康检查、Mailpit 注册验证、演示数据导入、测试账号与商品、测试命令和报告目录。
- 部署命令以 `doc/软件部署文档.md`、`deploy/README.md` 和现行 `scripts/dev/microservices.ps1` 为准，明确 Flyway 只建空库、演示数据需要手动导入。
- 验证：README 本地链接检查通过，`git diff --check` 通过；本轮仅修改说明文档，未重新构建容器或运行测试。
- 提交号：本轮提交后使用 `git log -1` 查看。遗留：无。

## 接手检查清单

1. `git status --short` 确认没有覆盖其他人的未提交修改。
2. `docker compose ... ps` 确认本地服务状态；当前验收入口为 `http://localhost`。
3. 阅读本文件“当前进行中”与最新提交，不重复已经完成的轮次。
4. 修改后同步测试、Postman（如契约变化）和本日志。
### 微服务 Maven HTML 测试报告（2026-09-03）

- 将 `maven-surefire-report-plugin 3.5.5` 配置迁移到 Gateway、Account、Marketplace、Trading、Governance 五个微服务；`verify` 阶段同时生成 Surefire/Failsafe HTML 到各服务 `target/reports`。
- CI Maven evidence 同步上传 `target/reports/**`，保留原 XML/TXT、统一 Markdown/JSON 和 Playwright HTML 报告。
- 验证：POM 配置与 CI 路径静态检查；尚未运行完整五服务 Maven/Docker 验证。
- 遗留：执行 `scripts/ci/verify-services.ps1` 后确认五个服务均生成 `target/reports/surefire.html` 与 `target/reports/failsafe.html`（实际文件名以插件输出为准）。
