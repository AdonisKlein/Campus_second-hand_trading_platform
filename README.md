# 校园二手交易平台

面向在校学生的二手商品发布、沟通和当面交易平台。目前处于企业化重构阶段。

## 当前能力

- 游客浏览、使用多关键词搜索商品或用户，并在搜索后按活跃、同区域、信用、价格、校园区域和商品标签筛选排序。
- 邮箱验证码注册、邮箱密码登录、找回密码。
- 支持阿里云邮件推送 SMTP：显式发件地址、465 隐式 TLS 或 80 STARTTLS、连接超时和未启用时的安全失败。
- 服务端 Session 登录，支持 CSRF、精确 CORS 和管理员权限复核。
- 学生发布带校园区域和交易标签的商品、管理自己的发布、留言、下单和查看自己的订单。
- 买家可从商品详情发起仅买卖双方可见的私聊；支持未读消息、历史分页、屏蔽与举报，公开留言和私聊严格分开。
- 商品图片由平台受控上传，限制格式、体积和尺寸，并清除照片元数据。
- 买家先提交非独占购买意向，商品继续在售；卖家选定一位买家后才预留，其他意向关闭；当面交接后由买家确认完成。
- 商品详情一次展示安全卖家摘要、当前用户的购买意向、可执行动作、同卖家在售商品、公开问答与校园交易提醒；桌面和移动使用各自适配布局。
- 订单工作台按“我买到的 / 我卖出的”和交易阶段组织记录；卖家按商品比较买家公开信用，买家查看时间线、关闭原因并可从订单继续私聊。
- 订单保存下单时的商品标题、价格和双方昵称，不受后续资料修改影响。
- 学生可举报商品、留言或用户并查看处理结果；管理员通过举报队列执行下架、移除、禁用或驳回并保留审计记录。
- 管理员管理用户、商品和留言。
- Flyway 从空数据库建立并校验表结构。
- Gateway 使用 Redis 保存 Web Session，多个 Gateway 实例可共享登录状态。

## 登录方案

Web 端使用 HttpOnly Cookie + 服务端 Session，浏览器 JavaScript 不能读取登录凭据，并且服务器可以立即注销、封禁或撤销全部旧会话。

Windows 和移动端后续会增加短期访问令牌与可撤销刷新令牌，不把长期 JWT 存入 Web 的 localStorage。两种客户端共享用户、角色和会话撤销规则。

## 环境与固定版本

日常启动推荐使用 Docker Compose。采用这种方式时，宿主机不需要安装 JDK、Maven、MySQL、Redis 或 RabbitMQ。

| 组件 | 项目使用或验证版本 | 用途 |
| --- | --- | --- |
| Java | 25 | 五个后端工程的编译与运行版本 |
| Spring Boot | 4.0.8 | Gateway 和四个业务服务 |
| Maven | 3.9.11（Docker 构建镜像）/ 本地建议 3.9.x | 后端构建与测试 |
| Node.js | 22（CI） | 前端静态检查、报告脚本和 Playwright |
| Playwright | 1.62.1 | 浏览器端到端测试 |
| MySQL | 8.4 | 四个独立业务数据库，使用 Flyway 迁移 |
| Redis | 7.4 | Gateway Web Session |
| RabbitMQ | 4.1 | Inbox/Outbox 跨服务事件 |
| Nginx | 1.28 | Web 静态资源和同源 `/api` 代理 |
| Kind | 0.32.0（CI） | 本地/CI Kubernetes 验收 |

Docker Desktop 的具体补丁版本不锁死，但必须能执行 `docker compose`（Compose v2 命令形式）。运行前确认：

```powershell
docker version
docker compose version
```

需要在宿主机运行 Maven 测试时，再安装 JDK 25 和 Maven 3.9.x。Windows 安装 JDK：

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK --exact
java -version
mvn -version
```

`java -version` 和 `mvn -version` 都必须显示 Java 25。若要在 IDE 单独调试服务，参考 [services/README.md](services/README.md)，每个服务只能使用自己数据库的账号。

## 端口与访问入口

Compose 默认只把 Web 和可选的 Mailpit Web 界面暴露给宿主机。其余端口仅供 Compose 内部网络使用。

| 组件 | 容器端口 | 宿主机入口 |
| --- | ---: | --- |
| Web / Nginx | 80 | `http://localhost`；由 `WEB_PORT` 修改 |
| Mailpit Web | 8025 | `http://localhost:8025`，仅使用 `-Mailpit` 时存在 |
| Mailpit SMTP | 1025 | 不对宿主机暴露 |
| API Gateway | 8080 | 不直接暴露，经 Web 的 `/api` 访问 |
| Account Service | 8081 | 不直接暴露 |
| Marketplace Service | 8082 | 不直接暴露 |
| Trading Service | 8083 | 不直接暴露 |
| Governance Service | 8084 | 不直接暴露 |
| MySQL | 3306 | 不直接暴露 |
| Redis | 6379 | 不直接暴露 |
| RabbitMQ | 5672、15672 | 不直接暴露 |

例如将 `deploy/.env` 中的 `WEB_PORT` 改成 `8088` 后，平台入口和健康检查地址都要改用 `http://localhost:8088`。

## Docker Compose 启动

下面是从空环境启动并验收的完整最短流程。更多说明和故障排查见 [软件部署文档](doc/软件部署文档.md)，Compose 配置说明见 [deploy/README.md](deploy/README.md)。

1. 启动 Docker Desktop，在项目根目录复制环境文件：

```powershell
Copy-Item deploy/.env.example deploy/.env
```

2. 编辑 `deploy/.env`，不能直接保留示例文件中的空值。以下字段必须填写：

```text
MYSQL_ROOT_PASSWORD
ACCOUNT_DB_PASSWORD
MARKETPLACE_DB_PASSWORD
TRADING_DB_PASSWORD
GOVERNANCE_DB_PASSWORD
REDIS_PASSWORD
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
VERIFICATION_PEPPER
INTERNAL_SERVICE_TOKEN
INTERNAL_JWT_SECRET
```

值只能包含字母、数字、下划线和连字符；`VERIFICATION_PEPPER`、`INTERNAL_SERVICE_TOKEN`、`INTERNAL_JWT_SECRET` 至少 32 个字符。可以多次运行下面的命令，为不同字段生成不同的本地随机值：

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

`deploy/.env` 已被 Git 忽略，禁止提交真实密码或密钥。本地邮件测试保持 `MAIL_ENABLED=false`，启动时由 Mailpit adapter 覆盖邮件配置。

3. 构建并启动完整微服务环境，然后执行自动验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action up -Mailpit
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action verify -Mailpit
```

首次构建需要下载镜像和 Maven 依赖。`verify` 会检查全部容器健康状态、四套 Flyway 迁移、数据库账号不能跨库访问、Gateway 存活状态和 Web 页面。

4. 查看状态、重新构建或停止：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action status -Mailpit
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action up -Mailpit
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action down -Mailpit
```

修改源码后重新执行 `up` 才会重建镜像。`down` 会停止并删除容器，但保留 MySQL、Redis、RabbitMQ 和图片卷中的数据。

## 健康检查

默认 `WEB_PORT=80` 时：

| 检查内容 | 地址 | 正常结果 |
| --- | --- | --- |
| Web 首页 | `http://localhost/index.html` | HTTP 200 |
| Gateway 存活 | `http://localhost/api/actuator/health/liveness` | HTTP 200，`status` 为 `UP` |
| Gateway 就绪 | `http://localhost/api/actuator/health/readiness` | HTTP 200，`status` 为 `UP` |
| Mailpit | `http://localhost:8025` | 能打开邮件列表 |

PowerShell 可以直接检查：

```powershell
Invoke-RestMethod http://localhost/api/actuator/health/liveness
Invoke-RestMethod http://localhost/api/actuator/health/readiness
```

Account、Marketplace、Trading、Governance 的 readiness 地址分别是容器网络中的 `http://account-service:8081/actuator/health/readiness`、`http://marketplace-service:8082/actuator/health/readiness`、`http://trading-service:8083/actuator/health/readiness` 和 `http://governance-service:8084/actuator/health/readiness`，不会直接映射到宿主机。

## 本地邮箱验证

使用 `-Mailpit` 启动后，在注册页填写任意格式正确且尚未注册的邮箱并发送验证码，然后打开 `http://localhost:8025`，从最新邮件中复制 6 位验证码。Mailpit 不会把邮件发送到公网。

## 初始数据和测试账号

Flyway 会自动建立空库结构，但 Compose 不会自动创建演示账号或商品。服务全部健康后，在项目根目录手动导入仅供开发/测试使用的数据：

```powershell
Get-Content .\scripts\dev\demo-seed.sql -Raw |
  docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml exec -T mysql `
  sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

脚本只删除并重建自己的账号 ID `1001`～`1003` 和商品 ID `2001`～`2003`，不会清空其他业务数据。所有演示账号密码均为 `abc123`：

| 角色 | 用户名 | 登录邮箱 | 昵称 | 校园区域 | 信用分 |
| --- | --- | --- | --- | --- | ---: |
| 管理员 | `admin` | `admin@example.com` | Admin | 学院路校区 | 100 |
| 学生 | `alice` | `alice@example.com` | 小艾 | 学院路校区 | 108 |
| 学生 | `bob` | `bob@examplee.com` | 小博 | 沙河校区 | 102 |

导入的商品：

| ID | 商品 | 价格 | 卖家 | 区域 | 标签 |
| ---: | --- | ---: | --- | --- | --- |
| 2001 | 高等数学教材（测试） | ¥18.00 | 小艾 | 学院路校区 | 可小刀、仅自提 |
| 2002 | 宿舍小台灯（测试） | ¥25.00 | 小博 | 沙河校区 | 支持验货、九成新 |
| 2003 | 二手蓝牙耳机（测试） | ¥68.00 | 小艾 | 学院路校区 | 可小刀、支持验货 |

三个商品初始状态均为 `ON_SALE`、`VISIBLE`，且没有商品图片。也可以不导入演示数据，直接通过 Mailpit 完成新用户邮箱注册。

## 测试与报告

运行 Gateway 和四个业务服务的单元、API 和 Testcontainers 集成测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/verify-services.ps1
```

快速测试使用 H2；Docker 可用时还会启动真实 MySQL 8.4、Redis 和 RabbitMQ Testcontainers。Flyway 从空库迁移，Hibernate 只执行 `validate`，不会自动建表掩盖迁移错误。

运行微服务 Compose + Mailpit 的 Playwright 端到端回归：

```powershell
Set-Location e2e
npm ci
npx playwright install chromium
npm run test:e2e:compose
Set-Location ..
```

生成统一统计报告：

```powershell
node scripts/ci/test-report.mjs --output test-results/summary
```

本地报告位置：

- 单元/API：`services/<服务名>/target/surefire-reports/`
- MySQL、Redis、RabbitMQ 集成测试：`services/<服务名>/target/failsafe-reports/`
- Playwright HTML：`e2e/test-results/playwright-report/index.html`
- 全部测试汇总：`test-results/summary/test-report.md` 和 `test-report.json`

## 可选：本地 Kind 验收

安装 Docker Desktop、Kind 和 kubectl 后执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 up
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 status
```

使用 `scripts/ci/kind-local.ps1 forward-web` 和 `forward-mail` 分别把 Web、Mailpit 临时转发到 `http://127.0.0.1:18080`、`http://127.0.0.1:18025`。完成后使用 `scripts/ci/kind-local.ps1 down` 删除本地集群。详细说明见 [k8s/README.md](k8s/README.md)。

## 主要目录

```text
services/api-gateway/                 Web 会话、安全与公开路由
services/account-service/             账号业务与独立 Flyway
services/marketplace-service/         商品业务与独立 Flyway
services/trading-service/             交易/私聊业务与独立 Flyway
services/governance-service/          举报治理业务与独立 Flyway
backend/                              不再运行的单体行为基线
frontend/                             Web 页面、Nginx 配置与 Dockerfile
deploy/                               Docker Compose 微服务部署方案
k8s/                                  Kubernetes Base 与 Kind Overlay
doc/                                  产品、设计和测试文档
CONTEXT.md                            已确认的业务词汇
AGENTS.md                             AI/自动化开发者入口与完成定义
docs/ai/                              AI 项目上下文和持续工作日志
```

AI 或新协作者请从 [AGENTS.md](AGENTS.md) 开始阅读。
