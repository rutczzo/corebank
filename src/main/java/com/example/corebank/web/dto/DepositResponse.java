package com.example.corebank.web.dto;

import java.util.UUID;

public record DepositResponse(UUID transferId, String status) {}
