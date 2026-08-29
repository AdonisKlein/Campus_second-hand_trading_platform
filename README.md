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

## 技术栈

- 前端：HTML、CSS、JavaScript，Nginx 同源代理 `/api`
- 后端：Java 25、Spring Boot 4.0.8；API Gateway + Account、Marketplace、Trading、Governance 四个业务服务
- 数据库：MySQL 8.4 四个独立数据库和最小权限账号、Flyway
- 会话与消息：Gateway Redis Session、RabbitMQ Inbox/Outbox 事件
- 测试：JUnit 5、MockMvc、H2、Testcontainers；每个服务可独立 `mvn verify`

## 本地开发

先安装 JDK 25。Windows 可在管理员 PowerShell 中运行：

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK --exact
```

安装后重新打开终端，用 `java -version` 和 `mvn -version` 确认二者都指向 Java 25。若只使用下面的 Docker 部署，则不需要在宿主机单独安装 JDK。

推荐直接使用下面的 Compose 微服务环境。若要在 IDE 单独调试某个服务，请参考 [services/README.md](services/README.md) 设置该服务自己的数据库、内部凭据和依赖 URI；不要让服务使用其他服务的数据库账号。

## 一键部署

完整的全新数据库 Docker 部署说明见 [deploy/README.md](deploy/README.md)，本机开发、测试和排障见 [软件部署文档](doc/软件部署文档.md)：

```powershell
Copy-Item deploy/.env.example deploy/.env
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action up -Mailpit
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action verify -Mailpit
```

本地测试邮箱验证码可叠加 Mailpit：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action up -Mailpit
```

随后在 `http://localhost:8025` 查看测试验证码邮件；Mailpit 不会向公网发送邮件。

## 测试

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/verify-services.ps1
```

快速测试使用 H2；Docker 可用时还会启动真正的 MySQL 8.4，验证空库迁移、Hibernate 映射和 JDBC Session 表。Hibernate 只负责 `validate`，不会用自动建表掩盖迁移错误。

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
