# AI 持续工作日志

本文件记录已经落地的事实，不记录未经确认的设想。每轮完成后在顶部追加结果，包含验证证据和提交号。

## 当前状态

第五轮已完成，工作区应以本轮提交为基线。下一轮开始前先结合产品优先级选择图片上传、举报治理或原生客户端认证中的一个闭环，不要同时铺开。

## 已完成轮次

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
