#!/usr/bin/env bash
set -Eeuo pipefail

BACKEND_IMAGE="${1:?usage: deploy-kind.sh <backend-image> <web-image>}"
WEB_IMAGE="${2:?usage: deploy-kind.sh <backend-image> <web-image>}"
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

mkdir -p "${EVIDENCE_DIR}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "required command not found: $1" >&2
    exit 2
  }
}

capture_evidence() {
  local exit_code="$1"
  trap - EXIT
  set +e

  if [[ -n "${PORT_FORWARD_PID}" ]]; then
    kill "${PORT_FORWARD_PID}" >/dev/null 2>&1
    wait "${PORT_FORWARD_PID}" >/dev/null 2>&1
  fi

  if kubectl config get-contexts "${CONTEXT}" >/dev/null 2>&1; then
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get pods,svc,pvc -o wide >"${EVIDENCE_DIR}/resources.txt" 2>&1
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get events --sort-by=.lastTimestamp >"${EVIDENCE_DIR}/events.txt" 2>&1
    kubectl --context "${CONTEXT}" -n "${NAMESPACE}" describe pods >"${EVIDENCE_DIR}/pods-describe.txt" 2>&1
    while IFS= read -r pod; do
      local safe_name="${pod#pod/}"
      kubectl --context "${CONTEXT}" -n "${NAMESPACE}" logs "${pod}" --all-containers --prefix >"${EVIDENCE_DIR}/${safe_name}.log" 2>&1
    done < <(kubectl --context "${CONTEXT}" -n "${NAMESPACE}" get pods -o name 2>/dev/null)
  fi

  rm -f "${SECRET_FILE}"

  local result="SUCCESS"
  if [[ "${exit_code}" -ne 0 ]]; then
    result="FAILED"
  fi
  cat >"${EVIDENCE_DIR}/summary.md" <<EOF
# Kubernetes deployment report

- Result: ${result}
- Exit code: ${exit_code}
- Commit: ${GITHUB_SHA:-local}
- Branch/ref: ${GITHUB_REF_NAME:-local}
- Cluster: ${CLUSTER_NAME}
- Kubernetes context: ${CONTEXT}
- Backend image: ${BACKEND_IMAGE}
- Web image: ${WEB_IMAGE}
- Controlled failure requested: ${CONTROLLED_FAILURE:-false}
- UTC finished at: $(date -u +%Y-%m-%dT%H:%M:%SZ)

Detailed resources, events, pod descriptions, logs and smoke responses are stored beside this report.
EOF

  exit "${exit_code}"
}

trap 'capture_evidence $?' EXIT

for command_name in docker kind kubectl curl openssl; do
  require_command "${command_name}"
done

docker version >/dev/null

if kind get clusters | grep -Fxq "${CLUSTER_NAME}"; then
  kind delete cluster --name "${CLUSTER_NAME}"
fi
kind create cluster --name "${CLUSTER_NAME}" --config "${OVERLAY}/kind-config.yaml"

for image in "${BACKEND_IMAGE}" "${WEB_IMAGE}"; do
  if ! docker image inspect "${image}" >/dev/null 2>&1; then
    docker pull "${image}"
  fi
done
kind load docker-image "${BACKEND_IMAGE}" "${WEB_IMAGE}" --name "${CLUSTER_NAME}"

umask 077
cat >"${SECRET_FILE}" <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
MYSQL_PASSWORD=$(openssl rand -hex 24)
VERIFICATION_PEPPER=$(openssl rand -hex 32)
EOF

kubectl --context "${CONTEXT}" apply -k "${OVERLAY}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/campus-backend "backend=${BACKEND_IMAGE}"
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" set image deployment/campus-web "web=${WEB_IMAGE}"

kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status statefulset/campus-mysql --timeout=300s
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status deployment/mailpit --timeout=180s
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status deployment/campus-backend --timeout=300s
kubectl --context "${CONTEXT}" -n "${NAMESPACE}" rollout status deployment/campus-web --timeout=180s

kubectl --context "${CONTEXT}" -n "${NAMESPACE}" port-forward service/web "${SMOKE_PORT}:80" >"${EVIDENCE_DIR}/port-forward.log" 2>&1 &
PORT_FORWARD_PID="$!"

for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/index.html" >"${EVIDENCE_DIR}/web-index.html"; then
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    echo "web smoke check did not become reachable" >&2
    exit 1
  fi
  sleep 1
done

curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/api/actuator/health/liveness" >"${EVIDENCE_DIR}/liveness.json"
curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/api/actuator/health/readiness" >"${EVIDENCE_DIR}/readiness.json"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${EVIDENCE_DIR}/liveness.json"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${EVIDENCE_DIR}/readiness.json"

if [[ "${CONTROLLED_FAILURE:-false}" == "true" ]]; then
  echo "Controlled failure requested after successful rollout and smoke checks." >"${EVIDENCE_DIR}/controlled-failure.txt"
  exit 42
fi

echo "Kubernetes rollout and smoke checks passed."
