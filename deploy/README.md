# 从空数据库部署

这套方案会启动 MySQL、后端和 Web/Nginx。MySQL 只创建空数据库，后端启动时由 Flyway 自动创建并校验全部表。

1. 安装并启动 Docker Desktop，执行 `docker version`，确认 Client 和 Server 都可用。
2. 复制 `deploy/.env.example` 为 `deploy/.env`，填写数据库密码和 `VERIFICATION_PEPPER`。这些关键值留空时 Compose 会直接拒绝启动，避免误用公开示例密钥。可用 PowerShell 生成随机值：

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```
3. 在项目根目录运行：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

4. 访问 `http://localhost`。查看状态：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f backend
```

健康检查：

```powershell
Invoke-RestMethod http://localhost/api/actuator/health/liveness
```

返回 `status = UP` 即表示后端可用。修改代码后应重新执行 `up -d --build`；只执行 `restart` 不会把源码更新进镜像。

## 本地邮箱验证码：Mailpit

本地开发不需要真实发送公网邮件。项目提供 `docker-compose.mailpit.yml` 作为本地 SMTP adapter：后端仍完整执行生成验证码、SMTP 发送、摘要存储和注册校验，邮件被 Mailpit 截获并显示在本机收件箱。

启动或切换到 Mailpit 模式：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.mailpit.yml up -d --build
```

访问：

```text
平台：http://localhost
Mailpit 收件箱：http://localhost:8025
```

在平台注册页填写任意格式正确且尚未注册的测试邮箱，例如 `student1@example.com`，点击“发送验证码”，再到 Mailpit 打开最新邮件复制 6 位验证码。Mailpit 不会把邮件投递到真实邮箱。

Mailpit Web 端口只绑定 `127.0.0.1`，SMTP 1025 端口只在 Compose 私有网络中使用，没有暴露给宿主机或局域网。邮件最多保留 500 封，并持久化在 `mailpit-data` 本地 volume。

查看开发服务状态：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.mailpit.yml ps
```

停止 Mailpit 模式且保留数据库、图片和测试邮件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.mailpit.yml down
```

切回阿里云/普通 Compose 配置时，使用基础文件重建后端：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build --force-recreate backend
```

Mailpit 仅用于本地开发，不能加入生产 Compose 命令，也不要把 8025 暴露到公网。官方默认端口说明见 [Mailpit Docker 文档](https://mailpit.axllent.org/docs/install/docker/)。

## 阿里云邮件推送

未配置邮件时保持 `MAIL_ENABLED=false`，网站仍可浏览和使用演示账号，但注册与找回密码的验证码接口会返回 503。生产启用步骤：

1. 在阿里云邮件推送的华东 1 区域创建专用发信子域名，例如 `notify.example.com`。
2. 按控制台给出的值，在实际 DNS 托管商添加所有权、SPF、DKIM、DMARC 和 MX 记录，等待全部验证通过。
3. 创建发信地址，例如 `no-reply@notify.example.com`，并为它设置独立 SMTP 密码。
4. 在 `deploy/.env` 填写：

```dotenv
MAIL_ENABLED=true
MAIL_HOST=smtpdm.aliyun.com
MAIL_PORT=465
MAIL_USERNAME=no-reply@notify.example.com
MAIL_PASSWORD=阿里云控制台设置的SMTP密码
MAIL_FROM=no-reply@notify.example.com
MAIL_SMTP_AUTH=true
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
MAIL_STARTTLS_REQUIRED=false
```

`MAIL_USERNAME`、`MAIL_FROM` 必须使用阿里云控制台已创建的同一个发信地址。不要填写阿里云登录密码或 AccessKey。更新后执行：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build --force-recreate backend
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --tail 100 backend
```

如果所在网络无法使用 465，可根据阿里云控制台与官方文档改用端口 80 + STARTTLS：关闭 `MAIL_SSL_ENABLED`，同时开启 `MAIL_STARTTLS_ENABLED` 和 `MAIL_STARTTLS_REQUIRED`。不要使用被云服务器默认限制的 25 端口。

官方配置依据：[阿里云发信域名](https://help.aliyun.com/zh/direct-mail/user-guide/how-to-configure-sending-domain-names)、[SMTP 地址和端口](https://www.alibabacloud.com/help/zh/direct-mail/smtp-endpoints)、[设置 SMTP 密码](https://www.alibabacloud.com/help/zh/direct-mail/sender-address-faqs)。

如需演示账号和商品，可在**全新的空业务库**中从项目根目录导入一次种子数据。脚本检测到已有用户或商品会直接停止，禁止在生产环境执行：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml cp database/seed.sql mysql:/tmp/seed.sql
docker compose --env-file deploy/.env -f deploy/docker-compose.yml exec -T mysql sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/seed.sql'
```

演示账号为 `admin@example.com`、`alice@example.com`、`bob@example.com`，密码统一为 `abc123`，仅供本地开发使用。

正式域名启用 HTTPS 后，把 `PUBLIC_ORIGIN` 改成完整域名，并把 `SESSION_COOKIE_SECURE` 改成 `true`。TLS 可以放在这套 Nginx 前面的 Caddy、Traefik 或云负载均衡器终止。

网站启动健康检查只检查应用存活，不把 SMTP 当作启动门槛；邮件发送状态应单独监控。这样邮箱服务临时故障时，浏览和订单功能仍可使用。

数据库数据位于 Docker volume `mysql-data`，商品图片位于 `media-data`。删除容器不会删除这些数据；只有显式删除对应 volume 才会清空。

日常停止且保留数据：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

不要在仍需保留数据时追加 `--volumes`。本机 MySQL + Maven 调试、完整测试、最小验收和常见故障处理见 `doc/软件部署文档.md`。
