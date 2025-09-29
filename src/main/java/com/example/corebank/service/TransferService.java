package com.example.corebank.service;

import com.example.corebank.common.error.AccountNotFoundException;
import com.example.corebank.common.error.InsufficientFundsException;
import com.example.corebank.common.error.InvalidAmountException;
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

    @Transactional
    public Transfer deposit(String toAccountNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        assertPositive(amount);
        var to = accountRepo.findByAccountNumber(toAccountNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccountNo));
        assertActive(to);

        var house = accountRepo.findByAccountNumber("HOUSE-0001")
                .orElseThrow(() -> new AccountNotFoundException("House account not found"));

        var t = new Transfer();
        t.setType("DEPOSIT");
        t.setFromAccount(house);
        t.setToAccount(to);
        t.setAmount(amount);
        t.setCurrency("KRW");
        t.setStatus("PENDING");
        t.setIdempotencyKey(idempotencyKey);

        try {
            transferRepo.saveAndFlush(t);
        } catch (DataIntegrityViolationException dup) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        house.setBalance(house.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepo.save(house);
        accountRepo.save(to);

        var jeD = new JournalEntry();
        jeD.setTransfer(t); jeD.setAccount(house);
        jeD.setDirection("D");
        jeD.setAmount(amount);
        journalRepo.save(jeD);

        var jeC = new JournalEntry();
        jeC.setTransfer(t); jeC.setAccount(to);
        jeC.setDirection("C");
        jeC.setAmount(amount);
        journalRepo.save(jeC);

        t.setStatus("SUCCESS");
        return t;
    }

    @Transactional
    public Transfer withdraw(String fromAccountNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        assertPositive(amount);
        var from = accountRepo.findByAccountNumber(fromAccountNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccountNo));
        assertActive(from);
        assertSufficient(from, amount);

        var house = accountRepo.findByAccountNumber("HOUSE-0001")
                .orElseThrow(() -> new AccountNotFoundException("House account not found"));

        var t = new Transfer();
        t.setType("WITHDRAW");
        t.setFromAccount(from);
        t.setToAccount(house);
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
        house.setBalance(house.getBalance().add(amount));
        accountRepo.save(from);
        accountRepo.save(house);

        var jeD = new JournalEntry();
        jeD.setTransfer(t); jeD.setAccount(from);
        jeD.setDirection("D");
        jeD.setAmount(amount);
        journalRepo.save(jeD);

        var jeC = new JournalEntry();
        jeC.setTransfer(t); jeC.setAccount(house);
        jeC.setDirection("C");
        jeC.setAmount(amount);
        journalRepo.save(jeC);

        t.setStatus("SUCCESS");
        return t;
    }

    @Transactional
    public Transfer accountTransfer(String fromNo, String toNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        assertPositive(amount);
        var from = accountRepo.findByAccountNumber(fromNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromNo));
        var to = accountRepo.findByAccountNumber(toNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toNo));
        if (from.getId().equals(to.getId()))
            throw new IllegalStateException("cannot transfer to same account");
        assertActive(from);
        assertActive(to);
        assertSufficient(from, amount);

        var t = new Transfer();
        t.setType("ACCOUNT_TRANSFER");
        t.setFromAccount(from);
        t.setToAccount(to);
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
        to.setBalance(to.getBalance().add(amount));
        accountRepo.save(from);
        accountRepo.save(to);

        var jeD = new JournalEntry();
        jeD.setTransfer(t);
        jeD.setAccount(from);
        jeD.setDirection("D");
        jeD.setAmount(amount);
        journalRepo.save(jeD);

        var jeC = new JournalEntry();
        jeC.setTransfer(t);
        jeC.setAccount(to);
        jeC.setDirection("C");
        jeC.setAmount(amount);
        journalRepo.save(jeC);

        t.setStatus("SUCCESS");
        return t;
    }

    private static void assertPositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("amount must be positive");
        }
    }

    private static void assertActive(Account acc) {
        if (!"ACTIVE".equals(acc.getStatus())) {
            throw new IllegalStateException("account not ACTIVE");
        }
    }

    private static void assertSufficient(Account acc, BigDecimal amount) {
        if (acc.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("insufficient funds");
        }
    }
}