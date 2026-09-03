# 阿里云共享主机部署

本方案将校园二手平台部署到 `campus.derawaze.top`，保留主域名 `derawaze.top` 和 `www.derawaze.top` 的博客。宿主机 Nginx 继续占用 80/443，平台容器只监听 `127.0.0.1:18080`，数据库、Redis、RabbitMQ 和五个 Java 服务均不向公网映射端口。

## 1. 当前主机前置检查

2026-09-03 的只读盘点结果为：Alibaba Cloud Linux 3、2 vCPU、1.8 GiB RAM、无 Swap、31 GiB 可用磁盘；Nginx 1.24 正在提供博客，宿主机 MySQL 8.0 正在使用 3306，Docker 尚未安装。

完整微服务拓扑不应直接运行在 1.8 GiB RAM 上。首次部署前建议把实例升级到至少 4 GiB RAM（长期运行建议 8 GiB），并配置 2～4 GiB Swap 作为 OOM 缓冲。停止静态博客不能释放足够内存，因此不是内存问题的解决办法。

安装 Docker Engine 和 Compose v2 时使用 Docker 官方 RPM 仓库，不使用来源不明的一键脚本：

```bash
dnf -y install dnf-plugins-core
dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
docker version
docker compose version
```

官方参考：https://docs.docker.com/engine/install/centos/

## 2. DNS、Nginx 和 HTTPS

1. 在阿里云 DNS 为 `campus.derawaze.top` 新增 A 记录，值为 `123.56.8.200`。当前该记录尚不存在。
2. 服务器当前 `/etc/nginx/nginx.conf` 没有加载 `conf.d`。先备份，再在 `http { ... }` 内加入：

```nginx
include /etc/nginx/conf.d/*.conf;
```

3. 在本地项目根目录上传 Nginx 配置和生产环境模板：

```powershell
scp -i D:\A1.pem deploy/cloud/nginx-campus.conf root@123.56.8.200:/tmp/campus.derawaze.top.conf
scp -i D:\A1.pem deploy/cloud/.env.production.example root@123.56.8.200:/tmp/campus.env
```

再在服务器安装 Nginx 配置，并且只在配置测试成功时重载：

```bash
cp -a /etc/nginx/nginx.conf /etc/nginx/nginx.conf.before-campus
install -d /etc/nginx/conf.d
install -m 644 /tmp/campus.derawaze.top.conf /etc/nginx/conf.d/campus.derawaze.top.conf
nginx -t && systemctl reload nginx
```

4. DNS 生效后使用 Certbot 或阿里云证书为子域名签发证书，并让 Nginx 提供 HTTPS。HTTPS 可用前不要启用生产注册登录，因为生产 Cookie 强制 `Secure`。

Nginx 配置按 `server_name` 分流，不修改博客根目录，也不占用新的公网端口。若配置测试失败，删除新增文件并恢复备份，博客即可回到原配置。

## 3. 生产环境文件

创建只允许 root 访问的共享目录：

```bash
install -d -m 700 /opt/campus-market/shared /opt/campus-market/state /opt/campus-market/evidence
install -m 600 /tmp/campus.env /opt/campus-market/shared/.env
```

编辑 `/opt/campus-market/shared/.env`，填写全部密码、Pepper、内部 Token 和阿里云 SMTP 凭据。每个密码使用不同随机值；三个内部安全值至少 32 字符。必须保留：

```dotenv
CORS_ORIGINS=https://campus.derawaze.top
SESSION_COOKIE_SECURE=true
MAIL_ENABLED=true
```

这个文件永远不由 CI 上传或覆盖。宿主机已有 MySQL 不会冲突，因为平台 MySQL 仅位于 Compose 私有网络，未映射 3306。

## 4. GitHub production 环境

在仓库 Settings → Environments 创建 `production`，再配置：

仓库 Variables：

| 名称 | 值 |
| --- | --- |
| `CLOUD_DEPLOY_ENABLED` | 准备完成前为 `false`，准备完成后改为 `true` |
| `CLOUD_HOST` | `123.56.8.200` |
| `CLOUD_USER` | `root` |
| `CLOUD_DEPLOY_PATH` | `/opt/campus-market` |
| `CLOUD_DOMAIN` | `campus.derawaze.top` |

`production` Environment Secrets：

| 名称 | 内容 |
| --- | --- |
| `CLOUD_SSH_KEY` | `D:\A1.pem` 的完整内容，不是文件路径 |
| `CLOUD_KNOWN_HOSTS` | 经人工核对指纹后的 `123.56.8.200` SSH host key 行 |

2026-09-03 已从服务器本机公钥与客户端扫描两侧核对 ED25519 指纹，二者均为 `SHA256:0qb2+tXkZMCWBQLvpEPqvv0rAYUFGbI0UHIxBCwMxII`。若以后重装服务器或 SSH host key 发生变化，必须重新从可信控制台核对，不能为了让流水线通过而关闭 host key 检查。

私钥不能复制到仓库或服务器发布目录。建议后续创建仅能执行部署的专用 Linux 用户和独立部署密钥，再替换当前 root 密钥。

## 5. 自动发布流程

`CLOUD_DEPLOY_ENABLED=true` 后，每次推送 `main` 会依次执行：

1. 五个服务测试、契约/前端测试和 Playwright E2E。
2. 构建并推送六个 `sha-xxxxxxx` 镜像。
3. 在 GitHub 临时 Kind 集群完成一次完整部署与健康检查。
4. 上传只包含 Compose 配置和部署脚本的不可变 release。
5. 云主机匿名拉取公开 GHCR 镜像，以 `--no-build` 启动。
6. 检查容器健康、Gateway readiness 和提交版本号。
7. 失败时保存 Compose 日志、容器资源和宿主机内存/磁盘证据；已有成功版本时尝试回滚应用镜像。

流水线不会停止、覆盖或重新配置博客，也不会上传 `.env`。发布证据保存在服务器 `/opt/campus-market/evidence/<sha-tag>/`，同时作为 GitHub Actions artifact 上传。

## 6. 手动验收与停止

```bash
curl -fsS https://campus.derawaze.top/index.html >/dev/null
curl -fsS https://campus.derawaze.top/api/actuator/health/readiness
curl -fsS https://campus.derawaze.top/api/actuator/info

cd /opt/campus-market/current
IMAGE_NAMESPACE=ghcr.io/adonisklein IMAGE_TAG="$(cat /opt/campus-market/state/current-image-tag)" \
WEB_BIND_ADDRESS=127.0.0.1 WEB_PORT=18080 \
docker compose --project-name campus-market \
  --env-file /opt/campus-market/shared/.env \
  -f deploy/docker-compose.yml -f deploy/docker-compose.production.yml ps
```

临时停止平台而不删除数据卷：

```bash
cd /opt/campus-market/current
IMAGE_NAMESPACE=ghcr.io/adonisklein IMAGE_TAG="$(cat /opt/campus-market/state/current-image-tag)" \
WEB_BIND_ADDRESS=127.0.0.1 WEB_PORT=18080 \
docker compose --project-name campus-market \
  --env-file /opt/campus-market/shared/.env \
  -f deploy/docker-compose.yml -f deploy/docker-compose.production.yml stop
```

不要使用 `down --volumes`，否则会删除平台数据库、Redis、RabbitMQ 和商品图片数据。
