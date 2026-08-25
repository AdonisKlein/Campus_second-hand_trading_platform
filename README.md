# 校园二手交易平台

面向在校学生的二手商品发布、沟通和当面交易平台。目前处于企业化重构阶段。

## 当前能力

- 游客浏览、使用多关键词搜索商品或用户，并在搜索后按活跃、同区域、信用、价格、校园区域和商品标签筛选排序。
- 邮箱验证码注册、邮箱密码登录、找回密码。
- 服务端 Session 登录，支持 CSRF、精确 CORS 和管理员权限复核。
- 学生发布带校园区域和交易标签的商品、管理自己的发布、留言、下单和查看自己的订单。
- 买家可从商品详情发起仅买卖双方可见的私聊；支持未读消息、历史分页、屏蔽与举报，公开留言和私聊严格分开。
- 商品图片由平台受控上传，限制格式、体积和尺寸，并清除照片元数据。
- 买家先提交非独占购买意向，商品继续在售；卖家选定一位买家后才预留，其他意向关闭；当面交接后由买家确认完成。
- 商品详情一次展示安全卖家摘要、当前用户的购买意向、可执行动作、同卖家在售商品、公开问答与校园交易提醒；桌面和移动使用各自适配布局。
- 订单工作台按“我买到的 / 我卖出的”和交易阶段组织记录；卖家按商品比较买家公开信用，买家查看时间线、关闭原因并可从订单继续私聊。
- 订单保存下单时的商品标题、价格和双方昵称，不受后续资料修改影响。
- 学生可举报商品、留言或用户并查看处理结果；管理员通过举报队列执行下架、移除、禁用或驳回并保留审计记录。
- 管理员管理用户、商品和留言。
- Flyway 从空数据库建立并校验表结构。
- Spring Session JDBC 让多个 Web 后端实例共享登录状态。

## 登录方案

Web 端使用 HttpOnly Cookie + 服务端 Session，浏览器 JavaScript 不能读取登录凭据，并且服务器可以立即注销、封禁或撤销全部旧会话。

Windows 和移动端后续会增加短期访问令牌与可撤销刷新令牌，不把长期 JWT 存入 Web 的 localStorage。两种客户端共享用户、角色和会话撤销规则。

## 技术栈

- 前端：HTML、CSS、JavaScript，Nginx 同源代理 `/api`
- 后端：Java 25、Spring Boot 3.5、Spring Security、Spring Data JPA
- 数据库：MySQL 8.4、Flyway
- 会话：Spring Session JDBC；高并发阶段可替换为 Redis adapter
- 测试：JUnit 5、MockMvc、H2、Testcontainers MySQL 8.4，测试时也真实执行 Flyway

## 本地开发

先安装 JDK 25。Windows 可在管理员 PowerShell 中运行：

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK --exact
```

安装后重新打开终端，用 `java -version` 和 `mvn -version` 确认二者都指向 Java 25。若只使用下面的 Docker 部署，则不需要在宿主机单独安装 JDK。

环境变量参考 [backend/.env.example](backend/.env.example)。至少需要：

```text
DB_URL=jdbc:mysql://localhost:3306/campus_secondhand?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=你的数据库密码
VERIFICATION_PEPPER=至少32位随机字符串
```

创建空数据库（PowerShell 推荐进入 MySQL 后使用 `source`）：

```powershell
mysql -u root -p
```

```sql
source D:/你的项目路径/Campus_second-hand_trading_platform/database/schema.sql;
```

在启动后端的同一个 PowerShell 设置 `DB_*`、`VERIFICATION_PEPPER` 和 CORS 环境变量；Spring Boot 不会自动加载 `.env.example`。完整命令见 [本地部署与运行文档](doc/软件部署文档.md)。

启动后端，Flyway 会自动创建全部表：

```powershell
cd backend
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
python -m http.server 5500
```

访问 `http://localhost:5500`。接口默认是 `http://localhost:8080/api`。

不要直接用 `file://` 双击打开 HTML；Cookie、CSRF 和跨域行为需要 HTTP 静态服务器。

## 一键部署

完整的全新数据库 Docker 部署说明见 [deploy/README.md](deploy/README.md)，本机开发、测试和排障见 [软件部署文档](doc/软件部署文档.md)：

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

## 测试

```powershell
cd backend
mvn test
```

快速测试使用 H2；Docker 可用时还会启动真正的 MySQL 8.4，验证空库迁移、Hibernate 映射和 JDBC Session 表。Hibernate 只负责 `validate`，不会用自动建表掩盖迁移错误。

## 主要目录

```text
backend/                              Spring Boot 后端与 Dockerfile
backend/src/main/resources/db/migration/  Flyway 数据库基线
frontend/                             Web 页面、Nginx 配置与 Dockerfile
database/schema.sql                   仅创建空数据库
database/seed.sql                     可选本地演示数据
deploy/                               Docker Compose 全新部署方案
doc/                                  产品、设计和测试文档
CONTEXT.md                            已确认的业务词汇
AGENTS.md                             AI/自动化开发者入口与完成定义
docs/ai/                              AI 项目上下文和持续工作日志
```

AI 或新协作者请从 [AGENTS.md](AGENTS.md) 开始阅读。
