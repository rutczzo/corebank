package com.example.corebank.web.dto;

public class Requests {
    public record DepositWithdraw(String accountNo, String idempotencyKey, Long amount) {}
    public record Transfer(String fromAccountNo, String toAccountNo, String idempotencyKey, Long amount) {}
}
