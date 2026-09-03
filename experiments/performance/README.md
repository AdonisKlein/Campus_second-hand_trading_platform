# D：性能对比实验 adapter

本目录现已提供 canonical CSV 到两个固定版本数据库结构的确定性 SQL adapter、两个版本共用的 k6 benchmark、D-local embedded 参数兼容 runner，以及结果汇总器。尚未执行 18 次正式 k6 实验，不作性能结论。adapter 不修改公共 CSV，并原样保留 user、item 和 message ID。

生成 SQL 已在真实 MySQL 8.4 验证导入：单体包含 users 2,500、items 20,000、item_tags 24,000、messages 50,000；微服务包含 Account users 2,500，以及 Marketplace projection 2,500、items 20,000、item_tags 24,000、messages 50,000。

## 生成 canonical dataset

在仓库根目录运行公共生成器：

```powershell
node experiments/common/dataset/generate-canonical-data.mjs `
  --seed 20260902 --users 2500 --items 20000 --messages 50000 `
  --output artifacts/cloud-native/dataset
```

不要将 SQL 输出目录设为 canonical dataset 目录。adapter 不删除输出目录中的其他文件，只写本次目标对应的 SQL 和 `adapter-manifest.json`。

## 生成导入 SQL

同时生成两个版本：

```powershell
node experiments/performance/generate-sql.mjs `
  --input artifacts/cloud-native/dataset `
  --output artifacts/cloud-native/performance-sql `
  --target all
```

也可以分别生成：

```powershell
node experiments/performance/generate-sql.mjs --input artifacts/cloud-native/dataset --output artifacts/cloud-native/midterm-sql --target midterm
node experiments/performance/generate-sql.mjs --input artifacts/cloud-native/dataset --output artifacts/cloud-native/microservices-sql --target microservices
```

输出文件：

- `midterm-check.sql`：导入 `midterm-check` 单体数据库。
- `microservices-end-account.sql`：导入 Account 数据库。
- `microservices-end-marketplace.sql`：导入 Marketplace 用户查询投影、商品、标签和公开留言。
- `adapter-manifest.json`：记录 canonical manifest SHA-256、三个 CSV 的 SHA-256、数量、ID 范围及实际输出文件。

SQL 使用每批 500 行的批量 `INSERT`。所有文本编码成 `CONVERT(0x... USING utf8mb4)`，因此中文、单引号、反斜杠和换行不依赖 MySQL 的字符串转义模式；可空空值写为 `NULL`。导入目标应是已经完成对应 tag 全部 Flyway migration 的空业务库。

## 字段映射与固定默认值

| Canonical 数据 | `midterm-check` | `microservices-end` |
| --- | --- | --- |
| `users.csv` | 单体 `users` | Account `users`，以及 Marketplace `searchable_user_projection` |
| `items.csv` | 单体 `items` | Marketplace `items` |
| `items.campus_region` | `items.region` | `items.region` |
| `items.tags`（`|` 分隔） | 单体 `item_tags` | Marketplace `item_tags` |
| `messages.csv` | 单体公开留言 `messages` | Marketplace 公开留言 `messages` |

`messages.csv` 不会导入 Trading 的 `chat_conversations`/`chat_messages`，两者属于另一套私聊模型。

Marketplace 的 `GET /api/items` 通过 JPQL `INNER JOIN item.sellerProjection` 读取 `searchable_user_projection`。因此 adapter 会把同一批 canonical 用户同步渲染成 Marketplace 查询投影；这不是额外业务数据，而是 Account canonical 用户在 Marketplace 中为列表查询维护的本地公开投影。Marketplace SQL 按 projection、items、item_tags、messages 的顺序导入。

Projection 字段映射：

| `users.csv` | `searchable_user_projection` |
| --- | --- |
| `id` | `id` |
| `username` | `username` |
| `nickname` | `nickname` |
| `campus_region` | `campus_region` |
| `credit_score` | `credit_score` |
| `last_active_at` | `last_active_at` |

Projection 固定 benchmark 值为 `status=ACTIVE`、`role=STUDENT`、`source_version=0`、`row_version=0`；`created_at` 和 `updated_at` 均使用该 canonical 用户的 `last_active_at`，不生成随机时间。

Canonical CSV 未提供但 schema 需要的字段使用以下确定值：

- `password_hash`：固定为 `$benchmark$not-a-real-password-hash$20260902`，这是不可用于真实登录的显式 benchmark 占位文本，两种架构一致。
- `role=STUDENT`、`status=ACTIVE`、`login_failed_count=0`、`auth_version=0`、`version=0`、`public_profile_version=0`。
- `phone=NULL`、`locked_until=NULL`、`image_url=NULL`、单体 `status_reason=NULL`、Marketplace `reserved_order_id=NULL`。
- `created_at`：用户使用 canonical `last_active_at`；商品和留言使用各自 canonical `created_at`。ISO UTC 时间确定性转换为 MySQL `DATETIME(6)`。

## 自动校验与测试

adapter 在写 SQL 前验证 manifest `schemaVersion=1`、seed、固定数量（2500/20000/50000）、文件字节数和 SHA-256、精确表头、必需字段、价格格式、引用 ID，以及三类主键严格保持 canonical `1..N`。任一检查失败均以非零状态退出。两个 adapter 的输出元数据引用同一个 canonical manifest SHA-256。

运行不依赖 MySQL 的测试：

```powershell
node --test experiments/performance/dataset-adapter.test.mjs experiments/performance/benchmark.test.mjs
```

## 共用 k6 benchmark

`benchmark.js` 供 `midterm-check` 与 `microservices-end` 共用，只通过 `BASE_URL` 切换被测环境。每个 iteration 按固定顺序匿名请求：

1. `GET /api/items`，tag 为 `endpoint=items_list`；
2. `GET /api/items/{id}`，tag 为 `endpoint=item_detail`；
3. `GET /api/messages/item/{itemId}`，tag 为 `endpoint=item_messages`。

脚本不登录、不设置 Cookie 或 Authorization。每个响应最多解析 JSON 一次；检查共同的 `{success,message,data}` 包装、非空数组、正整数 ID，以及详情/留言中的 item ID 是否与请求一致。JSON 解析错误转换为 check 失败并计入 `benchmark_json_errors`，不会让 iteration 因解析异常而中断。

固定 canonical item ID 为：

```text
5278, 7433, 7654, 8519, 11496, 18637, 3742, 13457, 4813, 9944
```

这些商品均存在、公开可见且有 9—11 条公开留言。第 `__VU` 个 VU 的第 `__ITER` 次 iteration 使用索引 `((__VU - 1) + __ITER) % 10`，不使用随机数。

`benchmark.js` 支持以下 k6 环境变量：

| 变量 | 安全默认值 | 用途 |
| --- | --- | --- |
| `BASE_URL` | 空，由 runner 必须传入 | 被测环境入口 |
| `VUS` | `1` | 并发 VU |
| `DURATION` | `10s` | 测量时间 |
| `ARCHITECTURE` | `development` | summary tag |
| `RUN_LABEL` | `smoke` | 重复运行标签 |

为兼容公共 `invoke-k6.ps1` 当前只透传 `BASE_URL` 和 `K6_SUMMARY_PATH` 的限制，D-local runner 使用 `generate-benchmark.mjs` 只替换 `/*__PERFORMANCE_RUN_CONFIG__*/ null` 这一处 sentinel，把 `vus`、`duration`、`architecture`、`runLabel` 和 `validationMode` 嵌入一份自包含脚本。配置优先级为 embedded config > `__ENV` > 安全默认值。权威 `benchmark.generated.js` 保存在对应 artifacts phase 目录；调用公共 runner 时会在已被忽略的 `.generated-runtime/` 暂存相同脚本并在结束后清理，因为公共 runner 安全地拒绝执行 `experiments/` 之外的 ScriptPath。

`handleSummary()` 按公共约定写入 RunDirectory 下的 `k6-summary.json`。机器 JSON 保留总体 `http_reqs`（请求数和吞吐率）、`http_req_duration`（平均值及 P95 等）、`http_req_failed`、`checks` 和 `benchmark_json_errors`；threshold selector 还会生成三个 endpoint 各自的请求数/吞吐、平均延迟/P95、错误率和 checks 子指标。控制台原始输出由公共 runner 保存为 `k6-console.txt`。

## 端到端 performance runner

统一入口支持四种模式：

```powershell
$runDir = Join-Path "artifacts/cloud-native" ("performance-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

# 只解析 tag 并打印部署、导入、切换和汇总计划，不启动 Docker/Kind
$env:PERFORMANCE_MODE = "dry-run"
& ".\experiments\performance\orchestrate.ps1" -RunDirectory $runDir

# 两个架构各跑一次 1 VU / 10s 严格 smoke
$env:PERFORMANCE_MODE = "smoke"
& ".\experiments\performance\orchestrate.ps1" -RunDirectory $runDir

# 课堂演示：两个架构各跑 10 VU × 1 次，10s warmup + 30s measurement + 10s cooldown
$env:PERFORMANCE_MODE = "demo"
& ".\experiments\performance\orchestrate.ps1" -RunDirectory $runDir

# 正式报告：两个架构各跑 10/50/100 VU × 3 次，共 18 次 measurement
$env:PERFORMANCE_MODE = "formal"
$env:PERFORMANCE_CONFIRM = "RUN_18_FORMAL_TESTS"
& ".\experiments\performance\orchestrate.ps1" -RunDirectory $runDir
```

Demo 使用真实 tag、数据、部署、HTTP 和清理切换，但只用于现场展示自动化，**不能作为最终正式性能数据或结论**。Formal 每轮为 2m warmup、独立 5m measurement、2m 无请求 cooldown；确认串不匹配会在 Docker 启动前失败。

入口自动执行：解析完整 tag SHA → 生成 canonical dataset 和两套 SQL → `git archive` 解出固定版本 → 部署 `midterm-check` → 等待 rollout/health → 导入并验证数据 → benchmark → 诊断 → 删除专用集群 → 部署并测试 `microservices-end` → 清理 → 双架构汇总。它不会 checkout 或修改当前分支。

两个 tag 都自带可构建 Dockerfile 和 `scripts/ci/kind-local.ps1`。单体无需额外 Dockerfile；微服务复用 Gateway、Account、Marketplace、Trading、Governance 和 Web 的现有 Dockerfile。归档工作区位于 `artifacts/cloud-native/performance-workspaces/`，部署使用专用 `campus-performance` Kind 集群，清理只删除这个集群，不接触开发者其他环境。两个架构都通过 Web Service 的 18080 port-forward 向 Docker k6 提供 `http://host.docker.internal:18080`。

Flyway 在应用启动时应用 tag 对应 migration，脚本通过 rollout 和 HTTP health 等待 Ready，不用固定启动 sleep。SQL 复制进 MySQL Pod 后以容器自己的 root 环境变量执行，不读取或记录 Secret。随后严格校验所有表数量、十个固定 item ID 以及这些 ID 均有留言。

Formal 公平性检查会拒绝 HPA，要求所有应用副本为 1，并统一 MySQL、Web 和应用资源：单体后端 2 CPU/2Gi；微服务 Gateway 加四业务服务总计 2 CPU/2Gi。Dockerfile SHA、镜像 ID、tag SHA、dataset manifest SHA、benchmark SHA、Docker/Kind/Kubernetes/主机信息均写入证据。默认 DEBUG/SQL 日志保持关闭；两个版本 Dockerfile 均使用 `-XX:MaxRAMPercentage=75`。

结果结构：

```text
<RunDirectory>/environment.json
<RunDirectory>/dataset/
<RunDirectory>/midterm-check/{metadata.json,environment.json,data-verification.json,vus-*}/
<RunDirectory>/microservices-end/{metadata.json,environment.json,data-verification.json,vus-*}/
<RunDirectory>/comparison-summary.json
<RunDirectory>/comparison-summary.csv
<RunDirectory>/evidence-index.json
```

每个 phase 保存自包含 generated JS、run metadata、k6 summary 和 console；measurement 另有公共 `collect-resources.ps1` 输出。正式 measurement 资源采样上限至少 360 秒。`validationMode=record` 保留错误率/checks/JSON 错误但不因业务 threshold 停止后续 repeat；有 summary 的失败记为 `benchmark_failed`，无 summary 记为 `infrastructure_failed`。架构失败后仍尝试诊断和清理，然后继续下一个架构；最终整体返回非零。

重新汇总已有结果：

```powershell
node experiments/performance/summarize-results.mjs --input artifacts/cloud-native/<performance-run-id> --comparison
```

逐次原始结果全部保留。每个架构/VU 的主对比值为三次中位数，并同时记录 min、max、mean；缺失或失败结果明确标记，不按 0 处理。正式数据产生前不作性能提升或回退结论。
