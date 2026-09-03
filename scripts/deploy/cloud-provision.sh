#!/usr/bin/env bash
set -Eeuo pipefail

CLOUD_DOMAIN="${1:?usage: cloud-provision.sh <domain> [base-dir] [deploy-user]}"
BASE_DIR="${2:-/opt/campus-market}"
DEPLOY_USER="${3:-campus-deploy}"
ENV_FILE="${BASE_DIR}/shared/.env"

[[ "${EUID}" -eq 0 ]] || { echo "Run cloud provisioning as root" >&2; exit 2; }
[[ "${CLOUD_DOMAIN}" =~ ^[a-z0-9.-]+$ ]] || { echo "Invalid cloud domain" >&2; exit 2; }
[[ "${BASE_DIR}" =~ ^/[A-Za-z0-9._/-]+$ ]] || { echo "Invalid base directory" >&2; exit 2; }
[[ "${DEPLOY_USER}" =~ ^[A-Za-z0-9_-]+$ ]] || { echo "Invalid deploy user" >&2; exit 2; }
id "${DEPLOY_USER}" >/dev/null 2>&1 || { echo "Deploy user does not exist: ${DEPLOY_USER}" >&2; exit 2; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 3; }

install -d -m 700 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" \
  "${BASE_DIR}/shared" \
  "${BASE_DIR}/state" \
  "${BASE_DIR}/evidence" \
  "${BASE_DIR}/releases"

if [[ -e "${ENV_FILE}" ]]; then
  echo "Production env already exists; refusing to overwrite ${ENV_FILE}"
  exit 0
fi

random_hex() { openssl rand -hex 32; }
umask 077
cat > "${ENV_FILE}" <<EOF
MYSQL_ROOT_PASSWORD=$(random_hex)
ACCOUNT_DB_PASSWORD=$(random_hex)
MARKETPLACE_DB_PASSWORD=$(random_hex)
TRADING_DB_PASSWORD=$(random_hex)
GOVERNANCE_DB_PASSWORD=$(random_hex)
REDIS_PASSWORD=$(random_hex)
RABBITMQ_USERNAME=campus
RABBITMQ_PASSWORD=$(random_hex)
INTERNAL_SERVICE_TOKEN=$(random_hex)
INTERNAL_JWT_SECRET=$(random_hex)
VERIFICATION_PEPPER=$(random_hex)
CORS_ORIGINS=https://${CLOUD_DOMAIN}
SESSION_COOKIE_SECURE=true
MAIL_ENABLED=false
MAIL_HOST=smtpdm.aliyun.com
MAIL_PORT=465
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MAIL_SMTP_AUTH=true
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
MAIL_STARTTLS_REQUIRED=false
EOF
chmod 600 "${ENV_FILE}"
chown "${DEPLOY_USER}:${DEPLOY_USER}" "${ENV_FILE}"

echo "Created ${ENV_FILE} with generated secrets."
echo "MAIL_ENABLED remains false until real Aliyun SMTP credentials are configured."
