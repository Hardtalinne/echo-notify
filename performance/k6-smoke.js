import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const payload = JSON.stringify({
    type: 'EMAIL',
    recipient: 'smoke@example.com',
    clientId: 'k6-smoke',
    payload: {
      subject: 'Smoke',
      body: 'Ping',
      from: 'no-reply@echo.com',
    },
    idempotencyKey: `k6-${__VU}-${__ITER}`,
  });

  const res = http.post('http://localhost:8080/v1/notifications', payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status is 202': (r) => r.status === 202,
  });

  sleep(1);
}
