# C：故障隔离实验 adapter

C 在本目录提供 `run.ps1`，参数 interface 必须兼容：

```powershell
param([string]$RunDirectory, [string]$Context, [string]$Namespace, [string]$BaseUrl)
```

脚本负责准备交易、停止/恢复 Marketplace、断言固定 503 和数据库无副作用。不得复制公共诊断、资源采样或证据哈希逻辑。
