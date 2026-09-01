import fs from "node:fs";

const read = path => fs.readFileSync(path, "utf8");
const workflow = read(".github/workflows/ci.yml");
const deploy = read("scripts/ci/deploy-kind.sh");
const ciOverlay = read("k8s/overlays/ci/kustomization.yaml");
const mysqlPlatform = read("k8s/base/platform.yaml");
const composeMysqlInit = read("deploy/mysql/init/01-databases.sh");
const services = ["api-gateway", "account-service", "marketplace-service", "trading-service", "governance-service"];
const images = ["campus-gateway", "campus-account", "campus-marketplace", "campus-trading", "campus-governance", "campus-web"];
const failures = [];
const expect = (condition, message) => { if (!condition) failures.push(message); };

expect(!workflow.includes("continue-on-error"), "CI must not bypass a failed quality gate");
expect(!workflow.includes("working-directory: backend"), "retired monolith must not be built by CI");
for (const service of services) expect(workflow.includes(service), `CI matrix is missing ${service}`);
for (const image of images) {
  expect(workflow.includes(image), `CI image matrix is missing ${image}`);
  expect(deploy.includes(image), `Kind deploy script is missing ${image}`);
}
expect(workflow.includes("needs: service-tests"), "contract tests must depend on service tests");
expect(workflow.includes("needs: contract-and-frontend-tests"), "E2E must depend on contract/frontend tests");
expect(workflow.includes("test-summary:") && workflow.includes("unified-test-report"), "CI must publish a unified machine/human-readable test report");
expect(workflow.includes("needs: [e2e-tests, test-summary]"), "images must depend on E2E and the unified report");
expect(workflow.includes("verify-event-contracts.mjs"), "CI must validate versioned RabbitMQ event contracts");
expect(workflow.includes("sha-${GITHUB_SHA::7}"), "images need immutable short-SHA tags");
expect(workflow.includes("if: ${{ always() }}"), "failure artifacts must use always()");
expect(deploy.includes("--previous"), "diagnostics must capture previous container logs");
expect(deploy.includes("pods-describe.txt") && deploy.includes("events.txt"), "diagnostics must capture pod descriptions and events");
expect(deploy.includes("/actuator/info") && deploy.includes("/health/readiness"), "deployment must verify version and readiness");
for (const [name, mysqlInit] of [["Kind", mysqlPlatform], ["Compose", composeMysqlInit]]) {
  expect(
    mysqlInit.includes("--protocol=socket --socket=/var/lib/mysql/mysql.sock"),
    `${name} MySQL initialization must use the temporary server socket explicitly`
  );
}
for (const deployment of ["gateway", "account-service", "marketplace-service", "trading-service", "governance-service", "web"]) {
  expect(
    ciOverlay.includes(`{name: ${deployment}, count: 0}`),
    `${deployment} must start at zero replicas while CI sets its immutable image and version`
  );
}
const setEnvAt = deploy.indexOf('set env deployment/');
const infrastructureRolloutAt = deploy.indexOf('for deployment in redis rabbitmq mailpit; do');
const scaleAt = deploy.indexOf('scale "${deployment_resources[@]}" --replicas=1');
const applicationRolloutAt = deploy.indexOf('for deployment in account-service marketplace-service');
expect(setEnvAt >= 0 && infrastructureRolloutAt > setEnvAt && scaleAt > infrastructureRolloutAt && applicationRolloutAt > scaleAt,
  "CI must configure applications, await infrastructure, scale applications once, then check application rollouts");

for (const service of services) {
  const config = read(`services/${service}/src/main/resources/application.yml`);
  expect(config.includes("include: health,info"), `${service} must expose health and info`);
  expect(config.includes("version: \${APP_VERSION:dev}"), `${service} must expose APP_VERSION`);
  expect(config.includes("console: ecs"), `${service} must emit structured ECS logs`);
}

if (failures.length) {
  console.error(failures.map(message => `- ${message}`).join("\n"));
  process.exit(1);
}
console.log("Work Item 8 CI/CD and observability contracts passed.");
