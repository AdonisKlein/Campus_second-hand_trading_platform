#!/bin/sh
# MySQL sources files in /docker-entrypoint-initdb.d into its own entrypoint
# process. Enabling `nounset` here would leak into that parent script and make
# optional MySQL variables (for example MYSQL_ONETIME_PASSWORD) fatal.
set -e
mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS campus_account CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_marketplace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_governance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'account_user'@'%' IDENTIFIED BY '${ACCOUNT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'marketplace_user'@'%' IDENTIFIED BY '${MARKETPLACE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'trading_user'@'%' IDENTIFIED BY '${TRADING_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'governance_user'@'%' IDENTIFIED BY '${GOVERNANCE_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON campus_account.* TO 'account_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON campus_marketplace.* TO 'marketplace_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON campus_trading.* TO 'trading_user'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON campus_governance.* TO 'governance_user'@'%';
FLUSH PRIVILEGES;
SQL
