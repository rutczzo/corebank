package com.example.corebank.web.dto;

public record TransferRequest(String fromAccountNo, String toAccountNo, String idempotencyKey, Long amount) {}
