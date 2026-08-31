# 微服务本地部署

当前 Compose 拓扑包含一个 MySQL 8.4 服务器、四个独立数据库和账号、Redis、RabbitMQ、API Gateway、四个业务服务与 Nginx Web。只有 Web 暴露宿主机端口，浏览器始终通过 Gateway 访问业务接口。

## 一键启动

1. 启动 Docker Desktop，确认 `docker version` 同时显示 Client 和 Server。
2. 从项目根目录复制配置：

```powershell
Copy-Item deploy/.env.example deploy/.env
```

3. 为所有空值填写随机本地密码。`VERIFICATION_PEPPER`、`INTERNAL_SERVICE_TOKEN` 和 `INTERNAL_JWT_SECRET` 至少 32 个字符。可重复执行以下表达式生成不同值：

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

4. 启动包含 Mailpit 的完整开发环境：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action up -Mailpit
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action verify -Mailpit
```

访问地址：

- 平台：`http://localhost`，若修改了 `WEB_PORT` 则使用对应端口。
- Mailpit：`http://localhost:8025`。
- Gateway 存活检查：`http://localhost/api/actuator/health/liveness`。

`verify` 不只是查看容器有没有启动，还会检查所有容器健康、四个服务的 Flyway 表、四个数据库账号的跨库访问被拒绝、Gateway 和 Web 可访问。修改源码后重新执行 `up`；单纯 `restart` 不会把源码重新构建进镜像。

## 状态、日志和停止

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action status -Mailpit
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.mailpit.yml logs -f api-gateway account-service marketplace-service trading-service governance-service
powershell -ExecutionPolicy Bypass -File scripts/dev/microservices.ps1 -Action down -Mailpit
```

停止不会删除命名卷。MySQL、Redis、RabbitMQ、商品图片和 Mailpit 邮件数据会保留。不要在仍需保留数据时使用 `down --volumes`。

不需要邮件测试时去掉所有命令中的 `-Mailpit`。直接使用 Compose 的等价命令是：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build --remove-orphans
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

## 本地注册与重置密码

Mailpit 是本地 SMTP adapter，不会把邮件发到公网。启动 `-Mailpit` 环境后，在注册页填写任意格式正确且未注册的测试邮箱，发送验证码，再到 `http://localhost:8025` 打开最新邮件并复制 6 位验证码。注册、重置密码与生产环境走同一套验证码生成、摘要存储和消费逻辑，只替换邮件投递 adapter。

旧单体的 `database/seed.sql` 与四库微服务结构不兼容，不要导入当前微服务数据库。开发账号应通过正常注册流程创建；管理员账号初始化将在后续运维工作项提供专用、不可误用于生产的命令。

## 生产邮件

未配置邮件时保持 `MAIL_ENABLED=false`，账号服务仍能启动，但发送验证码会返回 503。使用阿里云邮件推送时：

1. 创建专用发信子域名并按控制台完成 SPF、DKIM、DMARC 和 MX 验证。
2. 创建发信地址与独立 SMTP 密码。
3. 在未提交到 Git 的 `deploy/.env` 中配置：

```dotenv
MAIL_ENABLED=true
MAIL_HOST=smtpdm.aliyun.com
MAIL_PORT=465
MAIL_USERNAME=no-reply@notify.example.com
MAIL_PASSWORD=阿里云SMTP密码
MAIL_FROM=no-reply@notify.example.com
MAIL_SMTP_AUTH=true
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
MAIL_STARTTLS_REQUIRED=false
```

重建账号服务和 Gateway 后查看日志：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build --force-recreate account-service api-gateway
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --tail 100 account-service
```

生产域名启用 HTTPS 后，把 `CORS_ORIGINS` 设置为完整站点 origin，并将 `SESSION_COOKIE_SECURE=true`。SMTP 健康状态不作为整个平台启动门槛，邮件临时故障不会连带阻止商品和交易服务启动。

## 数据与安全边界

- `account_user` 只能访问 `campus_account`。
- `marketplace_user` 只能访问 `campus_marketplace`。
- `trading_user` 只能访问 `campus_trading`。
- `governance_user` 只能访问 `campus_governance`。
- 跨服务查询走内部 REST，状态变更走 RabbitMQ Inbox/Outbox；禁止跨库联表。
- Redis 保存 Gateway Web Session，内部 JWT 不返回浏览器。

MySQL 初始化脚本只在全新数据卷第一次启动时运行。如果修改了数据库初始化账号且本地数据无需保留，应明确删除 `campus-microservices-mysql-data` 后重建；这会永久清除本地微服务数据，执行前自行确认。
