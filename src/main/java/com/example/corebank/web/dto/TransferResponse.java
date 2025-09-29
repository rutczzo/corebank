package com.example.corebank.web.dto;

import java.util.UUID;

public record TransferResponse(UUID transferId, String status) {}
