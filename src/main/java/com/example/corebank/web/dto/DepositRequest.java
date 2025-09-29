package com.example.corebank.web.dto;

public record DepositRequest(String accountNo, String idempotencyKey, Long amount) {}
