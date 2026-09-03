import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

export const FIXED_ITEM_IDS = Object.freeze([5278, 7433, 7654, 8519, 11496, 18637, 3742, 13457, 4813, 9944]);
export const ENDPOINT_TAGS = Object.freeze({
  itemsList: 'items_list',
  itemDetail: 'item_detail',
  itemMessages: 'item_messages',
});

const EMBEDDED_RUN_CONFIG = /*__PERFORMANCE_RUN_CONFIG__*/ null;

function positiveInteger(value, fallback, name) {
  const text = value === undefined || value === '' ? String(fallback) : value;
  if (!/^[1-9]\d*$/.test(text)) throw new Error(`${name} must be a positive integer.`);
  return Number(text);
}

function duration(value) {
  const text = value || '10s';
  if (!/^\d+(ms|s|m|h)$/.test(text) || text.startsWith('0')) throw new Error('DURATION must be a positive k6 duration such as 10s or 5m.');
  return text;
}

const runConfig = Object.freeze({
  vus: EMBEDDED_RUN_CONFIG?.vus ?? positiveInteger(__ENV.VUS, 1, 'VUS'),
  duration: EMBEDDED_RUN_CONFIG?.duration ?? duration(__ENV.DURATION),
  architecture: EMBEDDED_RUN_CONFIG?.architecture ?? (__ENV.ARCHITECTURE || 'development'),
  runLabel: EMBEDDED_RUN_CONFIG?.runLabel ?? (__ENV.RUN_LABEL || 'smoke'),
  validationMode: EMBEDDED_RUN_CONFIG?.validationMode ?? (__ENV.VALIDATION_MODE || 'strict'),
});
if (!['strict', 'record'].includes(runConfig.validationMode)) throw new Error('validationMode must be strict or record.');
const jsonErrors = new Counter('benchmark_json_errors');

function validationThreshold(strictValue, recordValue) {
  return [runConfig.validationMode === 'strict' ? strictValue : recordValue];
}

export const options = {
  vus: positiveInteger(String(runConfig.vus), 1, 'VUS'),
  duration: duration(runConfig.duration),
  tags: { architecture: runConfig.architecture, run_label: runConfig.runLabel },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],
  thresholds: {
    http_req_failed: validationThreshold('rate==0', 'rate>=0'),
    checks: validationThreshold('rate==1', 'rate>=0'),
    benchmark_json_errors: validationThreshold('count==0', 'count>=0'),
    'http_reqs{endpoint:items_list}': ['count>0'],
    'http_reqs{endpoint:item_detail}': ['count>0'],
    'http_reqs{endpoint:item_messages}': ['count>0'],
    'http_req_failed{endpoint:items_list}': validationThreshold('rate==0', 'rate>=0'),
    'http_req_failed{endpoint:item_detail}': validationThreshold('rate==0', 'rate>=0'),
    'http_req_failed{endpoint:item_messages}': validationThreshold('rate==0', 'rate>=0'),
    'http_req_duration{endpoint:items_list}': ['p(95)<60000'],
    'http_req_duration{endpoint:item_detail}': ['p(95)<60000'],
    'http_req_duration{endpoint:item_messages}': ['p(95)<60000'],
    'checks{endpoint:items_list}': validationThreshold('rate==1', 'rate>=0'),
    'checks{endpoint:item_detail}': validationThreshold('rate==1', 'rate>=0'),
    'checks{endpoint:item_messages}': validationThreshold('rate==1', 'rate>=0'),
  },
};

function parseJson(response, endpoint) {
  try {
    return response.json();
  } catch (_) {
    jsonErrors.add(1, { endpoint });
    return null;
  }
}

function positiveId(value) {
  return Number.isInteger(value) && value > 0;
}

export default function () {
  const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
  const itemId = FIXED_ITEM_IDS[((__VU - 1) + __ITER) % FIXED_ITEM_IDS.length];

  const listResponse = http.get(`${baseUrl}/api/items`, { tags: { endpoint: ENDPOINT_TAGS.itemsList } });
  const listBody = parseJson(listResponse, ENDPOINT_TAGS.itemsList);
  check(listResponse, {
    'items_list status is 200': (response) => response.status === 200,
    'items_list JSON is valid': () => listBody !== null,
    'items_list envelope is successful': () => listBody?.success === true,
    'items_list data is non-empty array': () => Array.isArray(listBody?.data) && listBody.data.length > 0,
    'items_list first item has positive id': () => Array.isArray(listBody?.data) && positiveId(listBody.data[0]?.id),
  }, { endpoint: ENDPOINT_TAGS.itemsList });

  const detailResponse = http.get(`${baseUrl}/api/items/${itemId}`, { tags: { endpoint: ENDPOINT_TAGS.itemDetail } });
  const detailBody = parseJson(detailResponse, ENDPOINT_TAGS.itemDetail);
  check(detailResponse, {
    'item_detail status is 200': (response) => response.status === 200,
    'item_detail JSON is valid': () => detailBody !== null,
    'item_detail envelope is successful': () => detailBody?.success === true,
    'item_detail data is object': () => detailBody?.data !== null && typeof detailBody?.data === 'object' && !Array.isArray(detailBody.data),
    'item_detail id matches request': () => detailBody?.data?.id === itemId,
  }, { endpoint: ENDPOINT_TAGS.itemDetail });

  const messagesResponse = http.get(`${baseUrl}/api/messages/item/${itemId}`, { tags: { endpoint: ENDPOINT_TAGS.itemMessages } });
  const messagesBody = parseJson(messagesResponse, ENDPOINT_TAGS.itemMessages);
  check(messagesResponse, {
    'item_messages status is 200': (response) => response.status === 200,
    'item_messages JSON is valid': () => messagesBody !== null,
    'item_messages envelope is successful': () => messagesBody?.success === true,
    'item_messages data is non-empty array': () => Array.isArray(messagesBody?.data) && messagesBody.data.length > 0,
    'item_messages first message has positive id': () => Array.isArray(messagesBody?.data) && positiveId(messagesBody.data[0]?.id),
    'item_messages first message belongs to item': () => Array.isArray(messagesBody?.data) && messagesBody.data[0]?.itemId === itemId,
  }, { endpoint: ENDPOINT_TAGS.itemMessages });
}

export function handleSummary(data) {
  const path = __ENV.K6_SUMMARY_PATH || '/artifacts/k6-summary.json';
  return { [path]: JSON.stringify(data, null, 2) };
}
