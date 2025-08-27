# Core Banking Toy Project

Spring Boot + PostgreSQL 기반 **간단 코어뱅킹 시스템(MVP)**  
(고객/계좌 생성, 입금/출금, 계좌 간 이체, 잔액 조회 기능 포함 예정)

---

## ⚙ Tech Stack
- Java 21
- Spring Boot 3.3.x
- PostgreSQL 16
- Spring Data JPA (Hibernate)
- Flyway (DB Migration)
- Actuator (헬스체크)
- Gradle (Groovy DSL)
- Docker (Postgres 실행용)

---

## 📚 Core Banking Concepts

이 프로젝트는 단순한 잔액 관리가 아니라 **은행 코어뱅킹 시스템의 핵심 원칙**을 토이 형태로 구현합니다.

- **Ledger (원장)**  
  모든 거래는 원장(Journal)에 기록됩니다. 잔액(balance)은 원장의 합산 결과이며,  
  원장을 통해 언제/어디서/얼마가 이동했는지 추적 가능합니다.

- **House Account (하우스 계정)**  
  은행 내부 정산 계정입니다.  
  입금/출금은 항상 고객 계좌와 하우스 계좌 간 더블 엔트리(double entry)로 기록됩니다.

- **Transaction Types**
  - Deposit: 고객 계좌에 돈을 넣는 거래
  - Withdraw: 고객 계좌에서 돈을 빼는 거래
  - Account Transfer: 계좌 A에서 계좌 B로 송금하는 거래

- **Double Entry Principle**  
  모든 거래는 최소 두 줄(Debit/입금, Credit/출금)로 기록되어야 하며,  
  이렇게 함으로써 “돈이 사라지거나 생겨나는 상황”을 원천 차단합니다.

