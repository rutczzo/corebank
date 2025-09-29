package com.example.corebank.web.dto;

import java.util.UUID;

public record WithdrawResponse(UUID transferId, String status) {}
