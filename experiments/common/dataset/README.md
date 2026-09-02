# 标准性能数据

本 module 只生成与数据库结构无关的确定性 CSV。D 负责为 `midterm-check` 和 `microservices-end` 编写各自 SQL adapter，禁止修改这里的业务 ID 或随机规则。

正式数据：

```powershell
node experiments/common/dataset/generate-canonical-data.mjs `
  --seed 20260902 --users 2500 --items 20000 --messages 50000 `
  --output artifacts/cloud-native/dataset
```

相同参数必须得到字节完全相同的三个 CSV 和 `manifest.json`。邮箱使用保留域 `benchmark.example`，不会向真实地址发送邮件；数据中不包含密码或凭据。
