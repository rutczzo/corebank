package com.example.corebank.service;

import com.example.corebank.domain.Account;
import com.example.corebank.domain.JournalEntry;
import com.example.corebank.domain.Transfer;
import com.example.corebank.repository.AccountRepository;
import com.example.corebank.repository.JournalEntryRepository;
import com.example.corebank.repository.TransferRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final AccountRepository accountRepo;
    private final JournalEntryRepository journalRepo;
    private final TransferRepository transferRepo;

    public TransferService(AccountRepository a, JournalEntryRepository j, TransferRepository t) {
        this.accountRepo = a; this.journalRepo = j; this.transferRepo = t;
    }

    // Deposit: toAccount에 CREDIT 1건 + balance 증가

    @Transactional
    public Transfer deposit(String toAccountNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get(); // 멱등 재진입

        assertPositive(amount); // 잔액이 있는지 검증
        var to = accountRepo.findByAccountNumber(toAccountNo)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + toAccountNo));
        assertActive(to);

        var t = new Transfer();

        t.setType("DEPOSIT");
        t.setToAccount(to);
        t.setAmount(amount);
        t.setCurrency("KRW");
        t.setStatus("PENDING");
        t.setIdempotencyKey(idempotencyKey);

        try {
            transferRepo.saveAndFlush(t); // UNIQUE(idempotency_Key) 판단
        } catch (DataIntegrityViolationException dup) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        // 상태 반영(트랜잭션 단위)
        to.setBalance(to.getBalance().add(amount));
        accountRepo.save(to);

        var jeC = new JournalEntry();
        jeC.setTransfer(t); jeC.setAccount(to);
        jeC.setDirection("C"); // 은행 입장에서 부채 증가
        jeC.setAmount(amount); journalRepo.save(jeC);

        t.setStatus("SUCCESS");
        return t;
    }

    // withdraw: fromAccount에 DEBIT 1건 + balance 감소

    @Transactional
    public Transfer withdraw(String fromAccountNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get(); // 멱등 재진입

        assertPositive(amount);
        var from = accountRepo.findByAccountNumber(fromAccountNo)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + fromAccountNo));
        assertActive(from);
        assertSufficient(from, amount);

        var t = new Transfer();
        t.setType("WITHDRAW");
        t.setToAccount(from);
        t.setAmount(amount);
        t.setCurrency("KRW");
        t.setStatus("PENDING");
        t.setIdempotencyKey(idempotencyKey);

        try {
            transferRepo.saveAndFlush(t);
        } catch (DataIntegrityViolationException dup) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        from.setBalance(from.getBalance().subtract(amount));
        accountRepo.save(from);

        var jeD = new JournalEntry();
        jeD.setTransfer(t); jeD.setAccount(from);
        jeD.setDirection("D"); // 은행 입장에서 부채 감소
        jeD.setAmount(amount); journalRepo.save(jeD);

        t.setStatus("SUCCESS");
        return t;
    }

    // Account Transfer: from D, to C, balance 양쪽 수정

    @Transactional
    public Transfer accountTransfer(String fromNo, String toNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get(); // 멱등 재진입

        assertPositive(amount);
        var from = accountRepo.findByAccountNumber(fromNo)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + fromNo));
        var to = accountRepo.findByAccountNumber(toNo)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + toNo));
        if (from.getId().equals(to.getId()))
            throw new IllegalStateException("cannot transfer to same account");
        assertActive(from); assertActive(to);
        assertSufficient(from, amount);

        var t = new Transfer();
        t.setType("ACCOUNT_TRANSFER");
        t.setToAccount(from); t.setToAccount(to);
        t.setAmount(amount); t.setCurrency("KRW");
        t.setStatus("PENDING"); t.setIdempotencyKey(idempotencyKey);

        try {
            transferRepo.saveAndFlush(t);
        } catch (DataIntegrityViolationException dup) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepo.save(from); accountRepo.save(to);

        var jeD = new JournalEntry();
        jeD.setTransfer(t); jeD.setAccount(from);
        jeD.setDirection("D"); jeD.setAmount(amount);
        journalRepo.save(jeD);

        var jeC = new JournalEntry();
        jeC.setTransfer(t); jeC.setAccount(to);
        jeC.setDirection("C"); jeC.setAmount(amount);
        journalRepo.save(jeC);

        t.setStatus("SUCCESS");
        return t;
    }

    //  검증

    private static void assertPositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("must be positive");
        }
    }

    private static void assertActive(Account acc) {
        if (!"ACTIVE".equals(acc.getStatus())) {
            throw new IllegalArgumentException("account not ACTIVE");
        }
    }

    private static void assertSufficient(Account acc, BigDecimal amount) {
        if (acc.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("insufficient funds");
        }
    }

}
