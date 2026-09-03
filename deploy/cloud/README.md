# 阿里云共享主机部署

本方案将校园二手平台部署到 `campus.derawaze.top`，保留主域名 `derawaze.top` 和 `www.derawaze.top` 的博客。宿主机 Nginx 继续占用 80/443，平台容器只监听 `127.0.0.1:18080`，数据库、Redis、RabbitMQ 和五个 Java 服务均不向公网映射端口。

## 1. 当前主机前置检查

2026-09-03 的实机状态为：Alibaba Cloud Linux 3、2 vCPU、1.8 GiB RAM、4 GiB Swap、约 31 GiB 可用磁盘；Nginx 1.24 继续提供博客，宿主机 MySQL 8.0 继续使用 3306。Docker Engine 26.1.3、Compose 2.27.0 与 Certbot 1.22.0 已安装。

完整微服务拓扑不适合长期运行在 1.8 GiB RAM 上。本次课程演示已增加 4 GiB Swap 作为 OOM 缓冲：

```bash
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -Fqx '/swapfile none swap sw 0 0' /etc/fstab || \
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
swapon --show
```

Swap 不是内存扩容的替代品，只用于降低一次演示过程中因瞬时内存峰值触发 OOM 的概率。正式长期运行应升级到至少 4 GiB RAM，建议 8 GiB。

安装 Docker Engine 和 Compose v2 时优先使用 Docker 官方 RPM 仓库，不使用来源不明的一键脚本：

```bash
dnf -y install dnf-plugins-core
dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
docker version
docker compose version
```

官方参考：https://docs.docker.com/engine/install/centos/

该服务器访问 Docker 官方仓库时曾发生 TLS 连接失败，因此在保留原 repo 备份后，改用阿里云 Docker CE 镜像完成了同一组官方软件包的安装。

## 2. DNS、Nginx 和 HTTPS

1. 阿里云 DNS 已为 `campus.derawaze.top` 新增 A 记录，值为 `123.56.8.200`。
2. 服务器当前 `/etc/nginx/nginx.conf` 没有加载 `conf.d`。仓库内的 `nginx-host.conf` 是在现有博客配置上只增加 `conf.d` 加载后的可复现版本；安装前仍必须备份和比较。

```nginx
include /etc/nginx/conf.d/*.conf;
```

3. 在本地项目根目录上传 Nginx 配置和生产环境模板：

```powershell
scp -i D:\A1.pem deploy/cloud/nginx-campus.conf root@123.56.8.200:/tmp/campus.derawaze.top.conf
scp -i D:\A1.pem deploy/cloud/nginx-host.conf root@123.56.8.200:/tmp/nginx-host.conf
scp -i D:\A1.pem deploy/cloud/.env.production.example root@123.56.8.200:/tmp/campus.env
```

再在服务器安装 Nginx 配置，并且只在配置测试成功时重载：

```bash
cp -a /etc/nginx/nginx.conf /etc/nginx/nginx.conf.before-campus
install -d /etc/nginx/conf.d
install -m 644 /tmp/nginx-host.conf /etc/nginx/nginx.conf
install -m 644 /tmp/campus.derawaze.top.conf /etc/nginx/conf.d/campus.derawaze.top.conf
nginx -t && systemctl reload nginx
```

4. DNS 生效后使用 Certbot 为子域名签发证书，并让 Nginx 提供 HTTPS。本次只做课程 CD 演示、不接收续期提醒，因此使用无邮箱注册：

```bash
certbot --nginx -d campus.derawaze.top \
  --register-unsafely-without-email --agree-tos --no-eff-email \
  --redirect --non-interactive
nginx -t && systemctl reload nginx
```

2026-09-03 已签发有效证书并启用自动续期。真实长期运营时应重新配置可接收续期告警的邮箱。HTTPS 可用前不要启用生产注册登录，因为生产 Cookie 强制 `Secure`。

Nginx 配置按 `server_name` 分流，不修改博客根目录，也不占用新的公网端口。若配置测试失败，删除新增文件并恢复备份，博客即可回到原配置。

## 3. 生产环境文件

推荐上传并运行仓库脚本，让全部随机密钥直接在服务器生成，不经过本机、Git 或 CI 日志：

```powershell
scp -i D:\A1.pem scripts/deploy/cloud-provision.sh root@123.56.8.200:/tmp/cloud-provision.sh
ssh -i D:\A1.pem root@123.56.8.200 "id campus-deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash campus-deploy; usermod -aG docker campus-deploy; bash /tmp/cloud-provision.sh campus.derawaze.top /opt/campus-market campus-deploy"
```

脚本把部署目录和 `.env` 交给专用发布账号管理，并在 `.env` 已存在时拒绝覆盖。它初始设置 `MAIL_ENABLED=false`，以便先验证部署；需要演示真实注册邮件时，再编辑 `/opt/campus-market/shared/.env` 填写阿里云 SMTP 凭据并改为 `true`。必须保留：

```dotenv
CORS_ORIGINS=https://campus.derawaze.top
SESSION_COOKIE_SECURE=true
MAIL_ENABLED=false
```

这个文件永远不由 CI 上传或覆盖。宿主机已有 MySQL 不会冲突，因为平台 MySQL 仅位于 Compose 私有网络，未映射 3306。

## 4. GitHub Actions 配置

在仓库 Settings → Secrets and variables → Actions 配置以下仓库变量。工作流仍把发布 Job 标记为 `production` 环境；有仓库管理员权限时可再为该环境增加审批保护，但一次性课程演示不依赖 Environment Secret。

仓库 Variables：

| 名称 | 值 |
| --- | --- |
| `CLOUD_DEPLOY_ENABLED` | 准备完成前为 `false`，准备完成后改为 `true` |
| `CLOUD_DEPLOY_REF` | 临时验收为 `refs/heads/codex/cloud-server-deployment`；正式切换后才改为 `refs/heads/main` |
| `CLOUD_HOST` | `123.56.8.200` |
| `CLOUD_USER` | `campus-deploy` |
| `CLOUD_DEPLOY_PATH` | `/opt/campus-market` |
| `CLOUD_DOMAIN` | `campus.derawaze.top` |

Repository Secrets：

| 名称 | 内容 |
| --- | --- |
| `CLOUD_SSH_KEY` | 专用 `campus-deploy` 用户的无口令部署私钥内容，不是 `D:\A1.pem`，也不是文件路径 |
| `CLOUD_KNOWN_HOSTS` | 经人工核对指纹后的 `123.56.8.200` SSH host key 行 |

2026-09-03 已从服务器本机公钥与客户端扫描两侧核对 ED25519 指纹，二者均为 `SHA256:0qb2+tXkZMCWBQLvpEPqvv0rAYUFGbI0UHIxBCwMxII`。若以后重装服务器或 SSH host key 发生变化，必须重新从可信控制台核对，不能为了让流水线通过而关闭 host key 检查。

私钥不能复制到仓库或服务器发布目录。当前已创建独立的 `campus-deploy` Linux 用户，加入 `docker` 组，只让 GitHub Actions 使用其专用密钥；日常主机管理仍使用原 root 密钥。

## 5. 自动发布流程

`CLOUD_DEPLOY_ENABLED=true` 后，每次推送 `CLOUD_DEPLOY_REF` 指定的分支会依次执行：

1. 五个服务测试、契约/前端测试和 Playwright E2E。
2. 构建并推送六个应用镜像及三个基础依赖镜像，全部使用同一 `sha-xxxxxxx` 标签。
3. 在 GitHub 临时 Kind 集群完成一次完整部署与健康检查。
4. 上传只包含 Compose 配置和部署脚本的不可变 release。
5. 云主机匿名拉取公开 GHCR 镜像，以 `--no-build` 启动。由于该主机访问 Docker Hub 曾超时，CI 还会把固定版本的 MySQL、Redis、RabbitMQ 基础镜像原样镜像到同一 GHCR SHA 标签，生产主机不再依赖 Docker Hub。
6. 检查容器健康、Gateway readiness 和提交版本号。
7. 失败时保存 Compose 日志、容器资源和宿主机内存/磁盘证据；已有成功版本时尝试回滚应用镜像。

流水线不会停止、覆盖或重新配置博客，也不会上传 `.env`。发布证据保存在服务器 `/opt/campus-market/evidence/<sha-tag>/`，同时作为 GitHub Actions artifact 上传。

本次只验证远程分支，保持：

```text
CLOUD_DEPLOY_REF=refs/heads/codex/cloud-server-deployment
```

确认上述 DNS、HTTPS、Secret 和主机准备完成后，将 `CLOUD_DEPLOY_ENABLED` 从 `false` 改为 `true`，再向该分支推送一次提交。不要修改为 `main`，也不要把本分支合并到 `main`。

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
