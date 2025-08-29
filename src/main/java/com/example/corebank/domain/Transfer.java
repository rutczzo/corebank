package com.example.corebank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="transfers", indexes = {
        @Index(name="idx_transfers_created", columnList="created_at")
})
public class Transfer {
    @Id @Column(columnDefinition="uuid")
    private UUID id = UUID.randomUUID();

    @Column(nullable=false, length=20)
    private String type; // DEPOSIT / WITHDRAW / ACCOUNT_TRANSFER

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="from_account")
    private Account fromAccount;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="to_account")
    private Account toAccount;

    @Column(nullable=false, precision=19, scale=0)
    private BigDecimal amount;

    @Column(nullable=false, length=3)
    private String currency = "KRW";

    @Column(nullable=false, length=16)
    private String status; // PENDING / SUCCEEDED / FAILED

    @Column(name="idempotency_key", unique=true, length=64)
    private String idempotencyKey;

    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters/setters
    public UUID getId() { return id; }
    public String getType() { return type; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setType(String type) { this.type = type; }
    public void setFromAccount(Account fromAccount) { this.fromAccount = fromAccount; }
    public void setToAccount(Account toAccount) { this.toAccount = toAccount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(String status) { this.status = status; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
