package com.example.corebank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="accounts", indexes = {
        @Index(name="idx_accounts_customer", columnList="customer_id")
})
public class Account {
    @Id @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", nullable = false)
    private Customer customer;

    @Column(name="account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Column(nullable=false, length=3)  private String currency = "KRW";
    @Column(nullable=false, length=16) private String status   = "ACTIVE"; // ACTIVE/FROZEN/CLOSED
    @Column(name="is_house", nullable=false) private boolean house = false;

    @Column(nullable=false, precision=19, scale=0)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name="created_at", nullable=false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters/setters
    public UUID getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getAccountNumber() { return accountNumber; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public boolean isHouse() { return house; }
    public BigDecimal getBalance() { return balance; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCustomer(Customer customer) { this.customer = customer; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(String status) { this.status = status; }
    public void setHouse(boolean house) { this.house = house; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

}
