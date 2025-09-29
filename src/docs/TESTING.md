# TESTING - 수동 테스트 시나리오

본 문서는 Corebank 프로젝트의 주요 기능에 대한 수동 테스트 절차를 정리합니다.

---

## 0. 사전 준비

1.  **데이터베이스 실행**
    ```bash
    docker-compose up -d
    ```
2.  **애플리케이션 실행**
    ```bash
    ./gradlew bootRun
    ```
    애플리케이션이 시작되면 테스트에 필요한 계좌(HOUSE-0001, A-0001, B-0001)가 잔액 0원으로 자동 생성됩니다.

3.  **스웨거 UI 접속**
    웹 브라우저에서 아래 주소로 접속합니다.
    - `http://localhost:8080/swagger-ui.html`

---

## 1. 테스트 시나리오

아래 시나리오에 따라 스웨거 UI에서 각 API를 순서대로 호출하며 결과를 확인합니다.

### STEP 1: 입금 (성공)

-   `POST /api/transactions/deposit` 항목을 펼치고 `Try it out`을 클릭합니다.
-   `Request body`에 아래와 같이 입력합니다.
    ```json
    {
      "accountNo": "A-0001",
      "idempotencyKey": "test-deposit-001",
      "amount": 5000
    }
    ```
-   `Execute` 버튼을 클릭합니다.
-   **결과 확인**:
     - `Responses` 섹션에서 `Code`가 `200`이고, 응답 본문에 `status: "SUCCESS"`가 표시되는지 확인합니다.

### STEP 2: 멱등성 테스트 (입금)

-   STEP 1과 **완전히 동일한 내용**으로 다시 `Execute` 버튼을 클릭합니다.
-   **결과 확인**: 응답은 `200 SUCCESS`로 동일하게 오는지 확인합니다. (DB 잔액은 변동 없음)

### STEP 3: 출금 (성공)

-   `POST /api/transactions/withdraw` 항목을 펼치고 `Try it out`을 클릭합니다.
-   `Request body`에 아래와 같이 입력합니다.
    ```json
    {
      "accountNo": "A-0001",
      "idempotencyKey": "test-withdraw-001",
      "amount": 1000
    }
    ```
-   `Execute` 버튼을 클릭합니다.
-   **결과 확인**: `200 SUCCESS` 응답을 확인합니다. (A-0001 잔액: 4000)

### STEP 4: 계좌 이체 (성공)

-   `POST /api/transactions/transfer` 항목을 펼치고 `Try it out`을 클릭합니다.
-   `Request body`에 아래와 같이 입력합니다.
    ```json
    {
      "fromAccountNo": "A-0001",
      "toAccountNo": "B-0001",
      "idempotencyKey": "test-transfer-001",
      "amount": 2000
    }
    ```
-   `Execute` 버튼을 클릭합니다.
-   **결과 확인**: `200 SUCCESS` 응답을 확인합니다. (A-0001 잔액: 2000, B-0001 잔액: 2000)

### STEP 5: 오류 테스트 (잔액 부족)

-   `POST /api/transactions/withdraw` 항목에서 `Try it out`을 클릭합니다.
-   `Request body`에 아래와 같이 입력합니다. (잔액 2000원인데 3000원 출금 시도)
    ```json
    {
      "accountNo": "A-0001",
      "idempotencyKey": "test-error-001",
      "amount": 3000
    }
    ```
-   `Execute` 버튼을 클릭합니다.
-   **결과 확인**: `Code`가 **`422`** 이고, 응답 본문에 `error: "insufficient funds"` 메시지가 포함되어 있는지 확인합니다.

### STEP 6: 오류 테스트 (자기 이체)

-   `POST /api/transactions/transfer` 항목에서 `Try it out`을 클릭합니다.
-   `Request body`에 아래와 같이 입력합니다.
    ```json
    {
      "fromAccountNo": "A-0001",
      "toAccountNo": "A-0001",
      "idempotencyKey": "test-error-002",
      "amount": 100
    }
    ```
-   `Execute` 버튼을 클릭합니다.
-   **결과 확인**: `Code`가 **`422`** 이고, 응답 본문에 `error: "cannot transfer to same account"` 메시지가 포함되어 있는지 확인합니다.
