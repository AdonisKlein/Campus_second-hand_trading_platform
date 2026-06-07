# Campus Second-hand Trading Platform

校园二手交易平台是一个课程实践项目，用于完成校园内二手物品发布、浏览、留言沟通、下单和订单管理流程。

当前项目采用简易前后端分离结构：

- `backend/`：Spring Boot 后端，提供 REST API。
- `frontend/`：静态 HTML/CSS/JavaScript 前端页面。
- `database/`：MySQL 建表脚本和演示数据。
- `doc/`：需求、设计、安装、用户、测试等项目文档。

## 功能概览

已实现功能：

- 用户注册、登录。
- 邮箱验证码发送与校验。
- 密码 BCrypt 加密存储。
- 连续 3 次登录失败后锁定账号 10 分钟。
- 个人资料查看和修改。
- 商品浏览、关键词搜索、分类筛选。
- 商品发布，当前图片字段为图片地址。
- 商品详情查看。
- 商品留言发送与列表查看。
- 创建订单。
- 订单列表查看。
- 订单状态更新：`CONFIRMED`、`COMPLETED`、`CANCELLED`。
- 下单后商品状态更新为 `SOLD`。

当前简化点：

- 未实现真实本地文件上传，商品图片使用图片地址。
- 前端登录态使用 `localStorage` 保存用户基本信息。
- 未实现 Token 或 Session 权限体系。
- 留言按商品展示，没有独立私信会话页。

## 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | HTML、CSS、JavaScript |
| 后端 | Java 17、Spring Boot 3.5.14 |
| 数据库 | MySQL 8.x |
| ORM | Spring Data JPA |
| 构建 | Maven |
| 测试 | JUnit 5、MockMvc、H2 |
| 安全 | Spring Security Crypto BCrypt |

## 项目结构

```text
Campus_second-hand_trading_platform/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/campus/secondhand/
│       │   └── resources/application.yml
│       └── test/
├── database/
│   ├── schema.sql
│   └── seed.sql
├── doc/
│   ├── 安装手册.md
│   ├── 测试报告.md
│   ├── 后端测试.md
│   ├── 软件开发计划书.md
│   ├── 设计文档.md
│   ├── 需求说明书.md
│   └── 用户手册.md
├── frontend/
│   ├── index.html
│   ├── register.html
│   ├── detail.html
│   ├── publish.html
│   ├── orders.html
│   ├── profile.html
│   └── assets/
└── README.md
```

## 环境要求

- JDK 17 或以上。
- Maven 3.8 或以上。
- MySQL 8.x。
- Chrome、Edge 或 Firefox。

检查环境：

```bash
java -version
mvn -version
mysql --version
```

## 数据库初始化

### 1. 登录 MySQL

```bash
mysql -u root -p
```

### 2. 创建数据库和表

在 MySQL 控制台执行：

```sql
source database/schema.sql;
```

如果相对路径不可用，请使用绝对路径，例如：

```sql
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/schema.sql;
```

### 3. 导入演示数据

```sql
source database/seed.sql;
```

或：

```sql
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/seed.sql;
```

### 4. 检查表

```sql
USE campus_secondhand;
SHOW TABLES;
```

应看到：

```text
users
items
messages
trade_orders
email_verification
```

### 5. 旧数据库升级

如果之前已经创建过数据库，但 `users` 表缺少登录锁定字段，请执行：

```sql
ALTER TABLE users
ADD COLUMN login_failed_count INT NOT NULL DEFAULT 0,
ADD COLUMN locked_until DATETIME NULL;
```

## 后端配置

配置文件：

```text
backend/src/main/resources/application.yml
```

默认端口和接口前缀：

```yaml
server:
    port: 8080
    servlet:
        context-path: /api
```

数据库配置需要按本机 MySQL 修改：

```yaml
spring:
    datasource:
        url: jdbc:mysql://localhost:3306/campus_secondhand?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
        username: root
        password: "你的MySQL密码"
```

邮箱验证码依赖 SMTP 配置。如需真实发送邮件，请修改：

```yaml
spring:
    mail:
        host: smtp.mailtrap.io
        port: 2525
        username: YOUR_MAILTRAP_USERNAME
        password: YOUR_MAILTRAP_PASSWORD
```

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

启动成功后，接口基础地址为：

```text
http://localhost:8080/api
```

## 前端配置

前端 API 地址在：

```text
frontend/assets/js/api.js
```

默认值：

```javascript
const API_BASE = "http://localhost:8080/api";
```

如果用另一台设备访问后端，请改为后端设备的局域网 IP，例如：

```javascript
const API_BASE = "http://192.168.1.10:8080/api";
```

## 打开前端

方式一：直接打开首页：

```text
frontend/index.html
```

方式二：启动静态服务器：

```bash
cd frontend
python -m http.server 5500
```

访问：

```text
http://localhost:5500
```

## 主要页面

| 页面 | 文件 | 功能 |
|---|---|---|
| 首页 | `frontend/index.html` | 商品浏览、搜索、筛选。 |
| 注册 | `frontend/register.html` | 注册账号、发送邮箱验证码。 |
| 详情 | `frontend/detail.html` | 商品详情、留言、创建订单。 |
| 发布 | `frontend/publish.html` | 发布商品。 |
| 订单 | `frontend/orders.html` | 查看订单、更新订单状态。 |
| 个人中心 | `frontend/profile.html` | 登录、资料查看修改、退出登录。 |

## 测试

后端自动化测试：

```bash
cd backend
mvn test
```

当前测试覆盖：

- Spring 上下文加载。
- 注册、登录、错误密码。
- 3 次登录失败锁定。
- 商品发布和搜索。
- 留言发送和查询。
- 订单创建、重复下单拦截、订单状态校验、商品售出联动。

期望结果：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 局域网其他设备测试

如果在 A 电脑启动后端，在 B 电脑打开前端：

1. 确认两台设备在同一局域网。
2. 查询 A 电脑局域网 IP，例如 `192.168.1.10`。
3. 修改 `frontend/assets/js/api.js`：

```javascript
const API_BASE = "http://192.168.1.10:8080/api";
```

4. 确认 A 电脑防火墙允许访问 8080 端口。
5. 在 B 电脑打开前端页面或访问静态服务器地址。

## 文档

详细文档位于 `doc/`：

- `需求说明书.md`：系统功能和非功能需求。
- `设计文档.md`：架构、模块、接口和数据库设计。
- `安装手册.md`：新设备部署步骤。
- `用户手册.md`：用户操作流程。
- `测试报告.md`：测试范围和结果。
- `后端测试.md`：后端自动化测试说明。
- `软件开发计划书.md`：项目计划。

## 常见问题

### 数据库连接失败

检查 MySQL 是否启动，`application.yml` 中用户名、密码、数据库名是否正确。

### 前端页面能打开但接口失败

检查后端是否启动，以及 `frontend/assets/js/api.js` 中 `API_BASE` 是否正确。

### 邮箱验证码发送失败

检查 `spring.mail` 配置是否为真实可用的 SMTP 服务。

### 商品图片不显示

当前商品图片为图片地址，请确认地址能被浏览器访问。
