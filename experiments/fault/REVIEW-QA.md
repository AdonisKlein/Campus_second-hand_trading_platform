# 工作项 10 评审追问答复清单

面向答辩/评审口头问答。先用「先说」卡住口径，再用「如追问」补实现细节。所有数字来自正式 run `fault-20260902T074227Z-22133ba3` 与当前 Trading 代码。

---

## A. 选题与边界

### A1. 为什么只隔离 Trading → Marketplace，而不是所有远程调用？

**先说：** 工作项 10 的故障域是「创建购买意向必须读商品快照」。这条读路径一旦拖死，会把超时、半条订单和 Trading 探针一起拖垮。Account 画像、治理命令不在本项验收范围。

**如追问：** Marketplace 读取已收口到唯一 seam `MarketplaceDependency.executeRead`。Account 仍走 `OwnedRemoteAdapter`（超时 + GET 重试一次，错误码 `DEPENDENCY_UNAVAILABLE`），没有单独熔断。评审若问「不完整」，答：这是分工边界，不是否定 Account 也该保护；本项证明的是交易写路径不会被商品服务拖死。

### A2. 为什么故障注入是 scale Marketplace 到 0，而不是延迟注入或 Chaos Mesh？

**先说：** 计划明确允许「停止 Marketplace 或注入延迟」。scale 到 0 能同时证明：连接失败计入熔断、OPEN 后快速失败、数据库无副作用。Kind 单节点上这是可复现、不引入新组件的注入方式。

**如追问：** 超时路径由单元测试覆盖（MockWebServer 延迟 400ms，断言映射为 503 且总耗时 < 1.5s）。集群实验选「依赖消失」而不是「变慢」，是为了让 8 次调用稳定打满熔断窗口，避免延迟实验和 Gateway 超时叠在一起说不清。

### A3. 为什么不返回缓存商品或降级成「仍可下单」？

**先说：** 计划禁止伪造商品。购买意向必须基于 Marketplace 当时的交易快照；猜一个价格/状态会破坏预留和归属。正确降级是明确失败：503 + 固定文案 + 不写订单。

---

## B. 技术选型

### B1. 为什么用 Resilience4j 2.4.0，而不是 Spring Retry、自己写计数器、或服务网格熔断？

**先说：** 项目是 Spring Boot 4.0.8。Resilience4j 2.4.0 提供 `resilience4j-spring-boot4`，状态机、滑动窗口、忽略业务异常、OPEN→HALF_OPEN 自动切换都现成。不在集群里加 Istio，是因为本项要证明**应用内**隔离，证据在 Trading 日志和自己的库，不依赖网格。

**如追问：** 自己写计数器会把失败分类、半开探测、线程切换全做成私有逻辑，难测也难答辩。服务网格熔断看不到「有没有写 Outbox」，回答不了业务无副作用。

### B2. 调用栈怎么走？超时、重试、熔断谁包谁？

**先说：** 业务只调 `executeRead`。内部顺序是：

1. Reactor Netty **连接超时 300ms**（`InternalWebClients` / `CONNECT_TIMEOUT_MILLIS`）
2. Mono **响应超时 800ms**（`.timeout(Duration.ofMillis(...))`）
3. 每次 GET 包在 `circuitBreaker.executeSupplier` 里
4. 仅当第一次是 `MarketplaceFailureException` 时，**再执行一次**同样的 GET

**关键口径：** 重试在熔断外层。一次用户请求最多记 **两次** 熔断样本。OPEN 之后 `CallNotPermittedException` 不再打 Marketplace，直接 503。

**不要说错：** 不是 WebClient `.retry(1)` 套在 Marketplace 这条路径上。那是 Account adapter 的旧写法。

### B3. 为什么只重试 GET、且只一次？

**先说：** GET 交易快照幂等。写订单、改状态不是这条 client 的事。重试一次用来挡偶发空窗；再多会在 Marketplace 已慢时把尾延迟放大，并更快把窗口填满失败。

**如追问：** `isRetryable` 只认 `MarketplaceFailureException`。4xx 业务异常不重试。

---

## C. 熔断参数（最容易被追问「数字怎么来的」）

配置在 `application.yml` 的 `marketplaceReads`：

| 参数 | 值 | 答辩说法 |
| --- | --- | --- |
| 窗口类型 | COUNT_BASED | 实验是突发失败，按次数比按时间窗口更可复现 |
| 窗口大小 | 10 | 最近 10 次采样 |
| 最少调用 | 5 | 不到 5 次不算失败率，避免冷启动误跳闸 |
| 失败率阈值 | 50% | 一半失败即打开 |
| 打开停留 | 15s | 给 Marketplace 拉起 + 探针 Ready 留时间 |
| 半开探测 | 2 | 连续两次成功才闭合，降低抖动 |
| 自动半开 | true | 现场日志里的 `CircuitBreakerAutoTransitionThread` |
| health | false | 见 C5 |

### C1. 现场为什么第二次故障请求就 OPEN，失败率还是 80%？

**先说：** 打开前窗口里已经有预置订单的 **1 次成功** + 故障后的失败（含 GET 重试）。日志原文：`from=CLOSED to=OPEN failureRate=80.0 bufferedCalls=5 failedCalls=4`。5 次采样里 4 次失败 = 80% ≥ 50%，且已满最少 5 次。

**如追问：** 用户侧看到 8 次 503，不等于熔断窗口里只有 8 个点。OPEN 之后的请求被短路，不再记成新的远程失败；延迟从约 70ms 落到约 20–30ms，这正是熔断生效的证据。

### C2. HALF_OPEN 的 -1.0 failureRate 是不是坏了？

**先说：** 不是。半开/刚切换时样本不足，Resilience4j 对失败率返回 `-1` 表示「尚未统计」。日志仍要打这个字段，便于和 CLOSED/OPEN 同一行格式对齐。

### C3. 为什么恢复探测出现一次 HTTP 409？还算不算恢复成功？

**先说：** 算。半开允许 2 次。第一次 `POST /api/orders` 已 200 并写入订单 id=5，熔断在第二次探测时闭合。第二次对**同一商品**再建意向，业务上冲突，返回 409。409 是交易规则，不是 Marketplace 宕机。

### C4. 为什么不用 TIME_BASED 窗口？

**先说：** 课程实验要「同一脚本、同一集群、结果可对」。COUNT_BASED 与「连打 8 次」一一对应。TIME_BASED 会跟 QPS、等待 15s 搅在一起，现场不好解释。

### C5. 为什么 `registerHealthIndicator: false`，还关了 `management.health.circuitbreakers`？

**先说：** 熔断 OPEN 表示 **Marketplace 坏了**，不是 Trading 自己坏了。如果把 circuit 挂进 readiness，kubelet 会重启好的 Trading，实验要证明的「隔离」就失败了。

**如追问：** Trading liveness/readiness 在故障窗口日志里持续 HTTP 200；`tradingRestarted=false`；Pod Restarts=0。这三条要一起说。

### C6. 为什么不暴露熔断状态管理接口？

**先说：** 计划要求状态只走结构化日志。管理接口会变成新的攻击面和契约负担。实验从 `kubectl logs` 抽 `marketplace circuit transition from={} to={}` 即可。

---

## D. 失败分类与数据正确性

### D1. 哪些计入熔断，哪些不计入？

| 情况 | 类型 | 计入失败？ | 对外 |
| --- | --- | --- | --- |
| 超时、连接失败、5xx | `MarketplaceFailureException` | 是（`recordExceptions`） | 503 `PRODUCT_SERVICE_UNAVAILABLE` |
| 熔断 OPEN 短路 | `CallNotPermittedException` | 打开后不再访问 Marketplace | 同样 503 |
| 404 | `getOnce` 返回空 Map | **否**（当成功空结果） | 业务层「商品不存在」 |
| 其他 4xx | `MarketplaceBusinessException` | **否**（`ignoreExceptions`） | 参数/商品无效，不是 503 |
| 写订单本身 | 不走这条 client | — | 失败发生在读快照之后、落库之前 |

### D2. 如何保证「没有半条订单、没有 Outbox」？

**先说：** 创建意向是先 `executeRead` 拿快照，失败则抛 `TradingException.productUnavailable()`，**到不了** `TradingService` 的持久化。实验用同一库计数：故障前 4、故障中 4、恢复后 5。

**如追问：** 集成测试 `MarketplaceFaultIsolationTest` 断言 503、`Retry-After: 1`、code，并用仓库/SQL 看最终状态。集群实验再数一次真实 MySQL。

### D3. 503 文案和 Retry-After 在哪一层加的？

**先说：** 异常工厂 `TradingException.productUnavailable()` 固定文案「商品服务暂时不可用，请稍后重试」。`TradingExceptionHandler` 看到 `503` 就加 `Retry-After: 1`。Gateway 只是透传，不改写 code。

**如追问：** `Retry-After: 1` 是给客户端的提示，不是熔断 15s。熔断打开时立即失败；1 秒只表示「可以很快再试」，真正恢复仍要等 Marketplace Ready + 半开探测。

---

## E. 可观测性、相关 ID、安全

### E1. correlationId 怎么证明一次失败能串起来？

**先说：** 入口 `CorrelationIdFilter` 写入 MDC；内部调用带 `X-Correlation-Id`。熔断跳转日志带同一个 `correlationId`。OPEN 那一行是 `10315022b5154a02`，可在 `trading-circuit.log` 里对上同一次 `POST /api/orders`。

### E2. 内部调用如何鉴权？

**先说：** 每条 Marketplace GET 带 `X-Internal-Service-Token`。这是服务间令牌，不是把用户 Session 转发出去。Web 身份仍只相信服务端 Session，购买意向的买家来自当前登录用户，不从请求体伪造。

### E3. ECS 日志格式是什么意思？

**先说：** `logging.structured.format.console: ecs`。实验脚本按字段 `message` 匹配 `marketplace circuit transition`，不靠彩色控制台。

---

## F. 实验与证据（针对现场数字）

### F1. 最大延迟只有 71ms，800ms 超时是不是没起作用？

**先说：** 起作用的是「失败要快」。scale 到 0 后连接立刻失败，远低于 800ms。71ms 是 Gateway 往返上限，说明没有卡满超时。超时保护的是「Marketplace 变慢」；本 run 验证的是「Marketplace 消失」。两种失败都映射同一 503。

### F2. `circuit-transitions.txt` 为什么有两轮 CLOSED→OPEN→…？

**先说：** 那是同一 Trading Pod 的完整日志拼接，含上一轮几乎成功的 run。**PPT 只用本 run 15:42:41–15:43:19 四条。** 正式判定看 `result.json` = PASS 和 `fault-summary.json` 的 503/订单数。

### F3. 为什么实验挂在成员 A 的 `experiments/run.ps1` 上，C 不自己写环境采集？

**先说：** 四人约定：A 管 runId、environment.json、诊断、哈希、`result.json`。C 只实现 `experiments/fault/run.ps1`（造数、停/恢复 Marketplace、断言 503 和无副作用）。复制一套 harness 会分叉证据格式。

### F4. 单元测试过了，为什么还要 Kind？

**先说：** 单元测试证明映射和熔断状态机。Kind 证明：真实 Deployment 消失时，**别的 Pod 仍 Ready、Trading 不被 kubelet 重启、MySQL 行数不变、15s 后半开自动恢复**。这是课程要求的集群证据。

### F5. 准备阶段曾把 Gateway 超时调到 3000ms，算不算改了实验对象？

**先说：** 那是 **Gateway 调 Account 登录**（本机 bcrypt 偏慢）的运维调整，不是 Trading→Marketplace 的 800ms。故障阶段测的是购买意向，熔断参数未改。若追问是否进仓库：这是集群现场 `kubectl set env`，不是把 Marketplace 读超时放宽。

### F6. 故障 overlay `k8s/overlays/fault` 做了什么？

**先说：** 叠在 CI overlay 上，固定各应用副本为 1，避免 HPA/调试副本干扰「停一个服务」的对照。不改熔断业务代码。

---

## G. 局限（主动说，避免被问倒）

1. **未在集群里做延迟注入。** 超时路径以单测为准；现场是依赖消失。
2. **Account 远程调用尚未同等熔断。** 范围外。
3. **OPEN 时既有订单仍可读**，因为读的是 Trading 自己的库；不要说成「整个交易模块只读降级」。
4. **半开第二次 409** 是业务冲突，讲解时主动点明，避免被当成恢复失败。
5. **原始 artifacts 不进 Git。** 课程附件目录 `artifacts/cloud-native/fault-20260902T074227Z-22133ba3/`。

---

## H. 30 秒总述（开场或被问「你做了什么」）

Trading 把商品读取收进 `MarketplaceDependency`，用 Resilience4j 2.4.0 对网络/超时/5xx 熔断，404 和业务 4xx 不跳闸。Marketplace 停掉后，新购买意向 8 次都是 71ms 内的 503，订单数仍是 4，Account/Governance/Trading Ready 且 Trading 零重启。15 秒后半开探测成功，熔断闭合，新订单 id=5。证据在 Kind 实验 PASS 包和 Trading 27 项测试。

---

## I. 代码索引（被要求「指到文件」时）

| 话题 | 位置 |
| --- | --- |
| 唯一读取 seam | `MarketplaceDependency` / `MarketplaceDependencyClient` |
| 连接超时 | `InternalWebClients` |
| 熔断配置 | `application.yml` → `resilience4j.circuitbreaker` |
| 状态日志 | `MarketplaceCircuitEvents` |
| 503 文案 | `TradingException.productUnavailable()` |
| Retry-After | `TradingExceptionHandler` |
| 适配器改接线 | `HttpMarketplaceAdapter` 调 `executeRead("trade-snapshot", …)` |
| 单测 | `MarketplaceDependencyTest`、`MarketplaceFaultIsolationTest` |
| Kind adapter | `experiments/fault/run.ps1` |
| 正式证据 | `artifacts/cloud-native/fault-20260902T074227Z-22133ba3/` |
