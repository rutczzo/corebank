package com.example.corebank.web.dto;
import java.util.UUID;

public class Responses {
    public record TransferResult(UUID transferId, String status) {}
}
