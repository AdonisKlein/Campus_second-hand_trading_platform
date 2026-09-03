# C：故障隔离实验 adapter

C 在本目录提供 `run.ps1`，参数 interface 必须兼容：

```powershell
param([string]$RunDirectory, [string]$Context, [string]$Namespace, [string]$BaseUrl)
```

脚本负责准备交易、停止/恢复 Marketplace、断言固定 503 和数据库无副作用。不得复制公共诊断、资源采样或证据哈希逻辑。

## 运行

先用 `scripts/ci/kind-local.ps1 up` 部署可访问的 Kind 集群，再由公共入口调用：

```powershell
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment fault
```

默认 context 为 `kind-campus-ci`，namespace 为 `campus-market`。adapter 会把 `host.docker.internal` 改写为 `127.0.0.1` 以便宿主机 HTTP 调用，并在需要时对 `service/web` 做 port-forward。

实验证据写在 `$RunDirectory`：

- `fault-summary.json`：熔断状态、503 样本、订单计数、Trading 是否重启
- `circuit-transitions.txt`：从 Trading 日志抽出的 CLOSED/OPEN/HALF_OPEN 轨迹
- `logs/trading-circuit.log`：实验窗口内的 Trading 日志片段

公共 runner 仍负责 `environment.json`、诊断、证据哈希和最终 `result.json`。失败时 adapter 抛出异常，使入口保持非零退出码。
