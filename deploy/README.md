# 从空数据库部署

这套方案会启动 MySQL、后端和 Web/Nginx。MySQL 只创建空数据库，后端启动时由 Flyway 自动创建并校验全部表。

1. 安装 Docker Desktop。
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

正式域名启用 HTTPS 后，把 `PUBLIC_ORIGIN` 改成完整域名，并把 `SESSION_COOKIE_SECURE` 改成 `true`。TLS 可以放在这套 Nginx 前面的 Caddy、Traefik 或云负载均衡器终止。

网站启动健康检查只检查应用存活，不把 SMTP 当作启动门槛；邮件发送状态应单独监控。这样邮箱服务临时故障时，浏览和订单功能仍可使用。

数据库数据位于 Docker volume `mysql-data`。删除容器不会删除数据；只有显式删除该 volume 才会清空数据库。
