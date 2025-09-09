package com.example.corebank.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity @Table(name="journal_entries", indexes = {
        @Index(name="idx_journal_account_created", columnList="account_id, created_at DESC"),
        @Index(name="idx_journal_transfer", columnList="transfer_id")
})
public class JournalEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="transfer_id", nullable=false)
    private Transfer transfer;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="account_id", nullable=false)
    private Account account;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "direction", nullable=false, columnDefinition="char(1)")
    private String direction; // 'D' or 'C'

    @Column(nullable=false, precision=19, scale=0)
    private BigDecimal amount;

    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters/setters
    public Long getId() { return id; }
    public Transfer getTransfer() { return transfer; }
    public Account getAccount() { return account; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setTransfer(Transfer transfer) { this.transfer = transfer; }
    public void setAccount(Account account) { this.account = account; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
