# Kubernetes / Kind 部署

本目录是课程 CI/CD 的 Kubernetes 部署 interface。`base` 保存环境无关资源，`overlays/ci` 只描述 CI/本地 Kind 与基础环境的差异。

## 为什么使用 Base + Overlay

- `base`：MySQL、Backend、Web、ConfigMap、PVC、Service 和探针，只维护一份。
- `overlays/ci`：增加 Mailpit、CI 邮件配置和临时 Secret。
- 以后增加生产环境时，新建 `overlays/prod`，不复制并修改整套 YAML，避免三个环境逐渐不一致。

Kustomize 已内置在 kubectl 中，可以用 `kubectl kustomize` 渲染，用 `kubectl apply -k` 部署。

## 文件职责

- `namespace.yaml`：把课程环境隔离在 `campus-market` namespace。
- `mysql-statefulset.yaml`：MySQL 的稳定身份、Service 和健康检查。
- `backend-deployment.yaml`：后端、数据库等待、Session/图片存储和三类探针。
- `web-deployment.yaml`：Nginx Web 与同源 `/api` 代理。
- `pvc.yaml`：数据库和商品图片持久卷，Pod 重建不会清空数据。
- `configmap.yaml`：可以提交的非敏感配置。
- `secret.example.yaml`：Secret 键名示例，不参与部署，真实值不得提交。

## Windows 本地 Kind 验收

先启动 Docker Desktop，并安装 Kind。官方 Windows 包可用：

```powershell
winget install Kubernetes.kind
```

如果 `winget` 自身报错，可以直接使用 Kind 官方 Windows 二进制：

```powershell
New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\kind"
Invoke-WebRequest "https://kind.sigs.k8s.io/dl/v0.32.0/kind-windows-amd64" -OutFile "$env:LOCALAPPDATA\kind\kind.exe"
$env:Path = "$env:LOCALAPPDATA\kind;$env:Path"
kind version
```

上面的 PATH 只对当前窗口生效；需要长期使用时，把 `%LOCALAPPDATA%\kind` 加入 Windows 的“用户环境变量 → Path”。

重新打开 PowerShell 后检查：

```powershell
docker version
kind version
kubectl version --client
```

一键构建镜像、创建集群、生成本地随机 Secret、部署并冒烟测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 up
```

`up` 会使用临时端口转发完成健康检查，然后立即关闭转发。需要浏览页面时，另外打开一个 PowerShell 窗口运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 forward-web
```

邮件测试界面使用另一个窗口：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 forward-mail
```

转发运行期间可以访问：

- Web：`http://127.0.0.1:18080`
- Mailpit：`http://127.0.0.1:18025`
- 健康检查：`http://127.0.0.1:18080/api/actuator/health/liveness`

选择 `port-forward` 而不是固定 NodePort，是因为它不依赖 Docker Desktop、Kind 和宿主系统之间的端口映射实现；Windows、Linux 和 GitHub Actions 使用同一条命令，课程验收更稳定。

查看状态或删除本地集群：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 status
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 down
```

`.env.secret` 由脚本生成并被 Git 忽略。正式环境应由云 Secret Manager、External Secrets 或受保护的 CI Secret 注入，不能复制示例密码上线。

## 手动理解每一步

```powershell
# 1. 只渲染最终 YAML，不修改集群
kubectl kustomize k8s/overlays/ci

# 2. 创建/更新资源；重复执行是幂等的
kubectl apply -k k8s/overlays/ci

# 3. 等待新版本真正可用，不把“创建了对象”误认为“部署成功”
kubectl -n campus-market rollout status deployment/campus-backend

# 4. 查看 Pod、Service 和持久卷
kubectl -n campus-market get pods,svc,pvc
```

Readiness 失败时 Pod 不接收流量；Liveness 连续失败时 Kubernetes 重启容器；Startup Probe 给 MySQL 和 Spring Boot 足够的首次启动时间，避免启动过程中被误杀。
