import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

export const options = {
  stages: [
    // Ramp-up to 1000 req/s
    { duration: '30s', target: 100 },
    // Maintain 1000 req/s
    { duration: '2m', target: 100 },
    // Spike to 10000 req/s
    { duration: '30s', target: 1000 },
    // Maintain 10000 req/s
    { duration: '1m', target: 1000 },
    // Ramp-down
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be below 1%
  },
};

const shortenDuration = new Trend('shorten_duration');
const expandDuration = new Trend('expand_duration');
const shortenSuccessRate = new Rate('shorten_success_rate');
const expandSuccessRate = new Rate('expand_success_rate');
const totalRequests = new Counter('total_requests');

const BASE_URL = 'http://localhost:8080';

function generateRandomUrl() {
  const domains = ['example.com', 'test.org', 'demo.net'];
  const paths = ['', '/path', '/another/path', '/long/path/with/many/segments'];
  const queries = ['', '?param=value', '?param1=value1&param2=value2'];

  const domain = domains[Math.floor(Math.random() * domains.length)];
  const path = paths[Math.floor(Math.random() * paths.length)];
  const query = queries[Math.floor(Math.random() * queries.length)];

  return `https://${domain}${path}${query}`;
}

// function getExpiration() {
//     const expirations = [
//         "2025-05-26T21:50:00.156636800Z",
//         "2025-05-26T21:51:00.156636800Z",
//         "2025-05-26T21:52:00.156636800Z",
//         "2025-05-26T21:53:00.156636800Z",
//         "2025-05-26T21:54:00.156636800Z",
//         "2025-05-26T21:55:00.156636800Z",
//         "2025-05-26T21:56:00.156636800Z"
//     ]
//
//     return expirations[Math.floor(Math.random() * expirations.length)];
// }

export default function () {
  const now = new Date();
  now.setMinutes(now.getMinutes() + 1);
  const url = generateRandomUrl();
  const shortenPayload = JSON.stringify({ longUrl: url, customAlias: null, expirationDate: now.toISOString() });
  const shortenParams = {
    headers: { 'Content-Type': 'application/json' },
  };

  totalRequests.add(1);
  const shortenRes = http.post(`${BASE_URL}/shorten`, shortenPayload, shortenParams);
  shortenDuration.add(shortenRes.timings.duration);
  shortenSuccessRate.add(shortenRes.status === 201);

  check(shortenRes, {
    'shorten status is 201': (r) => r.status === 201,
  });

  if (shortenRes.status === 201) {
    const shortUrl = shortenRes.json('shortUrl');
    const shortCode = shortUrl.split('/').pop();

    totalRequests.add(1);
    const expandRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    expandDuration.add(expandRes.timings.duration);
    expandSuccessRate.add(expandRes.status === 301);

    check(expandRes, {
      'expand status is 301': (r) => r.status === 301,
      'expand location matches': (r) => r.headers.Location === url,
    });
  }

  sleep(0.1);
}
