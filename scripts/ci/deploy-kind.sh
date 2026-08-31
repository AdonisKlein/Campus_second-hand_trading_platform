#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_NAMESPACE="${1:?usage: deploy-kind.sh <image-namespace> <sha-tag>}"
IMAGE_TAG="${2:?usage: deploy-kind.sh <image-namespace> <sha-tag>}"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-campus-ci-deploy}"
CONTEXT="kind-${CLUSTER_NAME}"
NAMESPACE="campus-market"
SMOKE_PORT="${SMOKE_PORT:-18081}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY="${REPO_ROOT}/k8s/overlays/ci"
SECRET_FILE="${OVERLAY}/.env.secret"
RUN_KEY="${GITHUB_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVIDENCE_DIR="${K8S_EVIDENCE_DIR:-${REPO_ROOT}/test-results/k8s-deploy/${RUN_KEY}}"
PORT_FORWARD_PID=""
IMAGES=(campus-gateway campus-account campus-marketplace campus-trading campus-governance campus-web)
DEPLOYMENTS=(gateway account-service marketplace-service trading-service governance-service web)

mkdir -p "${EVIDENCE_DIR}"
require_command() { command -v "$1" >/dev/null 2>&1 || { echo "required command not found: $1" >&2; exit 2; }; }

capture_evidence() {
  local exit_code="$1"
  trap - EXIT
  set +e
  if [[ -n "${PORT_FORWARD_PID}" ]]; then kill "${PORT_FORWARD_PID}" >/dev/null 2>&1; wait "${PORT_FORWARD_PID}" >/dev/null 2>&1; fi
  if kubectl config get-contexts "${CONTEXT}" >/dev/null 2>&1; then
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get pods,svc,pvc -o wide >"${EVIDENCE_DIR}/resources.txt" 2>&1
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get events --sort-by=.lastTimestamp >"${EVIDENCE_DIR}/events.txt" 2>&1
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" describe pods >"${EVIDENCE_DIR}/pods-describe.txt" 2>&1
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get deployments -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}' >"${EVIDENCE_DIR}/deployed-images.txt" 2>&1
    while IFS= read -r pod; do
      safe_name="${pod#pod/}"
      kubectl --context "${CONTEXT}" -n "${NAMESPACE}" logs "${pod}" --all-containers --prefix >"${EVIDENCE_DIR}/${safe_name}.log" 2>&1
      kubectl --context "${CONTEXT}" -n "${NAMESPACE}" logs "${pod}" --all-containers --prefix --previous >"${EVIDENCE_DIR}/${safe_name}-previous.log" 2>&1 || true
    done < <(kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get pods -o name 2>/dev/null)
  fi
  rm -f "${SECRET_FILE}"
  result="SUCCESS"; [[ "${exit_code}" -ne 0 ]] && result="FAILED"
  cat >"${EVIDENCE_DIR}/summary.md" <<EOF
# Kubernetes deployment report

- Result: ${result}
- Exit code: ${exit_code}
- Commit: ${GITHUB_SHA:-local}
- Branch/ref: ${GITHUB_REF_NAME:-local}
- Cluster: ${CLUSTER_NAME}
- Image namespace: ${IMAGE_NAMESPACE}
- Immutable tag: ${IMAGE_TAG}
- Controlled failure requested: ${CONTROLLED_FAILURE:-false}
- UTC finished at: $(date -u +%Y-%m-%dT%H:%M:%SZ)

Artifacts include resources, events, pod descriptions, current/previous logs, deployed image SHAs and health/version responses.
EOF
  exit "${exit_code}"
}
trap 'capture_evidence $?' EXIT

for command_name in docker kind kubectl curl openssl; do require_command "${command_name}"; done
docker version >/dev/null
if kind get clusters | grep -Fxq "${CLUSTER_NAME}"; then kind delete cluster --name "${CLUSTER_NAME}"; fi
kind create cluster --name "${CLUSTER_NAME}" --config "${OVERLAY}/kind-config.yaml"

full_images=()
for name in "${IMAGES[@]}"; do
  image="${IMAGE_NAMESPACE}/${name}:${IMAGE_TAG}"
  docker pull "${image}"
  full_images+=("${image}")
done
kind load docker-image "${full_images[@]}" --name "${CLUSTER_NAME}"

umask 077
cat >"${SECRET_FILE}" <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
ACCOUNT_DB_PASSWORD=$(openssl rand -hex 24)
MARKETPLACE_DB_PASSWORD=$(openssl rand -hex 24)
TRADING_DB_PASSWORD=$(openssl rand -hex 24)
GOVERNANCE_DB_PASSWORD=$(openssl rand -hex 24)
REDIS_PASSWORD=$(openssl rand -hex 24)
RABBITMQ_PASSWORD=$(openssl rand -hex 24)
VERIFICATION_PEPPER=$(openssl rand -hex 32)
INTERNAL_SERVICE_TOKEN=$(openssl rand -hex 32)
INTERNAL_JWT_SECRET=$(openssl rand -hex 32)
MAIL_PASSWORD=ci
EOF

kubectl --context "${CONTEXT}" apply -k "${OVERLAY}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/gateway "gateway=${IMAGE_NAMESPACE}/campus-gateway:${IMAGE_TAG}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/account-service "account=${IMAGE_NAMESPACE}/campus-account:${IMAGE_TAG}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/marketplace-service "marketplace=${IMAGE_NAMESPACE}/campus-marketplace:${IMAGE_TAG}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/trading-service "trading=${IMAGE_NAMESPACE}/campus-trading:${IMAGE_TAG}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/governance-service "governance=${IMAGE_NAMESPACE}/campus-governance:${IMAGE_TAG}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/web "web=${IMAGE_NAMESPACE}/campus-web:${IMAGE_TAG}"
for deployment in "${DEPLOYMENTS[@]}"; do
  kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set env deployment/"${deployment}" APP_VERSION="${IMAGE_TAG}" GIT_COMMIT="${GITHUB_SHA:-local}"
done

kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status statefulset/campus-mysql --timeout=360s
for deployment in redis rabbitmq mailpit account-service marketplace-service trading-service governance-service gateway web; do
  kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status deployment/"${deployment}" --timeout=360s
done

kubectl --context "${CONTEXT}" -n "${NAMESPACE}" port-forward service/web "${SMOKE_PORT}:80" >"${EVIDENCE_DIR}/port-forward.log" 2>&1 &
PORT_FORWARD_PID="$!"
for attempt in $(seq 1 45); do
  if curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/index.html" >"${EVIDENCE_DIR}/web-index.html"; then break; fi
  [[ "${attempt}" -eq 45 ]] && { echo "web smoke check did not become reachable" >&2; exit 1; }
  sleep 1
done
curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/api/actuator/health/liveness" >"${EVIDENCE_DIR}/gateway-liveness.json"
curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/api/actuator/health/readiness" >"${EVIDENCE_DIR}/gateway-readiness.json"
curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/api/actuator/info" >"${EVIDENCE_DIR}/gateway-info.json"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${EVIDENCE_DIR}/gateway-liveness.json"
grep -Fq "${IMAGE_TAG}" "${EVIDENCE_DIR}/gateway-info.json"

for pair in account-service:8081 marketplace-service:8082 trading-service:8083 governance-service:8084; do
  name="${pair%%:*}"; port="${pair##*:}"
  kubectl --context "${CONTEXT}" -n "${NAMESPACE}" exec deployment/gateway -- curl --fail --silent "http://${name}:${port}/actuator/health/readiness" >"${EVIDENCE_DIR}/${name}-readiness.json"
  kubectl --context "${CONTEXT}" -n "${NAMESPACE}" exec deployment/gateway -- curl --fail --silent "http://${name}:${port}/actuator/info" >"${EVIDENCE_DIR}/${name}-info.json"
  grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${EVIDENCE_DIR}/${name}-readiness.json"
  grep -Fq "${IMAGE_TAG}" "${EVIDENCE_DIR}/${name}-info.json"
done

if [[ "${CONTROLLED_FAILURE:-false}" == "true" ]]; then
  echo "Controlled failure requested after successful rollout and smoke checks." >"${EVIDENCE_DIR}/controlled-failure.txt"
  exit 42
fi
echo "Kubernetes microservice rollout, readiness and immutable-version smoke checks passed."
