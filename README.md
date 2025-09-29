# Corebank - 간단한 코어뱅킹 학습 프로젝트

## 프로젝트 개요

이 프로젝트는 은행 핵심 시스템(Core Banking)을 단순화하여 직접 구현한 학습용 프로젝트입니다. 계좌(Account), 거래(Transfer), 원장(JournalEntry) 같은 은행 시스템의 기본 개념을 코드로 다루며, 트랜잭션, 멱등성(Idempotency), 이중기장(Double Entry Bookkeeping)과 같은 핵심 원리를 이해하는 데 목적이 있습니다.

보다 상세한 학습 내용 및 개념 설명은 [docs/LEARNING.md](./src/docs/LEARNING.md) 파일을 참고해 주세요.

---

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [데이터베이스 스키마](#데이터베이스-스키마)
- [API 명세](#api-명세)
- [시작하기](#시작하기)
- [사용법](#사용법-api-호출-예시)
- [라이선스](#라이선스)

---

## 주요 기능

- 계좌 입금, 출금, 이체 기능
- 모든 거래에 대한 원장(Journal) 기록
- 이중 기장 원칙에 따른 회계적 무결성 보장
- 멱등성 키(Idempotency Key)를 이용한 중복 요청 방지
- API 명세에 따른 명확한 에러 코드 반환

## 기술 스택

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.4
- **Data**: Spring Data JPA, PostgreSQL 16, Flyway
- **Build**: Gradle (Groovy DSL)
- **Etc**: Docker Compose
## 프로젝트 구조

```
src
├── main
│   ├── java
│   │   └── com/example/corebank
│   │       ├── CorebankApplication.java  # 애플리케이션 시작점
│   │       ├── common/error              # 커스텀 예외 및 전역 핸들러
│   │       ├── config                    # DataInitializer 등 설정 클래스
│   │       ├── domain                    # JPA 엔티티 (e.g., Account, Transfer)
│   │       ├── repository                # Spring Data JPA 리포지토리
│   │       ├── service                   # 비즈니스 로직
│   │       └── web                       # API 컨트롤러 및 DTO
│   └── resources
│       ├── application.yml             # Spring 애플리케이션 설정
│       └── db/migration                # Flyway DB 마이그레이션 스크립트
└── test
    └── java
        └── com/example/corebank
            └── CorebankApplicationTests.java
```

- **domain**: 데이터베이스 테이블과 매핑되는 엔티티 클래스가 위치합니다.
- **repository**: 데이터를 조회하고 저장하는 JPA 인터페이스가 위치합니다.
- **service**: 핵심 비즈니스 로직을 처리합니다. 트랜잭션 관리의 단위가 됩니다.
- **web**: 외부 요청을 받는 컨트롤러와 데이터 전송 객체(DTO)가 위치합니다.
- **common/error**: 비즈니스 예외 클래스와 이를 처리하는 전역 예외 핸들러가 위치합니다.
- **config**: 애플리케이션 실행 시 필요한 초기 설정(e.g., 테스트 데이터 생성)을 담당합니다.

## 데이터베이스 스키마

주요 엔티티는 `Customer`, `Account`, `Transfer`, `JournalEntry`로 구성됩니다. 각 엔티티는 아래와 같은 관계를 가집니다.

- `Customer` (1) : (N) `Account`
- `Account` (1) : (N) `Transfer` (From 또는 To)
- `Transfer` (1) : (N) `JournalEntry`

상세한 스키마 정보는 **[src/docs/schema.md](./src/docs/schema.md)** 파일에서 확인할 수 있습니다.

## API 명세

- `POST /api/transactions/deposit`: 계좌 입금
- `POST /api/transactions/withdraw`: 계좌 출금
- `POST /api/transactions/transfer`: 계좌 이체

**에러 응답**
- `400 Bad Request`: 잘못된 요청 값 (e.g., 금액이 음수)
- `404 Not Found`: 존재하지 않는 계좌
- `422 Unprocessable Entity`: 비즈니스 규칙 위반 (e.g., 잔액 부족)
- `409 Conflict`: 멱등성 키 중복 (동일한 요청 재시도 시)

## 시작하기

### 요구 사항
- Java 21
- Docker

### 설치 및 실행
1.  **프로젝트 클론**
    ```bash
    git clone <repository-url>
    ```

2.  **데이터베이스 실행**
    프로젝트 루트 디렉토리에서 Docker Compose를 실행하여 PostgreSQL 데이터베이스를 시작합니다.
    ```bash
    docker-compose up -d
    ```

3.  **애플리케이션 실행**
    ```bash
    ./gradlew bootRun
    ```
    애플리케이션이 시작되면 테스트에 필요한 계좌(HOUSE-0001, A-0001, B-0001)가 자동으로 생성됩니다.

## 사용법

애플리케이션 실행 후, 아래 두 가지 방법으로 API를 테스트할 수 있습니다.

### 1. 스웨거 UI (권장)

웹 브라우저에서 아래 주소로 접속하면 API 문서를 확인하고 직접 테스트를 수행할 수 있는 UI가 제공됩니다.

- **URL**: `http://localhost:8080/swagger-ui.html`

### 2. cURL (스크립트용)

- **입금**
  ```bash
  curl -X POST http://localhost:8080/api/transactions/deposit \
   -H "Content-Type: application/json" \
   -d '{"accountNo":"A-0001","idempotencyKey":"unique-deposit-key-001","amount":5000}'
  ```

- **출금**
  ```bash
  curl -X POST http://localhost:8080/api/transactions/withdraw \
   -H "Content-Type: application/json" \
   -d '{"accountNo":"A-0001","idempotencyKey":"unique-withdraw-key-001","amount":1000}'
  ```

- **계좌 이체**
  ```bash
  curl -X POST http://localhost:8080/api/transactions/transfer \
   -H "Content-Type: application/json" \
   -d '{"fromAccountNo":"A-0001","toAccountNo":"B-0001","idempotencyKey":"unique-transfer-key-001","amount":2000}'
  ```

## 라이선스

[LICENSE](./LICENSE) 파일을 참고해 주세요.