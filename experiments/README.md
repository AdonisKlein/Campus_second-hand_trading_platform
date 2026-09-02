# 云原生实验公共入口

本目录承载工作项 9—11 的可复现实验。调用者只需要选择实验名，公共 runner 负责 runId、环境记录、诊断、结果 Schema 和退出码：

```powershell
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment smoke
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment hpa
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment fault
powershell -ExecutionPolicy Bypass -File experiments/run.ps1 -Experiment performance
```

- `common/` 由 A 维护；B/C/D 不复制环境采集、k6、Metrics Server 或诊断逻辑。
- `hpa/`、`fault/`、`performance/` 分别由 B/C/D 实现，并各自提供 `run.ps1`。
- 生成证据位于 `artifacts/cloud-native/<runId>/`，已被 Git 忽略。只提交经过脱敏的小型摘要和证据校验和。
- 公共采集器不会读取 Kubernetes Secret、`.env`、SMTP 密码或本地凭据。
- k6 固定为 Grafana 官方 GHCR `1.5.0` manifest digest `sha256:2072ea9eafa596532d9aee0cc0e0a50cfb0e7fb703981a46179af5f4f22c5ea4`，避免浮动标签和 Docker Hub 证书差异。

基础 smoke 要求已有可访问的 Kind 集群；默认 context 为 `kind-campus-ci`，namespace 为 `campus-market`。它会安装固定版本 Metrics Server、自动建立 Web port-forward、用 Docker k6 请求 Gateway liveness，并保存资源采样与失败诊断。

入口兼容 Windows PowerShell 5.1 (`powershell.exe`) 与 PowerShell 7 (`pwsh`)；后台资源采样器会复用启动入口的同一个 PowerShell host，不要求额外安装 PowerShell 7。
