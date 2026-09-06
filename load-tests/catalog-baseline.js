import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://gateway-service:8080';
const rate = Number(__ENV.K6_RATE || 5);
const duration = __ENV.K6_DURATION || '10s';
const preAllocatedVUs = Number(__ENV.K6_PREALLOCATED_VUS || Math.max(5, rate));
const maxVUs = Number(__ENV.K6_MAX_VUS || Math.max(20, rate * 4));

export const options = {
  scenarios: {
    catalog_reads: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:course_catalog}': ['p(95)<1500'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/v1/courses?page=0&size=20`, {
    tags: { name: 'course_catalog' },
  });
  check(response, {
    'catalog returns HTTP 200': (result) => result.status === 200,
    'catalog returns standard success envelope': (result) => {
      try {
        return result.json('code') === 0;
      } catch (_) {
        return false;
      }
    },
  });
}

export function handleSummary(data) {
  return {
    '/results/catalog-summary.json': JSON.stringify(data, null, 2),
    stdout: `CampusEnroll catalog load test complete: ${data.metrics.iterations.values.count} iterations\n`,
  };
}
