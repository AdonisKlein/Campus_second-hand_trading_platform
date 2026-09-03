# 工作项 9：HPA 自动扩缩容实验（成员 B）

目标：证明 Marketplace 在 MinIO 图片存储下无本地状态，CPU 压力升高后 HPA 把副本从 1
扩到 2 以上，压力下降后在稳定窗口内回到 1；并留下副本时间线、CPU/内存采样、吞吐与
错误率证据。

## 前置条件

- Docker Desktop（已启动）、`kind`、`kubectl`、PowerShell。
- k6 统一使用固定版本 Docker 镜像，不在本机安装。
- 首次运行会生成 `k8s/overlays/ci/.env.secret`（随机开发密钥，勿提交）。

## 环境准备

1. 用集成分支完整部署微服务环境（会构建全部镜像并创建 kind 集群）：

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/ci/kind-local.ps1 -Action up
   ```

   如果本机已有等价部署且只想增量应用本实验内容，可跳过上一步。

2. 应用 HPA overlay（MinIO、Metrics Server、Marketplace MinIO 环境变量、资源限制、
   HPA、全部应用副本 1）：

   ```powershell
   kubectl apply -k k8s/overlays/hpa
   kubectl -n campus-market rollout status deployment/marketplace-service --timeout=300s
   ```

   Metrics Server 部署在 `kube-system`，使用官方 v0.9.0 清单并加入 Kind 所需的
   `--kubelet-insecure-tls=true` 参数（来源见
   https://github.com/kubernetes-sigs/metrics-server/tree/v0.9.0/manifests/base ，Apache-2.0）。

3. 等待资源指标可用（不能用固定 sleep 替代）：

   ```powershell
   kubectl wait --for=jsonpath='{.status.conditions[?(@.type=="Available")].status}'=True \
     --timeout=180s -n kube-system apiservice/v1beta1.metrics.k8s.io
   kubectl top nodes
   ```

4. 暴露 Gateway：

   ```powershell
   # k6 以 Docker 容器运行时，端口转发必须监听 0.0.0.0（容器经 host.docker.internal 访问宿主机）
   kubectl -n campus-market port-forward --address 0.0.0.0 service/gateway 8080:8080
   ```

## 预置数据与冒烟

1. 生成确定性压测数据（50 卖家 + 20000 在售商品，固定随机种子 20260826）：

   ```powershell
   python experiments/hpa/seed-marketplace.py --out seed-hpa.sql
   ```

2. 导入到 Marketplace 库（先确认行数一致再压测）：

   ```powershell
   ```powershell
   # 复制到 MySQL Pod 内再 source，避免 PowerShell 管道向 kubectl stdin 传中文时被重新编码
   kubectl cp --context kind-campus-microservices -n campus-market seed-hpa.sql \
     campus-market/campus-mysql-0:/tmp/seed-hpa.sql
   $secrets = @{}; Get-Content k8s/overlays/ci/.env.secret | ForEach-Object {
       if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.+)$') { $secrets[$Matches[1]] = $Matches[2] } }
   kubectl exec --context kind-campus-microservices -n campus-market campus-mysql-0 -- env \
     "MYSQL_PWD=$($secrets.MARKETPLACE_DB_PASSWORD)" mysql -u marketplace_user campus_marketplace \
     -e "source /tmp/seed-hpa.sql"
   kubectl exec --context kind-campus-microservices -n campus-market campus-mysql-0 -- env \
     "MYSQL_PWD=$($secrets.MARKETPLACE_DB_PASSWORD)" mysql -u marketplace_user campus_marketplace \
     -Nse "SELECT CONCAT((SELECT COUNT(*) FROM items),',',(SELECT COUNT(*) FROM searchable_user_projection))"
   ```

   期望输出 `20000,50`。

3. 冒烟：`GET /api/search?scope=ITEMS&q=的` 应返回 200 且 `data.items` 非空。

## 运行扩缩容实验

1. 打开采样器记录副本/CPU/内存/HPA 状态：

   ```powershell
   powershell -File experiments/hpa/sample-resources.ps1 -ClusterContext kind-campus-microservices -Output evidence/run1/resource-samples.csv
   ```

2. 先 2 分钟低负载预热，再分阶段加压（20 → 50 → 100 → 150 VUs 各 2 分钟），
   期间另开终端观察 HPA 和 Pod：

   ```powershell
   # 本机直接跑 k6 时用 http://127.0.0.1:8080；k6 走 Docker 容器时用 host.docker.internal
   docker run --rm -v "${PWD}:/scripts" -e K6_BASE_URL=http://host.docker.internal:8080 \
     -e 'STAGES=[{"target":20,"duration":"2m"},{"target":50,"duration":"2m"},{"target":100,"duration":"2m"},{"target":150,"duration":"2m"},{"target":20,"duration":"1m"}]' \
     grafana/k6:0.54.0 run --summary-export=/scripts/evidence/run1/k6-summary.json /scripts/load.js
   kubectl get hpa -w -n campus-market
   kubectl get pods -w -n campus-market
   ```

3. 压力结束后继续观察，直到 HPA 副本回到 1，停止采样器。

4. 每次正式实验至少跑 3 次并保留：k6 summary、`resource-samples.csv`、
   `kubectl get hpa -o yaml`、事件和 Pod 日志；不满足“扩容到 >=2 再缩回 1”即判定失败，
   调整后重跑，绝不使用手动 scale 冒充 HPA。

## MinIO 跨副本图片读取证据

1. 扩容前用任意学生账号在页面给商品上传一张图片（存到 MinIO）。
2. 压测使 HPA 扩到 >=2 个 Pod 后，打开该商品详情页并直接 GET 图片 URL：
   应为 200，说明新副本从同一个 MinIO bucket 读到了旧副本上传的图片。
3. 也可以停掉旧 Pod（`kubectl delete pod -l app=marketplace`）后再次 GET，图片仍可访问。

## 验收证据目录建议

```text
experiments/hpa/evidence/<runId>/
  hpa-samples.csv            # 副本 + CPU/内存/HPA 时间线
  k6-summary.json            # 吞吐、平均/P95、错误率
  hpa.yaml                   # 副本 desired 变化快照
  pod-events.txt             # 扩容/缩容事件
  marketplace-logs/          # 采样期间日志
```

注意：Metrics Server 与统一结果目录最终由成员 A 的公共实验基础整合，本目录先保证
工作项 9 可以独立复现。

## 现场运行记录（2026-09-02/03）

三轮正式实验已完成，证据在 `experiments/hpa/evidence/run1|run2|run3/`（每轮含
`resource-samples.csv`、`k6-summary.json`、HPA before/after YAML、describe、事件、
k6 控制台日志）。三轮请求量 31204 / 33752 / 36136，失败 0 / 0 / 1（0.00%，唯一失败为
run3 删除旧 Pod 做跨副本验证瞬间的请求），P95 延迟约 2.5–2.7s；HPA desired 均扩到 ≥2
并缩回 1。跨副本图片证据见 `evidence/run3/image-state.json` 与
`image-cross-replica-checks.json`（删除上传时唯一旧 Pod 后 10/10 GET 200）。
