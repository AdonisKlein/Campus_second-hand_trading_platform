# 测试报告汇总

`test-report.mjs` 不依赖第三方 Node 包，把 Maven Surefire/Failsafe 和 Playwright 的 JUnit XML 合并为：

- `test-report.json`：流水线上传、门禁和后续统计使用的机器可读结果；
- `test-report.md`：提交号、分支、环境、三类测试统计和失败摘要。

本地运行：

```powershell
node scripts/ci/test-report.mjs --output test-results/summary
```

没有发现任何测试结果时脚本仍会写出诊断报告，但默认返回非零，防止流水线把“测试没有执行”误判成成功。仅在调试报告模板时可以显式使用 `--allow-empty`。

CI 可显式传入元数据和报告目录：

```powershell
node scripts/ci/test-report.mjs `
  --input backend/target/surefire-reports/TEST-example.xml `
  --input backend/target/failsafe-reports/TEST-example.xml `
  --input e2e/test-results/results.xml `
  --commit $env:GITHUB_SHA `
  --branch $env:GITHUB_REF_NAME `
  --environment ci `
  --java 25 `
  --database "MySQL 8.4 Testcontainers" `
  --docker "Docker Engine 29" `
  --kubernetes "not-used" `
  --output test-results/summary
```

输出 JSON 遵循同目录的 `test-report.schema.json`；未显式传入提交号和分支时，脚本从当前 Git 工作区读取。

发现失败或错误时进程返回非零；报告仍然会写出。上传步骤可使用 GitHub Actions 的 `if: always()`。

```powershell
node scripts/ci/test-report.mjs --self-test
```
