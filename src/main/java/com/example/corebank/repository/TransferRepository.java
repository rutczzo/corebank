package com.example.corebank.repository;

import com.example.corebank.domain.Transfer;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO transfers (id, type, from_account, to_account, amount, currency, status, idempotency_key)
            VALUES (:id, :type, :fromAccountId, :toAccountId, :amount, :currency, :status, :idempotencyKey)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id,
                       String type,
                       UUID fromAccountId,
                       UUID toAccountId,
                       BigDecimal amount,
                       String currency,
                       String status,
                       String idempotencyKey);
}
