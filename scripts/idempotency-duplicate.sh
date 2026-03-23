#!/usr/bin/env bash

set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
ACCOUNT_NO="${ACCOUNT_NO:-A-0001}"
AMOUNT="${AMOUNT:-1000}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-idem-duplicate-001}"

PAYLOAD="$(printf '{"accountNo":"%s","amount":%s,"idempotencyKey":"%s"}' "$ACCOUNT_NO" "$AMOUNT" "$IDEMPOTENCY_KEY")"

curl -sS -X POST "$BASE_URL/api/transactions/withdraw" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD" > /tmp/idempotency-response-1.json &
PID1=$!

curl -sS -X POST "$BASE_URL/api/transactions/withdraw" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD" > /tmp/idempotency-response-2.json &
PID2=$!

wait "$PID1"
wait "$PID2"

printf 'response-1: '
cat /tmp/idempotency-response-1.json
printf '\nresponse-2: '
cat /tmp/idempotency-response-2.json
printf '\n'
