# 校园二手交易平台

校园二手交易平台是一个面向校园学生的课程项目，用于完成二手物品发布、浏览搜索、留言沟通、下单、订单管理、个人资料维护和管理员管理等基础交易流程。

项目采用简易前后端分离架构：

- 前端：静态 HTML/CSS/JavaScript 页面。
- 后端：Spring Boot REST API。
- 数据库：MySQL。
- 测试：JUnit 5、MockMvc、H2。

## 功能概览

当前项目已实现：

- 用户注册、用户名登录、邮箱登录。
- 邮箱验证码注册、修改邮箱和忘记密码重置。
- 连续 3 次登录失败后账号临时锁定 10 分钟。
- 个人资料查看和修改。
- 商品浏览、关键词搜索、分类筛选和详情查看。
- 登录用户发布商品。
- 商品详情页留言、修改自己的留言、删除自己的留言。
- 创建订单、查看相关订单、更新订单状态。
- 创建订单后商品状态变为 `SOLD`。
- 管理员禁用/恢复普通用户、删除留言、下架/恢复商品。

当前实现边界：

- 商品图片使用图片地址或静态资源路径，不支持本地文件上传。
- 前端使用 `localStorage` 保存当前用户基本信息，未引入 Token 或 Session 鉴权。
- 管理员接口根据请求中的 `adminId` 校验管理员角色，适合课程项目演示。
- 邮箱验证码需要配置可用 SMTP 服务后才能真实发送邮件。

## 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | HTML、CSS、JavaScript |
| 后端 | Java 24、Spring Boot 3.5.14、Spring Web、Spring Data JPA |
| 数据库 | MySQL 8.x |
| 安全相关 | Spring Security Crypto BCrypt |
| 邮件 | Spring Boot Mail |
| 测试 | JUnit 5、Spring Boot Test、MockMvc、H2 |
| 构建 | Maven |

## 目录结构

```text
Campus_second-hand_trading_platform/
├── backend/                 Spring Boot 后端
├── database/                数据库脚本
│   ├── schema.sql
│   └── seed.sql
├── doc/                     项目文档
├── frontend/                静态前端页面
│   ├── index.html
│   ├── register.html
│   ├── detail.html
│   ├── publish.html
│   ├── orders.html
│   ├── profile.html
│   ├── admin.html
│   └── assets/
└── README.md
```

## 环境要求

- JDK 24.0.2。
- Maven 3.8 或以上。
- MySQL 8.x。
- Chrome、Edge 或 Firefox。
- Python 3.x，可选，用于启动前端静态服务器。

## 数据库初始化

登录 MySQL：

```bash
mysql -u root -p
```

在 MySQL 控制台执行：

```sql
source database/schema.sql;
source database/seed.sql;
```

Windows 上建议使用绝对路径，并使用 `/` 分隔路径：

```sql
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/schema.sql;
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/seed.sql;
```

默认数据库名：

```text
campus_secondhand
```

演示管理员账号：

```text
用户名：admin
密码：abc123
```

## 后端配置与启动

后端配置文件：

```text
backend/src/main/resources/application.yml
```

重点检查数据库账号密码：

```yaml
spring:
    datasource:
        url: jdbc:mysql://localhost:3306/campus_secondhand?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
        username: root
        password: "你的MySQL密码"
```

后端默认端口和接口前缀：

```yaml
server:
    port: 8080
    servlet:
        context-path: /api
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

后端接口地址：

```text
http://localhost:8080/api
```

健康检查：

```text
http://localhost:8080/api/actuator/health
```

## 前端访问

前端接口地址配置：

```text
frontend/assets/js/api.js
```

默认配置：

```javascript
const API_BASE = "http://localhost:8080/api";
```

可直接打开：

```text
frontend/index.html
```

也可以在 `frontend/` 目录启动静态服务器：

```bash
cd frontend
python -m http.server 5500
```

然后访问：

```text
http://localhost:5500/index.html
```

## 主要页面

| 页面 | 文件 | 功能 |
|---|---|---|
| 首页 | `frontend/index.html` | 商品列表、关键词搜索、分类筛选。 |
| 注册页 | `frontend/register.html` | 用户注册和邮箱验证码发送。 |
| 详情页 | `frontend/detail.html` | 商品详情、留言列表、发送留言、创建订单。 |
| 发布页 | `frontend/publish.html` | 发布商品，图片字段为图片地址。 |
| 订单页 | `frontend/orders.html` | 查看相关订单并更新订单状态。 |
| 个人中心 | `frontend/profile.html` | 登录、忘记密码、资料查看、资料修改、退出登录。 |
| 管理中心 | `frontend/admin.html` | 普通用户管理、留言管理、商品管理。 |

## 运行测试

后端自动化测试使用 `test` profile 和 H2 内存数据库，不依赖本地 MySQL。

执行：

```bash
cd backend
mvn test
```

当前自动化测试覆盖：

- Spring Boot 上下文加载。
- 用户注册、用户名/邮箱登录和错误密码处理。
- 登录失败锁定。
- 忘记密码验证码重置。
- 商品发布和搜索。
- 留言发送、查询、修改和删除。
- 管理员禁用用户、删除留言、下架商品。
- 创建订单、重复下单拦截、订单状态校验和商品售出联动。

## 项目文档

| 文档 | 说明 |
|---|---|
| `doc/安装手册.md` | 环境准备、数据库初始化、前后端启动和常见问题。 |
| `doc/用户手册.md` | 普通用户和管理员的页面操作说明。 |
| `doc/测试报告.md` | 自动化测试、手工测试、数据库测试和测试结论。 |
| `doc/后端测试.md` | 后端测试技术、执行方式和测试用例说明。 |
| `doc/需求说明书.md` | 功能需求、非功能需求、数据需求和实现边界。 |
| `doc/设计文档.md` | 系统架构、模块设计、接口设计和数据库设计。 |
| `doc/软件开发计划书.md` | 开发计划、资源安排、风险管理和质量保证。 |

## 常见问题

### 数据库连接失败

检查 MySQL 是否启动、`campus_secondhand` 是否已创建，以及 `application.yml` 中用户名和密码是否正确。

### 表结构校验失败

后端配置为 `spring.jpa.hibernate.ddl-auto: validate`，不会自动创建或修改表结构。请先执行 `database/schema.sql`，旧库结构不一致时建议备份后重建数据库。

### 前端请求失败

检查后端是否已启动，浏览器 Network 面板中的请求地址是否为：

```text
http://localhost:8080/api
```

### 邮箱验证码发送失败

检查 `application.yml` 中的 `spring.mail` 是否配置为真实可用的 SMTP 服务。
