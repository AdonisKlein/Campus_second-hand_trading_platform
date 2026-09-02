# AI 开发协作说明

本文件是所有 AI/自动化开发者进入仓库后的第一入口。开始工作前依次阅读：

1. `README.md`：运行、部署和技术栈。
2. `CONTEXT.md`：已确认的业务词汇，避免重新发明术语。
3. `docs/ai/PROJECT_CONTEXT.md`：代码地图、关键 interface 和安全不变量。
4. `docs/ai/WORKLOG.md`：已经完成的轮次、当前工作和后续事项。
5. `docs/roadmap/2026-08-microservices-migration.md`：微服务工作项 1—11、数据归属和实验路线。

## 工作规则

- 不提交 `deploy/.env`、真实密码、验证码 pepper、SMTP 密钥或任何本地凭据。
- Web 身份只相信服务端 Session；不得重新从 localStorage、请求体 userId/adminId 推导身份。
- 所有 Web 写请求必须保留 CSRF；前端统一通过 `frontend/assets/js/api.js` 的 `request()` 调用。
- 商品、订单、留言和管理员写操作必须从 `CurrentActorService` 取得当前用户，并在后端检查资源归属。
- 交易状态修改统一经过 `TradingService`；不要在 Controller 或前端复制交易规则。
- 每个业务轮次结束时更新 `docs/ai/WORKLOG.md`，写清完成内容、验证证据、提交号和遗留项。
- 修改 interface 时同步更新 `docs/ai/PROJECT_CONTEXT.md`、Postman 契约和相关测试。
- 独立 review、静态检查等边界清晰的子任务默认使用低成本/低 token 模型；只有复杂架构或疑难故障再升级模型。


## 常用验证

```powershell
# 五个微服务独立验证；项目目标为 JDK 25
powershell -ExecutionPolicy Bypass -File scripts/ci/verify-services.ps1

# JavaScript 语法
Get-ChildItem frontend/assets/js -Filter *.js | ForEach-Object { node --check $_.FullName }

# Docker 本地部署
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps

# Kind 微服务部署与状态
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 up
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 status
```

测试时 Docker 可用会启动真实 `mysql:8.4` Testcontainers，执行 Flyway 空库迁移与 Hibernate validate。不要用 Hibernate 自动建表替代迁移。

## 完成定义

- 业务规则有后端测试，权限测试必须包含反向用例。
- 桌面端和 390px 移动端无整体横向溢出，主要状态均有可见反馈。
- `git diff --check`、全部 JS `node --check`、Maven 测试通过。
- 独立审查没有未处理的 P0/P1。
- 工作日志已更新，工作区只包含本轮预期文件。
