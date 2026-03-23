import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    withdraw_race: {
      executor: "shared-iterations",
      vus: Number(__ENV.VUS || 150),
      iterations: Number(__ENV.ITERATIONS || 150),
      maxDuration: "30s",
    },
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const accountNo = __ENV.ACCOUNT_NO || "A-0001";
const amount = Number(__ENV.AMOUNT || 1000);
const keyPrefix = __ENV.KEY_PREFIX || "race-withdraw";

export default function () {
  const idempotencyKey = `${keyPrefix}-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    accountNo,
    amount,
    idempotencyKey,
  });

  const response = http.post(
    `${baseUrl}/api/transactions/withdraw`,
    payload,
    {
      headers: { "Content-Type": "application/json" },
      timeout: "10s",
    }
  );

  check(response, {
    "status is 200 or 422": (r) => r.status === 200 || r.status === 422,
  });

  sleep(0.1);
}
