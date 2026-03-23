# TESTING

이 문서는 현재 코드 기준의 수동 테스트와 동시성 검증 절차를 정리합니다.

## 0. 사전 준비

### 데이터베이스 실행

```bash
docker-compose up -d
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

### Swagger UI

- `http://localhost:8080/swagger-ui.html`

### 테스트 데이터 초기화

```bash
docker exec corebank-postgres psql -U core -d corebank -c "
DELETE FROM journal_entries;
DELETE FROM transfers;
UPDATE accounts
SET balance = CASE
    WHEN account_number = 'HOUSE-0001' THEN 0
    WHEN account_number = 'A-0001' THEN 100000
    WHEN account_number = 'B-0001' THEN 0
    ELSE balance
END
WHERE account_number IN ('HOUSE-0001','A-0001','B-0001');
"
```

## 1. 기능 테스트

### STEP 1. 입금 성공

요청:

```json
{
  "accountNo": "A-0001",
  "idempotencyKey": "test-deposit-001",
  "amount": 5000
}
```

기대 결과:

- HTTP `200`
- 응답 `status = "SUCCEEDED"`

### STEP 2. 입금 멱등성 확인

STEP 1과 완전히 동일한 요청을 다시 보냅니다.

기대 결과:

- HTTP `200`
- 같은 거래가 재사용되거나 동일 결과 반환
- 잔액은 한 번만 증가

### STEP 3. 출금 성공

요청:

```json
{
  "accountNo": "A-0001",
  "idempotencyKey": "test-withdraw-001",
  "amount": 1000
}
```

기대 결과:

- HTTP `200`
- 응답 `status = "SUCCEEDED"`

### STEP 4. 계좌이체 성공

요청:

```json
{
  "fromAccountNo": "A-0001",
  "toAccountNo": "B-0001",
  "idempotencyKey": "test-transfer-001",
  "amount": 2000
}
```

기대 결과:

- HTTP `200`
- 응답 `status = "SUCCEEDED"`

### STEP 5. 잔액 부족

요청:

```json
{
  "accountNo": "A-0001",
  "idempotencyKey": "test-error-001",
  "amount": 999999
}
```

기대 결과:

- HTTP `422`
- `error = "insufficient funds"`

### STEP 6. 자기 계좌 이체

요청:

```json
{
  "fromAccountNo": "A-0001",
  "toAccountNo": "A-0001",
  "idempotencyKey": "test-error-002",
  "amount": 100
}
```

기대 결과:

- HTTP `422`
- `error = "cannot transfer to same account"`

## 2. Race Condition 검증

### 목적

- 동일 계좌 동시 출금 시 잔액 음수 또는 lost update가 발생하는지 확인
- `PESSIMISTIC_WRITE` 적용 전후를 비교

### 실행 조건

- 계좌: `A-0001`
- 초기 잔액: `100000`
- 요청 수: `150`
- 요청 금액: `1000`

### k6 스크립트

파일:

- [scripts/race-condition-withdraw.js](../../scripts/race-condition-withdraw.js)

실행:

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ACCOUNT_NO=A-0001 \
  -e AMOUNT=1000 \
  -e VUS=150 \
  -e ITERATIONS=150 \
  -e KEY_PREFIX=race-withdraw \
  ./scripts/race-condition-withdraw.js
```

### curl 기반 동시 실행

```bash
seq 1 150 | xargs -I{} -P150 bash -lc \
"curl -s -o /tmp/race-{}.json -w '%{http_code}\n' \
  -X POST http://localhost:8080/api/transactions/withdraw \
  -H 'Content-Type: application/json' \
  -d '{\"accountNo\":\"A-0001\",\"idempotencyKey\":\"race-withdraw-{}\",\"amount\":1000}'" \
| sort | uniq -c
```

### 결과 확인

```bash
docker exec corebank-postgres psql -U core -d corebank -c "
SELECT account_number, balance
FROM accounts
WHERE account_number IN ('HOUSE-0001','A-0001')
ORDER BY account_number;
"
```

```bash
docker exec corebank-postgres psql -U core -d corebank -c "
SELECT status, COUNT(*) AS count
FROM transfers
WHERE type = 'WITHDRAW'
  AND idempotency_key LIKE 'race-withdraw-%'
GROUP BY status
ORDER BY status;
"
```

### 판단 기준

- `A-0001` 잔액이 `0` 미만이면 실패
- `성공건수 * 1000 = 100000 - 최종잔액`이 안 맞으면 불일치
- 락 적용 후에는 성공 `100건`, 실패 `50건(422)`, 최종 잔액 `0`이 정상

### 실제 측정 결과

- 락 미적용 기준선:
  - `200 = 150건`
  - `A-0001 = 86000`
  - 불일치 발생
- 락 적용 후:
  - `200 = 100건`
  - `422 = 50건`
  - `A-0001 = 0`
  - 불일치 없음

## 3. Idempotency Key 검증

### 목적

- 동일 `Idempotency Key` 동시 요청에서 중복 거래가 생성되지 않는지 확인

### 스크립트

- [scripts/idempotency-duplicate.sh](../../scripts/idempotency-duplicate.sh)

실행:

```bash
BASE_URL=http://localhost:8080 \
ACCOUNT_NO=A-0001 \
AMOUNT=1000 \
IDEMPOTENCY_KEY=idem-duplicate-001 \
bash ./scripts/idempotency-duplicate.sh
```

### 결과 확인

```bash
docker exec corebank-postgres psql -U core -d corebank -c "
SELECT idempotency_key, COUNT(*) AS transfer_count
FROM transfers
WHERE idempotency_key = 'idem-duplicate-001'
GROUP BY idempotency_key;

SELECT account_number, balance
FROM accounts
WHERE account_number IN ('HOUSE-0001','A-0001')
ORDER BY account_number;
"
```

### 판단 기준

- 두 응답이 같은 `transferId`를 반환
- `transfer_count = 1`
- 잔액 차감은 `1회`만 반영

### 실제 측정 결과

- 두 응답 모두 동일 `transferId` 반환
- `transfer_count = 1`
- `A-0001 = 99000`
- `HOUSE-0001 = 1000`

## 4. 참고 문서

- [README.md](../../README.md)
- [result.md](../../result.md)
