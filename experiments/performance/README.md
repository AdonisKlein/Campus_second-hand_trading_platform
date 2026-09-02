# D：性能对比实验 adapter

D 在本目录提供 `run.ps1`，参数 interface 必须兼容：

```powershell
param([string]$RunDirectory, [string]$Context, [string]$Namespace, [string]$BaseUrl)
```

D 读取 `common/dataset` 生成的标准 CSV，分别渲染 `midterm-check` 和 `microservices-end` SQL，并保存 18 次独立结果。不得修改公共数据的 ID、随机种子或校验规则。
