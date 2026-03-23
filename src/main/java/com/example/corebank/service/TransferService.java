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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepo;
    private final JournalEntryRepository journalRepo;
    private final TransferRepository transferRepo;

    public TransferService(AccountRepository a,
                           JournalEntryRepository j,
                           TransferRepository t) {
        this.accountRepo = a;
        this.journalRepo = j;
        this.transferRepo = t;
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

        if (!insertTransferIfAbsent(t)) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
        t = transferRepo.findById(t.getId()).orElseThrow();

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

        t.setStatus("SUCCEEDED");
        transferRepo.save(t);
        return t;
    }

    @Transactional
    public Transfer withdraw(String fromAccountNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        assertPositive(amount);
        var fromId = getAccountIdByNumber(fromAccountNo);
        var houseId = getAccountIdByNumber("HOUSE-0001");
        var lockedAccounts = lockAccounts(fromId, houseId);
        var from = lockedAccounts.get(fromId);
        assertActive(from);
        assertSufficient(from, amount);
        var house = lockedAccounts.get(houseId);

        var t = new Transfer();
        t.setType("WITHDRAW");
        t.setFromAccount(from);
        t.setToAccount(house);
        t.setAmount(amount);
        t.setCurrency("KRW");
        t.setStatus("PENDING");
        t.setIdempotencyKey(idempotencyKey);

        if (!insertTransferIfAbsent(t)) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
        t = transferRepo.findById(t.getId()).orElseThrow();

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

        t.setStatus("SUCCEEDED");
        transferRepo.save(t);
        return t;
    }

    @Transactional
    public Transfer accountTransfer(String fromNo, String toNo, BigDecimal amount, String idempotencyKey) {
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        assertPositive(amount);
        var fromId = getAccountIdByNumber(fromNo);
        var toId = getAccountIdByNumber(toNo);
        var lockedAccounts = lockAccounts(fromId, toId);
        var from = lockedAccounts.get(fromId);
        var to = lockedAccounts.get(toId);
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

        if (!insertTransferIfAbsent(t)) {
            return transferRepo.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
        t = transferRepo.findById(t.getId()).orElseThrow();

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

        t.setStatus("SUCCEEDED");
        transferRepo.save(t);
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

    private UUID getAccountIdByNumber(String accountNo) {
        return accountRepo.findIdByAccountNumber(accountNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNo));
    }

    private Map<UUID, Account> lockAccounts(UUID... accountIds) {
        return List.of(accountIds).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(this::findByIdWithLock)
                .collect(java.util.stream.Collectors.toMap(Account::getId, account -> account));
    }

    private Account findByIdWithLock(UUID accountId) {
        return accountRepo.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private boolean insertTransferIfAbsent(Transfer transfer) {
        return transferRepo.insertIfAbsent(
                transfer.getId(),
                transfer.getType(),
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getIdempotencyKey()
        ) == 1;
    }
}
