# Playwright E2E

Run from this directory:

```powershell
npm ci
npm run test:e2e
```

本机若已安装 Google Chrome，可设置 `E2E_BROWSER=chrome` 使用系统浏览器，避免下载 Playwright Chromium：

```powershell
$env:E2E_BROWSER = 'chrome'
$env:E2E_VIDEO = 'off'
npm run test:e2e:compose
```

未设置该变量时，默认使用 Playwright 管理的 Chromium。
未设置 `E2E_VIDEO` 时失败用例会保留视频；本地关闭视频可避免额外安装 ffmpeg。

`E2E_BASE_URL` may point at a local Compose/Kubernetes web service. The
initial smoke test uses a data URL so the runner and report contract can be
verified without an application environment. Full user journeys will use the
same fixture and configuration.

Every run writes machine-readable JUnit XML to
`test-results/junit.xml` and an HTML report to
`test-results/playwright-report`. Failed tests retain trace, screenshot and
video; the evidence fixture also attaches browser console, page error and
failed-network-request logs.
