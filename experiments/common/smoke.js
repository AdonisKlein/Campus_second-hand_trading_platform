import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 1,
  iterations: 3,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
  const response = http.get(`${baseUrl}/api/actuator/health/liveness`, { tags: { endpoint: 'gateway-liveness' } });
  check(response, {
    'liveness status is 200': (value) => value.status === 200,
    'liveness body reports UP': (value) => {
      try { return JSON.parse(value.body).status === 'UP'; } catch (_) { return false; }
    },
  });
}

export function handleSummary(data) {
  const path = __ENV.K6_SUMMARY_PATH || '/artifacts/k6-summary.json';
  return { [path]: JSON.stringify(data, null, 2) };
}
