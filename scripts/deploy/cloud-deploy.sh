#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_NAMESPACE="${1:?usage: cloud-deploy.sh <image-namespace> <sha-tag> <domain> [base-dir]}"
IMAGE_TAG="${2:?usage: cloud-deploy.sh <image-namespace> <sha-tag> <domain> [base-dir]}"
CLOUD_DOMAIN="${3:?usage: cloud-deploy.sh <image-namespace> <sha-tag> <domain> [base-dir]}"
BASE_DIR="${4:-/opt/campus-market}"
RELEASE_DIR="${BASE_DIR}/releases/${IMAGE_TAG}"
ENV_FILE="${BASE_DIR}/shared/.env"
STATE_DIR="${BASE_DIR}/state"
EVIDENCE_DIR="${BASE_DIR}/evidence/${IMAGE_TAG}"
COMPOSE_PROJECT_NAME="campus-market"
PREVIOUS_TAG=""

[[ "${IMAGE_NAMESPACE}" =~ ^[a-z0-9./_-]+$ ]] || { echo "Invalid image namespace" >&2; exit 2; }
[[ "${IMAGE_TAG}" =~ ^sha-[0-9a-f]{7}$ ]] || { echo "Invalid immutable image tag" >&2; exit 2; }
[[ "${CLOUD_DOMAIN}" =~ ^[a-z0-9.-]+$ ]] || { echo "Invalid cloud domain" >&2; exit 2; }
[[ "${BASE_DIR}" =~ ^/[A-Za-z0-9._/-]+$ ]] || { echo "Invalid base directory" >&2; exit 2; }

command -v docker >/dev/null || { echo "Docker is not installed" >&2; exit 3; }
docker compose version >/dev/null || { echo "Docker Compose v2 is not installed" >&2; exit 3; }
[[ -f "${ENV_FILE}" ]] || { echo "Missing production env file: ${ENV_FILE}" >&2; exit 4; }
[[ -f "${RELEASE_DIR}/deploy/docker-compose.yml" ]] || { echo "Release files are incomplete: ${RELEASE_DIR}" >&2; exit 4; }

install -d -m 700 "${STATE_DIR}" "${EVIDENCE_DIR}"
if [[ -f "${STATE_DIR}/current-image-tag" ]]; then
  PREVIOUS_TAG="$(tr -d '\r\n' < "${STATE_DIR}/current-image-tag")"
fi

export IMAGE_NAMESPACE IMAGE_TAG
export WEB_BIND_ADDRESS="127.0.0.1"
export WEB_PORT="${WEB_PORT:-18080}"

compose() {
  docker compose \
    --project-name "${COMPOSE_PROJECT_NAME}" \
    --env-file "${ENV_FILE}" \
    -f "${RELEASE_DIR}/deploy/docker-compose.yml" \
    -f "${RELEASE_DIR}/deploy/docker-compose.production.yml" \
    "$@"
}

capture_diagnostics() {
  compose ps --all > "${EVIDENCE_DIR}/compose-ps.txt" 2>&1 || true
  compose logs --no-color --tail 300 > "${EVIDENCE_DIR}/compose-logs.txt" 2>&1 || true
  docker stats --no-stream > "${EVIDENCE_DIR}/docker-stats.txt" 2>&1 || true
  free -h > "${EVIDENCE_DIR}/memory.txt" 2>&1 || true
  df -h / > "${EVIDENCE_DIR}/disk.txt" 2>&1 || true
}

rollback() {
  local exit_code="$1"
  trap - ERR
  echo "Deployment of ${IMAGE_TAG} failed (exit ${exit_code})." >&2
  capture_diagnostics
  if [[ "${PREVIOUS_TAG}" =~ ^sha-[0-9a-f]{7}$ && "${PREVIOUS_TAG}" != "${IMAGE_TAG}" ]]; then
    echo "Rolling back application images to ${PREVIOUS_TAG}." >&2
    export IMAGE_TAG="${PREVIOUS_TAG}"
    compose up -d --no-build --remove-orphans || true
    capture_diagnostics
  fi
  exit "${exit_code}"
}
trap 'rollback $?' ERR

grep -Fxq "CORS_ORIGINS=https://${CLOUD_DOMAIN}" "${ENV_FILE}" || {
  echo "CORS_ORIGINS must be exactly https://${CLOUD_DOMAIN} in ${ENV_FILE}" >&2
  exit 5
}
grep -Fxq "SESSION_COOKIE_SECURE=true" "${ENV_FILE}" || {
  echo "SESSION_COOKIE_SECURE=true is required for cloud deployment" >&2
  exit 5
}

compose config --quiet
compose pull
compose up -d --no-build --remove-orphans --wait --wait-timeout 600

curl --fail --silent --show-error --retry 20 --retry-delay 3 \
  "http://127.0.0.1:${WEB_PORT}/index.html" > "${EVIDENCE_DIR}/index.html"
curl --fail --silent --show-error --retry 20 --retry-delay 3 \
  "http://127.0.0.1:${WEB_PORT}/api/actuator/health/readiness" > "${EVIDENCE_DIR}/gateway-readiness.json"
curl --fail --silent --show-error \
  "http://127.0.0.1:${WEB_PORT}/api/actuator/info" > "${EVIDENCE_DIR}/gateway-info.json"
grep -Fq "${IMAGE_TAG}" "${EVIDENCE_DIR}/gateway-info.json"
curl --fail --silent --show-error --retry 10 --retry-delay 3 \
  "https://${CLOUD_DOMAIN}/api/actuator/health/readiness" > "${EVIDENCE_DIR}/public-readiness.json"

capture_diagnostics
printf '%s\n' "${IMAGE_TAG}" > "${STATE_DIR}/current-image-tag"
ln -sfn "${RELEASE_DIR}" "${BASE_DIR}/current"
trap - ERR
echo "Cloud deployment succeeded: https://${CLOUD_DOMAIN} -> ${IMAGE_TAG}"
