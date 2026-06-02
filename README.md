# Campus Second-hand Trading Platform

校园二手交易平台课程项目模板，基于软件开发计划书搭建。

## 技术栈

- 前端：HTML / CSS / JavaScript
- 后端：Java 17 / Spring Boot 3 / Spring Data JPA
- 数据库：MySQL 8
- 构建工具：Maven

## 项目结构

```text
.
├── backend/                 # Spring Boot 后端
├── frontend/                # 静态前端页面
├── database/                # MySQL 建表与初始化脚本
├── doc/                     # 项目文档
└── README.md
```

## 核心功能模块

- 用户注册 / 登录 / 个人信息管理
- 二手物品发布、浏览、分类查询、关键词搜索
- 物品详情查看
- 简易留言沟通
- 订单创建与订单状态管理

## 快速开始

1. 创建 MySQL 数据库并导入脚本：

```sql
source database/schema.sql;
source database/seed.sql;
```

2. 修改后端配置：

```text
backend/src/main/resources/application.yml
```

3. 启动后端：

```bash
cd backend
mvn spring-boot:run
```

4. 打开前端：

```text
frontend/index.html
```

默认后端接口地址为 `http://localhost:8080/api`。

