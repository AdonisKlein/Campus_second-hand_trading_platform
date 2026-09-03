// HPA 压测脚本：对 Marketplace 搜索接口持续加压。
// 通过网关 /api/search 访问，不绕过 Gateway，不携带身份（公开只读接口）。
// 用法：
//   docker run --rm -v "%cd%":/scripts -e K6_BASE_URL=http://127.0.0.1:8080 \
//     -e VUS=100 -e DURATION=3m grafana/k6:0.54.0 run /scripts/load.js
// 或使用阶段式加压：
//   -e 'STAGES=[{"target":20,"duration":"1m"},{"target":150,"duration":"4m"},{"target":20,"duration":"1m"}]'
import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";

const BASE_URL = __ENV.K6_BASE_URL || "http://127.0.0.1:8080";
const TERMS = new SharedArray("terms", function () {
  return ["的", "书", "台灯", "教材", "自行车", "耳机", "键盘", "闲置", "九成新", "风扇",
          "宿舍", "考研", "高数", "吉他", "显示器", "桌", "椅", "吹风机", "保温杯", "雨伞"];
});

const vus = Number(__ENV.VUS || 50);
const duration = __ENV.DURATION || "3m";
const stages = __ENV.STAGES ? JSON.parse(__ENV.STAGES) : null;

export const options = stages
  ? {
      scenarios: {
        load: {
          executor: "ramping-vus",
          startVUs: 10,
          stages,
        },
      },
    }
  : {
      scenarios: {
        load: {
          executor: "constant-vus",
          vus,
          duration,
        },
      },
    };

export default function () {
  const term = TERMS[Math.floor(Math.random() * TERMS.length)];
  const query = `scope=ITEMS&q=${encodeURIComponent(term)}&sort=RELEVANCE&page=0&size=24`;
  const response = http.get(`${BASE_URL}/api/search?${query}`, {
    headers: { Accept: "application/json" },
  });
  check(response, {
    "search returns 200": (result) => result.status === 200,
  });
  sleep(0.05 + Math.random() * 0.1);
}
