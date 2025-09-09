# TESTING - 수동 테스트 시나리오

본 문서는 Corebank 프로젝트 **수동 테스트 절차**를 정리합니다.
스모크(간단) 절차는 README 최하단을 참고하세요.

---

## 0) 사전 준비

- Docker로 PostgreSQL이 실행 중이며 포트가 5433으로 포워딩되어 있다고 가정합니다.
- 애플리케이션 실행:
```bash
./gradlew bootRun
```
- 헬스 체크
```bash
http://localhost:8080/actuator/health
```

## 1) 모의 데이터 세팅
- Docker 컨테이너 이름 확인:
```bash
docker ps
```
- psql 접속:
```bash
docker exec -it <postgres-container> psql -U core -d corebank
```
- 아래 SQL을 실행 (UUID 고정값 사용):
```sql
-- 고객
INSERT INTO customers (id, name, created_at) VALUES
('00000000-0000-0000-0000-000000000001', 'HOUSE', now())
ON CONFLICT DO NOTHING;
INSERT INTO customers (id, name, created_at) VALUES
('00000000-0000-0000-0000-000000000002', 'Alice', now()),
('00000000-0000-0000-0000-000000000003', 'Bob',   now())
ON CONFLICT DO NOTHING;

-- 계좌
INSERT INTO accounts (id, customer_id, account_number, currency, status, is_house, balance, created_at) VALUES
('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'HOUSE-0001', 'KRW', 'ACTIVE', TRUE,  0, now())
ON CONFLICT DO NOTHING;
INSERT INTO accounts (id, customer_id, account_number, currency, status, is_house, balance, created_at) VALUES
('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'A-0001',     'KRW', 'ACTIVE', FALSE, 0, now()),
('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'B-0001',     'KRW', 'ACTIVE', FALSE, 0, now())
ON CONFLICT DO NOTHING;

-- 확인
SELECT account_number, balance, status, is_house FROM accounts ORDER BY account_number;
```

## 2) 시나리오 A - 입금(Deposit) 멱등성
### A-1. 입금
```bash
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"K-DEP-001","amount":5000}'
```
**기대: SUCCEEDED**

### A-2. 동일 키 재시도(멱등)
```bash
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"K-DEP-001","amount":5000}'
```
**기대: 동일 응답, DB 변화 없음**

### A-3. 검증 쿼리
```sql
SELECT id, type, amount, status, idempotency_key
  FROM transfers WHERE idempotency_key='K-DEP-001';

SELECT account_number, balance
  FROM accounts WHERE account_number='A-0001';

SELECT direction, amount
  FROM journal_entries
 ORDER BY id DESC LIMIT 5;
```

## 3) 시나리오 B - 출금(Withdraw) + 잔액 검사
### B-1. 출금
```bash
curl -s -X POST http://localhost:8080/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"K-WITH-001","amount":1000}'
```
**기대: SUCCEEDED**

### B-2. 잔액 부족 출금
```bash
curl -s -X POST http://localhost:8080/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"K-WITH-002","amount":10000}'
```
**기대: 422 Unprocessable(잔액 부족)**

### B-3. 검증 쿼리
```sql
SELECT account_number, balance FROM accounts WHERE account_number='A-0001';
SELECT type, amount, status FROM transfers WHERE idempotency_key IN ('K-WITH-001','K-WITH-002') ORDER BY created_at;
SELECT direction, amount FROM journal_entries ORDER BY id DESC LIMIT 5;
```

## 4) 시나리오 C - 계좌이체(Account Transfer)
### C-1. 이체
```bash
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNo":"A-0001","toAccountNo":"B-0001","idempotencyKey":"K-TRF-001","amount":2000}'
```
**기대: SUCCEEDED**

### C-2. 동일 키 재시도(멱등)
```bash
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNo":"A-0001","toAccountNo":"B-0001","idempotencyKey":"K-TRF-001","amount":2000}'
```
**기대: 동일 응답, DB 변화 없음**

### C-3. 검증 쿼리
```sql
SELECT account_number, balance
  FROM accounts
 WHERE account_number IN ('A-0001','B-0001')
 ORDER BY account_number;

SELECT j.direction, j.amount
  FROM journal_entries j
  JOIN transfers t ON t.id = j.transfer_id
 WHERE t.idempotency_key='K-TRF-001'
 ORDER BY j.id;
```

## 5) 시나리오 D - 규칙 위반/예외
### D-1. 금액 0 또는 음수
``` bash
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"K-NEG-001","amount":0}'
```
**기대: 400 Bad Request**

### D-2. 자기 이체 금지
```bash
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNo":"A-0001","toAccountNo":"A-0001","idempotencyKey":"K-TRF-ERR","amount":100}'
```
**기대: 400 Bad Request**

### D-3. 비활성 계좌 (FROZEN)
```sql
UPDATE accounts SET status='FROZEN' WHERE account_number='B-0001';
```
```bash
curl -s -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"B-0001","idempotencyKey":"K-FRZ-001","amount":1000}'
```
**기대: 422 Unprocessable**

## 6) 리셋(재실행용)
```sql
DELETE FROM journal_entries;
DELETE FROM transfers;
UPDATE accounts SET balance = 0, status='ACTIVE' WHERE account_number IN ('A-0001','B-0001','HOUSE-0001');
```
**전체 초기화(주의: 모든 데이터 삭제)**
```bash
docker compose down -v
docker compose up -d
```