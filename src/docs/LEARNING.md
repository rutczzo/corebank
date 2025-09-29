## Core Banking Concepts & Key Principles

이 프로젝트는 단순한 잔액 관리가 아니라 은행 코어뱅킹 시스템의 핵심 원칙을 토이 형태로 구현하며, 그 과정에서 소프트웨어 아키텍처와 데이터베이스의 중요한 개념을 학습합니다.

### 은행 도메인 개념
- **Ledger (원장)**  
  모든 거래는 원장(Journal)에 기록됩니다. 잔액(balance)은 원장의 합산 결과이며, 원장을 통해 언제/어디서/얼마가 이동했는지 추적 가능합니다.

- **House Account (하우스 계정)**  
  은행 내부 정산 계정입니다. 입금/출금은 항상 고객 계좌와 하우스 계좌 간 더블 엔트리(double entry)로 기록됩니다.

- **Transaction Types**
  - Deposit: 고객 계좌에 돈을 넣는 거래
  - Withdraw: 고객 계좌에서 돈을 빼는 거래
  - Account Transfer: 계좌 A에서 계좌 B로 송금하는 거래

- **Double Entry Principle**  
  모든 거래는 최소 두 줄(Debit/차변, Credit/대변)으로 기록되어야 하며, 이렇게 함으로써 “돈이 사라지거나 생겨나는 상황”을 원천 차단합니다.

### 구현/소프트웨어 개념
- **트랜잭션 (Transaction)**  
  거래는 반드시 모두 성공하거나 전혀 반영되지 않아야 합니다. Spring의 `@Transactional`로 하나의 트랜잭션에서 처리하며, 실패 시 전체 롤백됩니다.

- **멱등성 (Idempotency)**  
  같은 거래 의도가 여러 번 요청되더라도 결과는 한 번만 반영되어야 합니다. `transfers.idempotency_key`에 UNIQUE 제약을 걸어 중복 삽입을 막고, 이미 존재하면 같은 결과를 반환합니다.

- **DTO 분리 (API 계약 고정)**  
  API 요청과 응답은 반드시 DTO를 통해 이루어집니다. 엔티티를 그대로 노출하지 않음으로써, 보안 문제와 내부 구조 변경에 따른 외부 영향이 차단됩니다.

- **전역 예외 처리 (Global Exception Handling)**
  `@ControllerAdvice`를 사용하여 서비스 계층에서 발생한 특정 예외(e.g., `AccountNotFoundException`)를 잡아, API 명세에 맞는 HTTP 상태 코드(e.g., 404 Not Found)로 변환하여 일관된 에러 응답을 제공합니다.

---

## 전체 구조와 흐름

**웹 계층 (Controller)**  
사용자와 상호작용합니다.  
HTTP 요청(JSON)을 받아 요청 DTO로 변환하고 → 서비스 계층을 호출합니다.  
서비스 결과(엔티티)는 그대로 노출하지 않고, 응답 DTO로 변환해 반환합니다.  
→ 책임: **입출력(I/O), API 계약**

⬇️ **DTO(Data Transfer Object)** 를 매개로 값 전달  
요청 DTO는 사용자 입력을, 응답 DTO는 서비스 결과를 외부에 맞는 형식으로 고정합니다.

**서비스 계층 (Service)**  
비즈니스 로직이 담기는 핵심 계층입니다.  
예: 출금 시 잔액 충분 여부 확인, 이체 시 출금 계좌와 입금 계좌가 달라야 함.  
트랜잭션(@Transactional) 관리, 멱등성 검증(idempotencyKey)도 이 계층에서 수행합니다.  
서비스는 리포지토리를 통해 엔티티를 조회·수정하고, 원장(JournalEntry)에 거래 기록을 남깁니다.  
→ 책임: **규칙과 절차, 트랜잭션 관리**

⬇️ **Repository 호출**

**리포지토리 계층 (Repository)**  
DB 접근 전용 계층입니다.  
Spring Data JPA 인터페이스만 정의하며, 구현은 프레임워크가 생성합니다.  
리포지토리는 단순히 엔티티를 저장/조회하고, 비즈니스 규칙은 두지 않습니다.  
DB 제약조건(UNIQUE, FK)을 활용해 무결성과 동시성을 보장합니다.  
→ 책임: **데이터 접근 경계**

⬇️ **엔티티(Entity)와 매핑**

**도메인 (Entity)**  
DB 테이블과 매핑된 객체입니다.  
예: `Customer`, `Account`, `Transfer`, `JournalEntry`.  
계좌의 balance, status 같은 상태를 보관하고, 기본적인 제약조건을 표현합니다.  
학습 단계에서는 게터/세터를 열어 직관적으로 다루지만,  
실제 서비스 수준에서는 세터 대신 `credit`, `debit`, `freeze` 같은 **의도 메서드**로 상태 변경을 강제합니다.  
→ 책임: **데이터 표현 + 기본 규칙 내장**

---

## 데이터 모델과 규칙

> 스키마: [schema.md](./schema.md)

**엔티티**
- Customer — 고객
- Account — 계좌 (고객 소유, balance, status 포함)
- Transfer — 거래 (입금/출금/이체, 멱등성 키 포함)
- JournalEntry — 원장 기록 (Debit/Credit, Transfer와 연결)

**불변식(규칙)**
- amount > 0
- 계좌는 ACTIVE 상태여야 거래 가능
- 이체 시 출금 계좌 ≠ 입금 계좌
- 잔액 부족 시 출금/이체 불가
- 모든 거래는 원장 기록 필수

---

## 학습 포인트
- Flyway로 DB 스키마를 버전 관리하고, Spring JPA는 검증(`ddl-auto=validate`)만 수행
- JPA 엔티티와 DB 스키마를 1:1로 매핑하는 방법
- 서비스 계층에 비즈니스 규칙을 집중시키는 이유
- 멱등성 보장 전략(DB 제약조건 활용)
- 이중기장의 원리와 회계적 의미
- API 설계에서 DTO 분리의 중요성
- `@ControllerAdvice`를 이용한 중앙화된 예외 처리의 장점

---

## 확장 아이디어 (추가 학습 주제)

이 프로젝트는 학습용 MVP 수준이지만, 이후 확장을 통해 더 깊은 은행 도메인과 데이터베이스 개념을 실험할 수 있습니다.

- **트랜잭션 격리 수준 (Isolation Levels)**
  - READ COMMITTED / REPEATABLE READ / SERIALIZABLE 비교
  - 동시성 문제(Dirty Read, Phantom Read 등)를 재현하고 해결 방법 실험

- **데이터베이스 교체 학습**
  - PostgreSQL → MySQL로 교체 시 차이점
    - AUTO_INCREMENT vs SERIAL/IDENTITY
    - DATETIME vs TIMESTAMPTZ
    - UNIQUE 제약 및 Lock 동작 방식
  - 동일한 도메인 모델을 RDB마다 어떻게 적용할지 비교

- **ORM ↔ 순수 SQL 비교**
  - JPA/Hibernate 기반 설계 vs 직접 SQL (MyBatis, JDBC Template 등)
  - 성능, 제어 수준, 코드 복잡도 차이를 체감

- **고급 주제**
  - 분산 트랜잭션, CQRS, 이벤트 소싱
  - Kafka 같은 메시지 브로커와 연계한 비동기 처리
  - 다중 통화(Multi-Currency), 환율 적용 로직

이 확장 아이디어들은 실제 금융 시스템에서 자주 고민되는 주제이며, 본 프로젝트를 바탕으로 점진적으로 실험해볼 수 있습니다.