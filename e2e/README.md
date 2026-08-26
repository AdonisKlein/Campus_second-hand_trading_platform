# Playwright E2E

Run from this directory:

```powershell
npm ci
npm run test:e2e
```

`E2E_BASE_URL` may point at a local Compose/Kubernetes web service. The
initial smoke test uses a data URL so the runner and report contract can be
verified without an application environment. Full user journeys will use the
same fixture and configuration.

Every run writes machine-readable JUnit XML to
`test-results/junit.xml` and an HTML report to
`test-results/playwright-report`. Failed tests retain trace, screenshot and
video; the evidence fixture also attaches browser console, page error and
failed-network-request logs.
