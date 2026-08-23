# 第一轮账号安全升级

`V2__account_security.sql` 用于从旧版数据库升级到第一轮账号模型。执行前请先备份数据库。

先执行以下检查；任何查询有结果时，先人工处理，不能直接运行迁移：

```sql
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*)
FROM users
GROUP BY LOWER(TRIM(email))
HAVING normalized_email IS NULL OR normalized_email = '' OR COUNT(*) > 1;
```

确认无空邮箱和重复邮箱后，再执行迁移脚本。旧验证码会全部失效，这是有意的安全处理；用户需要重新获取验证码。

当前项目尚未启用 Flyway 自动迁移，因此生产部署不能只更新 JAR，必须先完成这里的数据库升级。下一轮会把基线和增量脚本正式接入 Flyway。
