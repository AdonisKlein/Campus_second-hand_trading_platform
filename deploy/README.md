# 从空数据库部署

这套方案会启动 MySQL、后端和 Web/Nginx。MySQL 只创建空数据库，后端启动时由 Flyway 自动创建并校验全部表。

1. 安装并启动 Docker Desktop，执行 `docker version`，确认 Client 和 Server 都可用。
2. 复制 `deploy/.env.example` 为 `deploy/.env`，填写密码和 `VERIFICATION_PEPPER`。这些关键值留空时 Compose 会直接拒绝启动，避免误用公开示例密钥。可用 PowerShell 生成随机值：

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
