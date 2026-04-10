package com.example.corebank.service;

import com.example.corebank.domain.Account;
import com.example.corebank.domain.Transfer;
import com.example.corebank.repository.AccountRepository;
import com.example.corebank.repository.JournalEntryRepository;
import com.example.corebank.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private JournalEntryRepository journalRepo;

    @Mock
    private TransferRepository transferRepo;

    @InjectMocks
    private TransferService transferService;

    @Test
    void depositLocksAccountsBeforeBalanceUpdate() {
        var toId = UUID.randomUUID();
        var houseId = UUID.randomUUID();
        var to = account(toId, "USER-0001", "ACTIVE", 1_000);
        var house = account(houseId, "HOUSE-0001", "ACTIVE", 10_000);
        var persisted = new Transfer();

        when(transferRepo.findByIdempotencyKey("dep-1")).thenReturn(Optional.empty());
        when(accountRepo.findIdByAccountNumber("USER-0001")).thenReturn(Optional.of(toId));
        when(accountRepo.findIdByAccountNumber("HOUSE-0001")).thenReturn(Optional.of(houseId));
        when(accountRepo.findByIdWithLock(eq(toId))).thenReturn(Optional.of(to));
        when(accountRepo.findByIdWithLock(eq(houseId))).thenReturn(Optional.of(house));
        when(transferRepo.insertIfAbsent(any(), eq("DEPOSIT"), eq(houseId), eq(toId), eq(BigDecimal.valueOf(500)), eq("KRW"), eq("PENDING"), eq("dep-1")))
                .thenReturn(1);
        when(transferRepo.findById(any())).thenReturn(Optional.of(persisted));
        when(transferRepo.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transferService.deposit("USER-0001", BigDecimal.valueOf(500), "dep-1");

        verify(accountRepo).findIdByAccountNumber("USER-0001");
        verify(accountRepo).findIdByAccountNumber("HOUSE-0001");
        verify(accountRepo).findByIdWithLock(houseId);
        verify(accountRepo).findByIdWithLock(toId);
        verify(accountRepo, never()).findByAccountNumber("USER-0001");
        verify(accountRepo, never()).findByAccountNumber("HOUSE-0001");

        var savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo, org.mockito.Mockito.times(2)).save(savedAccount.capture());
        assertThat(savedAccount.getAllValues())
                .extracting(Account::getAccountNumber, Account::getBalance)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("HOUSE-0001", BigDecimal.valueOf(9_500)),
                        org.assertj.core.groups.Tuple.tuple("USER-0001", BigDecimal.valueOf(1_500))
                );
    }

    @Test
    void depositDoesNotTouchBalancesWhenInsertIfAbsentReturnsZero() {
        var toId = UUID.randomUUID();
        var houseId = UUID.randomUUID();
        var to = account(toId, "USER-0001", "ACTIVE", 1_000);
        var house = account(houseId, "HOUSE-0001", "ACTIVE", 10_000);
        var existing = new Transfer();

        when(transferRepo.findByIdempotencyKey("dep-2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(accountRepo.findIdByAccountNumber("USER-0001")).thenReturn(Optional.of(toId));
        when(accountRepo.findIdByAccountNumber("HOUSE-0001")).thenReturn(Optional.of(houseId));
        when(accountRepo.findByIdWithLock(eq(toId))).thenReturn(Optional.of(to));
        when(accountRepo.findByIdWithLock(eq(houseId))).thenReturn(Optional.of(house));
        when(transferRepo.insertIfAbsent(any(), eq("DEPOSIT"), eq(houseId), eq(toId), eq(BigDecimal.valueOf(500)), eq("KRW"), eq("PENDING"), eq("dep-2")))
                .thenReturn(0);

        var result = transferService.deposit("USER-0001", BigDecimal.valueOf(500), "dep-2");

        assertThat(result).isSameAs(existing);
        assertThat(house.getBalance()).isEqualByComparingTo("10000");
        assertThat(to.getBalance()).isEqualByComparingTo("1000");
        verify(accountRepo, never()).save(any(Account.class));
        verify(journalRepo, never()).save(any());
    }

    private static Account account(UUID id, String accountNumber, String status, long balance) {
        var account = new Account();
        forceId(account, id);
        account.setAccountNumber(accountNumber);
        account.setStatus(status);
        account.setBalance(BigDecimal.valueOf(balance));
        return account;
    }

    private static void forceId(Account account, UUID id) {
        try {
            var field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set account id for test", e);
        }
    }
}
