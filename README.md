# Corebank

간단한 Core Banking 학습 프로젝트입니다. 계좌, 거래, 원장 개념을 Spring Boot로 구현하면서 트랜잭션, 멱등성, 이중기장, 동시성 제어를 실습하는 목적의 백엔드 애플리케이션입니다.

## 주요 기능

- 계좌 입금, 출금, 계좌이체 API
- 거래별 이중기장(`JournalEntry`) 기록
- `Idempotency Key` 기반 중복 요청 방지
- `PESSIMISTIC_WRITE` 기반 동시 출금/이체 정합성 보장
- `@ControllerAdvice` 기반 일관된 에러 응답

## 기술 스택

- Java 21
- Spring Boot 3.3.4
- Spring Web
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Gradle
- Docker Compose

## 프로젝트 구조

```text
src
├── main
│   ├── java/com/example/corebank
│   │   ├── common/error
│   │   ├── config
│   │   ├── domain
│   │   ├── repository
│   │   ├── service
│   │   └── web
│   └── resources
│       ├── application.yml
│       └── db/migration
└── test
```

- `web`: HTTP API와 DTO
- `service`: 거래 처리, 멱등성, 동시성 제어
- `repository`: JPA 조회 및 native insert
- `domain`: `Customer`, `Account`, `Transfer`, `JournalEntry`
- `common/error`: 전역 예외 처리
- `config`: 초기 데이터 생성

## 실행 방법

### 요구 사항

- Docker / Docker Compose
- Java 21

### 1. 데이터베이스 실행

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

실행 후 아래 계좌가 자동 생성됩니다.

- `HOUSE-0001`
- `A-0001`
- `B-0001`

### 3. 테스트 / 빌드

```bash
./gradlew test
```

## API

- `POST /api/transactions/deposit`
- `POST /api/transactions/withdraw`
- `POST /api/transactions/transfer`

Swagger UI:

- `http://localhost:8080/swagger-ui.html`

## API 예시

### 입금

```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"deposit-001","amount":5000}'
```

### 출금

```bash
curl -X POST http://localhost:8080/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountNo":"A-0001","idempotencyKey":"withdraw-001","amount":1000}'
```

### 계좌이체

```bash
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNo":"A-0001","toAccountNo":"B-0001","idempotencyKey":"transfer-001","amount":2000}'
```

성공 응답 예시:

```json
{
  "transferId": "b2bfbc23-3d12-4139-8ae2-45928cdfee15",
  "status": "SUCCEEDED"
}
```

## 에러 응답

- `400 Bad Request`: 금액이 0 이하
- `404 Not Found`: 계좌 없음
- `422 Unprocessable Entity`: 잔액 부족, 자기 계좌 이체 등 비즈니스 규칙 위반

같은 `Idempotency Key`로 동일 요청을 재전송하면 새 거래를 만들지 않고 기존 거래를 반환합니다.

## 동시성 / 멱등성 검증 결과

실제 로컬 검증 결과:

- Race condition 기준선
  - 동일 계좌에 `150`건 동시 출금
  - 락 미적용 시 `150건` 모두 `200` 응답
  - 최종 잔액 `86,000원`으로 불일치 발생
- Pessimistic Lock 적용 후
  - `200 = 100건`, `422 = 50건`
  - 최종 잔액 `0원`
  - 데이터 불일치 없음
- Idempotency Key 검증
  - 동일 키 동시 `2건` 요청
  - 두 응답이 같은 `transferId` 반환
  - DB 거래 row `1건`만 생성

상세 내용:

- [result.md](./result.md)
- [corebank.md](./corebank.md)
- [src/docs/TESTING.md](./src/docs/TESTING.md)
- [src/docs/LEARNING.md](./src/docs/LEARNING.md)
- [src/docs/schema.md](./src/docs/schema.md)

## 라이선스

[LICENSE](./LICENSE)
