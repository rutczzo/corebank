# Core Banking Concepts & Learning Notes

이 프로젝트는 단순 CRUD가 아니라 자금 이동 시스템에서 중요한 정합성 문제를 직접 다뤄보는 학습용 예제입니다.

## 핵심 도메인 개념

### Ledger / Journal

- 모든 거래는 `JournalEntry`로 기록됩니다.
- `balance`는 결과값이고, 실제 거래 이력은 원장에 남습니다.
- 거래 추적성과 회계적 설명 가능성을 확보하는 기본 구조입니다.

### House Account

- `HOUSE-0001`은 은행 내부 계정입니다.
- 입금은 `HOUSE -> 고객 계좌`, 출금은 `고객 계좌 -> HOUSE` 흐름으로 처리됩니다.
- 고객 간 이체는 `fromAccount -> toAccount`로 직접 처리됩니다.

### Double Entry

- 모든 거래는 최소 2개의 분개를 남깁니다.
- 한쪽 계좌에서 차감되면 다른 한쪽 계좌에는 같은 금액이 증가합니다.
- 그래서 돈이 “사라지거나 생기는” 현상을 줄일 수 있습니다.

## 구현 관점 학습 포인트

### 1. 트랜잭션

- `TransferService`의 주요 메서드는 `@Transactional`로 묶여 있습니다.
- 거래 row 생성, 잔액 변경, 원장 기록이 하나의 작업 단위로 처리됩니다.
- 중간 단계에서 실패하면 전체가 롤백되어야 합니다.

### 2. 멱등성

- `transfers.idempotency_key`에는 `UNIQUE` 제약이 있습니다.
- 같은 키가 다시 들어오면 새 거래를 만들지 않고 기존 거래를 반환합니다.
- 최종 구현은 `INSERT ... ON CONFLICT DO NOTHING` 방식이라 동시 중복 요청에서도 트랜잭션이 깨지지 않습니다.

### 3. 동시성 제어

- 출금과 계좌이체는 잔액을 바꾸는 작업이므로 race condition 위험이 있습니다.
- 최종 구현은 `AccountRepository.findByIdWithLock()`에 `PESSIMISTIC_WRITE`를 적용했습니다.
- 중요한 점은 `accountNumber`로 `Account` 엔티티를 먼저 읽으면 영속성 컨텍스트 때문에 락 조회가 무력화될 수 있다는 점입니다.
- 그래서 먼저 `findIdByAccountNumber()`로 ID만 조회하고, 그 다음 `findByIdWithLock()`로 실제 행 잠금을 잡습니다.

### 4. 데드락 회피

- 두 계좌를 동시에 잠글 때는 UUID 정렬 순서로 잠급니다.
- 락 획득 순서를 고정하면 역순 대기 가능성을 줄일 수 있습니다.

### 5. 예외 처리

- `AccountNotFoundException` -> `404`
- `InvalidAmountException` -> `400`
- `InsufficientFundsException`, `IllegalStateException` -> `422`
- 전역 예외 처리는 `GlobalExceptionHandler`가 담당합니다.

## 실제 검증 과정에서 배운 점

### Pessimistic Lock을 추가했다고 바로 해결되지 않음

처음에는 `findByIdWithLock()`를 추가했지만, 같은 트랜잭션 안에서 이미 `findByAccountNumber()`로 엔티티를 읽어온 상태라 실제 DB 락이 기대대로 걸리지 않았습니다.

즉:

- 겉보기에는 락 메서드가 존재
- 실제로는 race condition이 계속 발생

이 문제를 `ID만 선조회 -> 락 조회` 방식으로 수정한 뒤 정합성이 맞았습니다.

### Idempotency도 동시성 문제를 가짐

처음에는 `UNIQUE` 충돌을 `try/catch`로 복구하려 했지만, PostgreSQL에서는 예외 발생 후 현재 트랜잭션이 abort 상태가 되어 500이 발생했습니다.

최종적으로:

- `ON CONFLICT DO NOTHING`
- 중복이면 기존 거래 재조회

방식으로 바꾸면서 실제 동시 요청에서도 정상 동작하도록 수정했습니다.

## 실측 결과

### Race Condition 기준선

- 동일 계좌 동시 출금 `150건`
- 락 미적용 시 `150건` 모두 `200`
- 최종 잔액 `84,000원`
- 기대값과 맞지 않아 불일치 발생

### Pessimistic Lock 적용 후

- 동일 계좌 동시 출금 `150건`
- `200 = 100건`, `422 = 50건`
- 최종 잔액 `0원`
- 데이터 불일치 없음

### Idempotency Key 검증

- 동일 키 동시 요청 `2건`
- 두 응답이 같은 `transferId`
- 실제 거래 row `1건`
- 잔액 차감도 `1회`만 반영

## 이 프로젝트로 설명 가능한 주제

- Spring `@Transactional`의 실제 의미
- JPA 영속성 컨텍스트와 락 동작의 관계
- `PESSIMISTIC_WRITE`가 필요한 상황
- 금융성 데이터에서 race condition이 왜 위험한지
- DB `UNIQUE` 제약만으로는 멱등성이 완성되지 않는 이유
- `ON CONFLICT DO NOTHING` 기반 중복 방지 전략
- 이중기장과 잔액 정합성 검증 방식

## 참고 문서

- [README.md](../../README.md)
- [schema.md](./schema.md)
- [TESTING.md](./TESTING.md)
