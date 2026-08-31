# Kubernetes / Kind 微服务部署

`k8s/base` 是环境无关的微服务运行拓扑，`k8s/overlays/ci` 叠加本地/CI 使用的 Mailpit 和配置。Kustomize 已内置在 `kubectl` 中。

## Base 中包含什么

- MySQL StatefulSet：同一服务器创建四库四账号，使用独立 PVC。
- Redis：保存 Gateway Session。
- RabbitMQ：传递交易 Saga 和治理事件。
- API Gateway 与 Account、Marketplace、Trading、Governance：各自 Deployment、ClusterIP Service、liveness、readiness 和 startup probe。
- Web/Nginx：浏览器唯一入口并将 `/api` 同源转发 Gateway。
- Marketplace 图片 PVC，以及 Redis、RabbitMQ 的独立 PVC。
- ConfigMap 保存非敏感配置；Secret 只保存数据库密码、Pepper、内部凭据和消息中间件密码。

四个数据库账号只能访问自己的数据库。内部服务只以 ClusterIP 暴露，不映射宿主机端口。

## Windows 本地 Kind 验收

需要 Docker Desktop、`kubectl` 和 Kind。安装后执行：

```powershell
docker version
kind version
kubectl version --client
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 up
```

脚本会完成六个镜像的独立构建、创建 Kind 集群、加载镜像、生成本地随机 Secret、应用 Kustomize、等待平台与所有业务服务 rollout，并通过 Web 同源入口做冒烟测试。任意 rollout 或健康检查失败都会返回非零退出码。

查看状态：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 status
kubectl -n campus-market get pods,svc,pvc
```

临时访问 Web 或 Mailpit：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 forward-web
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 forward-mail
```

- Web：`http://127.0.0.1:18080`
- Mailpit：`http://127.0.0.1:18025`
- 健康检查：`http://127.0.0.1:18080/api/actuator/health/liveness`

删除这个本地验收集群：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 down
```

## 手动理解部署过程

手动执行前先复制 Secret 键模板并替换所有示例值；该文件已被 Git 忽略。使用一键脚本时不需要这一步，脚本会生成随机值。

```powershell
Copy-Item k8s/overlays/ci/.env.secret.example k8s/overlays/ci/.env.secret
```

```powershell
# 只渲染，不修改集群
kubectl kustomize k8s/overlays/ci

# 创建或更新资源
kubectl apply -k k8s/overlays/ci

# 等待某个新版本真正可接收请求
kubectl -n campus-market rollout status deployment/account-service --timeout=300s

# 观察资源与故障原因
kubectl -n campus-market get pods,svc,pvc
kubectl -n campus-market describe pod <pod-name>
kubectl -n campus-market logs <pod-name> --all-containers
```

Readiness 失败时 Pod 不接收流量；Liveness 连续失败时 Kubernetes 重启容器；Startup Probe 避免首次迁移和 Spring Boot 启动期间被误杀。

`k8s/overlays/ci/.env.secret` 由本地脚本生成且被 Git 忽略。真实环境应由受保护的 CI Secret 或 Secret Manager 注入，不能提交实际密码。

## 与工作项 8 的边界

工作项 6 提供可重复的微服务 Kustomize 拓扑和本地 Kind 验收入口。当前 GitHub Actions 与 `scripts/ci/deploy-kind.sh` 仍是旧单体流水线接口；工作项 8 才会把它替换为六个 SHA 版本镜像、按服务构建部署、统一日志与失败证据收集。不要把旧脚本当成当前微服务的自动发布入口。
