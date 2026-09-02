# B：HPA 实验 adapter

B 在本目录提供 `run.ps1`，参数 interface 必须兼容：

```powershell
param([string]$RunDirectory, [string]$Context, [string]$Namespace, [string]$BaseUrl)
```

脚本只实现 MinIO/HPA 的准备、施压和业务断言；环境记录、Metrics Server、资源采样、诊断及最终 `result.json` 由公共 runner 负责。
