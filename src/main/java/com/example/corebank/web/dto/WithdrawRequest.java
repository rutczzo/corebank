package com.example.corebank.web.dto;

public record WithdrawRequest(String accountNo, String idempotencyKey, Long amount) {}
