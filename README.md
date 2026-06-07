# Campus Second-hand Trading Platform

校园二手交易平台课程项目，采用前后端分离的简易结构：

- `backend/`：Spring Boot 后端服务，提供用户、商品、留言、订单、邮箱验证码、图片上传等接口。
- `frontend/`：静态 HTML/CSS/JavaScript 页面，可直接通过浏览器或静态服务器访问。
- `database/`：MySQL 建库脚本和初始化数据。
- `doc/`：项目文档，例如软件开发计划书。

## 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.x
- 浏览器：Chrome、Edge 或 Firefox

## 项目结构

```text
Campus_second-hand_trading_platform/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/campus/secondhand/
│       └── resources/application.yml
├── database/
│   ├── schema.sql
│   └── seed.sql
├── doc/
│   └── 软件开发计划书.md
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

## 数据库初始化

另一台设备默认没有数据库和表，需要先安装并启动 MySQL，然后执行项目中的建库建表脚本。

### 1. 确认 MySQL 已启动

在命令行中执行：

```bash
mysql --version
```

如果能看到 MySQL 版本号，说明命令行可以识别 MySQL。然后使用 root 用户登录：

```bash
mysql -u root -p
```

输入 MySQL 密码后进入 MySQL 控制台。

### 2. 执行建库建表脚本

项目已经提供了完整建库建表脚本：

```text
database/schema.sql
```

该脚本会自动创建数据库 `campus_secondhand`，并创建用户、商品、留言、订单、邮箱验证码等表。

如果当前命令行已经位于项目根目录，可以在 MySQL 控制台中执行：

```sql
source database/schema.sql;
```

在 Windows 上，如果相对路径无法识别，可以使用绝对路径，例如：

```sql
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/schema.sql;
```

注意：MySQL 的 `source` 路径建议使用 `/`，不要使用 Windows 的 `\`，否则可能需要额外转义。

### 3. 导入测试数据

如需导入默认测试数据，继续执行：

```sql
source database/seed.sql;
```

或使用绝对路径：

```sql
source D:/Code/Software/bigwork/Campus_second-hand_trading_platform/database/seed.sql;
```

如果只想测试空数据库，可以跳过 `seed.sql`。

### 4. 检查数据库和表

执行：

```sql
SHOW DATABASES;
USE campus_secondhand;
SHOW TABLES;
```

正常情况下应能看到类似表名：

```text
users
items
messages
trade_orders
email_verification
```

也可以检查是否有测试数据：

```sql
SELECT * FROM users;
SELECT * FROM items;
```

### 5. 退出 MySQL

```sql
exit;
```

### 6. 另一种执行方式

也可以不进入 MySQL 控制台，直接在项目根目录执行：

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p campus_secondhand < database/seed.sql
```

第一条命令创建数据库和表，第二条命令导入测试数据。

## 后端配置

后端配置文件位于：

```text
backend/src/main/resources/application.yml
```

默认配置：

- 服务端口：`8080`
- 接口前缀：`/api`
- 数据库：`campus_secondhand`
- 上传目录：`uploads`

在其他设备测试前，请根据本机 MySQL 修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/campus_secondhand?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "你的MySQL密码"
```

如果暂时不测试邮箱验证码发送，需要确认邮件配置不会阻塞注册流程；如需真实发送验证码，请配置 `spring.mail` 下的 SMTP 账号信息。

## 启动后端

进入后端目录：

```bash
cd backend
```

启动服务：

```bash
mvn spring-boot:run
```

启动成功后，后端接口地址为：

```text
http://localhost:8080/api
```

如果在局域网另一台设备访问，请把 `localhost` 换成运行后端电脑的局域网 IP，例如：

```text
http://192.168.1.10:8080/api
```

## 前端配置与访问

前端接口地址配置在：

```text
frontend/assets/js/api.js
```

默认值：

```javascript
const API_BASE = "http://localhost:8080/api";
```

如果前端和后端在同一台电脑上测试，可以保持不变。

如果用其他设备访问前端，需要把这里改成后端电脑的局域网 IP，例如：

```javascript
const API_BASE = "http://192.168.1.10:8080/api";
```

然后打开前端首页：

```text
frontend/index.html
```

也可以在 `frontend/` 目录下启动一个简单静态服务器，例如：

```bash
python -m http.server 5500
```

访问：

```text
http://localhost:5500
```

局域网其他设备访问时，将 `localhost` 换成前端所在电脑的 IP。

## 主要页面

- `frontend/index.html`：首页、商品浏览、搜索筛选入口
- `frontend/register.html`：注册/登录
- `frontend/detail.html`：商品详情、留言、下单入口
- `frontend/publish.html`：发布商品
- `frontend/orders.html`：订单管理
- `frontend/profile.html`：个人中心

## 测试流程建议

1. 初始化数据库并导入测试数据。
2. 启动后端，确认 `http://localhost:8080/api` 可访问。
3. 打开前端首页。
4. 注册或登录用户。
5. 测试商品发布、搜索、详情查看。
6. 测试留言沟通。
7. 测试创建订单和订单管理。
8. 测试个人信息查看与修改。

## 常见问题

### 数据库连接失败

检查 `application.yml` 中的数据库地址、用户名、密码是否正确，并确认 MySQL 已启动。

### 其他设备无法访问后端

检查以下内容：

- 后端是否已启动。
- `frontend/assets/js/api.js` 中是否仍写着 `localhost`。
- 后端电脑和测试设备是否在同一局域网。
- Windows 防火墙是否放行 `8080` 端口。

### 前端页面能打开但接口报错

通常是 `API_BASE` 配置不正确，或后端未启动。打开浏览器开发者工具的 Network 面板，检查请求地址是否指向正确的后端 IP 和端口。

### 图片上传或图片显示异常

确认后端 `app.upload-dir` 指向的上传目录存在，并且后端进程对该目录有写入权限。

## 打包后端

如需生成 jar 包：

```bash
cd backend
mvn clean package
```

打包完成后运行：

```bash
java -jar target/secondhand-0.0.1-SNAPSHOT.jar
```
