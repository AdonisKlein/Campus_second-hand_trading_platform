# 工作项 10 PPT 讲稿

带图表的导出件在 `experiments/fault/report/`：

- `fault-isolation-experiment-report.pdf`：8 页幻灯片，含柱状图与熔断时间线
- `fault-isolation-experiment-report.html`：浏览器打开即可看图，也可打印成 PDF
- `chart-latency.png` / `chart-orders.png` / `chart-circuit.png`：可直接插入 PowerPoint

依据正式证据目录 `artifacts/cloud-native/fault-20260902T074227Z-22133ba3/` 整理。原始日志不进 Git，本文件只含可展示摘要。时间一律换算为北京时间（UTC+8）。

建议 8 页。每节标题可直接做幻灯片标题，要点做正文，底部署名出处。

---

## 第 1 页 · 封面

**标题：** Trading → Marketplace 依赖故障隔离

**副标题：** 校园二手交易平台 · 云原生实验 · 工作项 10 · 成员 C

**结论（大字）：** Marketplace 停止后，Trading 稳定返回 503，不写订单、不拖垮其他服务；恢复后熔断自行闭合，无需重启 Trading。

| 项 | 值 |
| --- | --- |
| 判定 | PASS |
| runId | `fault-20260902T074227Z-22133ba3` |
| 时间 | 2026-09-02 15:42:27–15:43:21 |
| Git | `22133ba3` · `codex/cloud-native-experiments` |
| 命令 | `experiments/run.ps1 -Experiment fault` |

---

## 第 2 页 · 环境

**一句话：** 同一台 Windows 机器、同一 Kind 集群、同一提交。

| 项 | 实测 |
| --- | --- |
| 主机 | Windows 11 · Ryzen 9 7940H · 16 核 · 16 GB |
| Docker Desktop | 29.3.1 · 分配约 7.4 GB |
| Kind | v0.33.0 windows/amd64 |
| Kubernetes | v1.37.0 · 单节点 `campus-ci-control-plane` |
| 运行时 | containerd 2.3.4 · Debian 13 |
| JDK / Maven | 25.0.4.1 LTS · 3.9.16 |
| 集群 | context `kind-campus-ci` · ns `campus-market` |
| 基线标签 | `midterm-check` 89bd7d68 · `microservices-end` a21e14fa |

出处：`environment.json`

---

## 第 3 页 · 方法

**注入故障：** `kubectl scale deployment/marketplace-service --replicas=0`

**保护策略：**

- 连接超时 300ms，响应超时 800ms
- 仅对安全 GET 重试一次
- 熔断窗口 10、最少 5 次、失败率 50%、打开 15s、半开 2 次
- 熔断不注册 health，避免 Kubernetes 重启 Trading

**自动步骤：** 造数 → 停 Marketplace → 连打 8 次购买意向 → 读既有订单 → 拉回 Marketplace → 等 15s 半开 → 再下单

---

## 第 4 页 · 故障时固定 503

**大字：** 8 / 8 次新购买意向 = HTTP 503，最大 71 ms

| 次 | HTTP | code | Retry-After | 延迟 |
| --- | --- | --- | --- | --- |
| 1 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 71 ms |
| 2 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 62 ms |
| 3 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 52 ms |
| 4 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 58 ms |
| 5 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 59 ms |
| 6 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 60 ms |
| 7 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 53 ms |
| 8 | 503 | PRODUCT_SERVICE_UNAVAILABLE | 1 | 56 ms |

文案：「商品服务暂时不可用，请稍后重试」。无伪造商品，无 Outbox。

出处：`fault-summary.json` → `faultResponses`

柱状图数据（延迟 ms）：`71, 62, 52, 58, 59, 60, 53, 56`

---

## 第 5 页 · 隔离：订单数不变，别的服务还在

**大字：** 故障中订单数 4 → 4，Trading 未重启

| 阶段 | 订单行数 | 说明 |
| --- | --- | --- |
| 故障前 | 4 | 含预置意向 existingOrderId=4 |
| 故障中 | 4 | 8 次 503 没有新行 |
| 恢复后 | 5 | recoveredOrderId=5 |

| 断言 | 结果 |
| --- | --- |
| otherServicesReady | true |
| tradingRestarted | false |
| Account / Governance / Trading | 1/1 Running，Restarts=0 |

出处：`fault-summary.json`、`cluster-resources.txt`

柱状图数据（订单数）：`4, 4, 5`

---

## 第 6 页 · 熔断四态（本 run）

**主图请用本页，不要用 `circuit-transitions.txt` 的两轮叠字。** 那份文件把同一 Pod 上一轮失败 run 的日志也拼进去了。

| 北京时间 | 状态 | 日志要点 |
| --- | --- | --- |
| 15:42:41 | CLOSED → OPEN | failureRate=80.0，bufferedCalls=5，failedCalls=4 |
| 15:42:56 | OPEN → HALF_OPEN | 打开约 15s，自动半开线程 |
| 15:43:19 | 半开探测成功 | POST /api/orders → 200，订单 id=5 |
| 15:43:19 | HALF_OPEN → CLOSED | 无需重启 Trading |

可贴原文：

```
marketplace circuit transition from=CLOSED to=OPEN failureRate=80.0 bufferedCalls=5 failedCalls=4
marketplace circuit transition from=OPEN to=HALF_OPEN failureRate=-1.0 bufferedCalls=0 failedCalls=0
marketplace circuit transition from=HALF_OPEN to=CLOSED failureRate=-1.0 bufferedCalls=0 failedCalls=0
```

出处：`logs/trading-circuit.log`（`@timestamp` 07:42:41Z–07:43:19Z）

---

## 第 7 页 · 对照验收清单

| 计划要求 | 现场 |
| --- | --- |
| 其他服务保持 Ready | 是 |
| 新意向无数据库副作用 | 订单数 4 不变 |
| 有上限的设计 503 | 8/8，最大 71ms |
| 日志四态完整 | CLOSED → OPEN → HALF_OPEN → CLOSED |
| 恢复不重启 Trading | tradingRestarted=false |

单元测试（代码侧，非本目录）：Trading `mvn test` 27/27，含超时、5xx、404、非幂等不重试、熔断打开与恢复。

---

## 第 8 页 · 证据可追溯

正式目录（课程附件，不提交 Git）：

`artifacts/cloud-native/fault-20260902T074227Z-22133ba3/`

| 文件 | 用来证明什么 |
| --- | --- |
| `result.json` | 公共入口 status=PASS |
| `fault-summary.json` | 503 样本与订单计数 |
| `logs/trading-circuit.log` | 熔断状态变化原文 |
| `cluster-resources.txt` | 恢复后 Pod Ready、Restarts=0 |
| `environment.json` | 机器、Kind、K8s、Git SHA |

复现：

```powershell
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment fault
```
