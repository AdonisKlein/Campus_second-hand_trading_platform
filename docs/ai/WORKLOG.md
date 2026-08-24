# AI 持续工作日志

本文件记录已经落地的事实，不记录未经确认的设想。每轮完成后在顶部追加结果，包含验证证据和提交号。

## 当前状态

第九轮已完成：举报与内容治理闭环已部署到本地 Docker。下一轮可继续完善信用分规则与治理申诉，或开始原生客户端认证 adapter 的技术验证。

## 已完成轮次

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
