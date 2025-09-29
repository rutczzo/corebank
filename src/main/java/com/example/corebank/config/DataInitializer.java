package com.example.corebank.config;

import com.example.corebank.domain.Account;
import com.example.corebank.domain.Customer;
import com.example.corebank.repository.AccountRepository;
import com.example.corebank.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepo;
    private final AccountRepository accountRepo;

    public DataInitializer(CustomerRepository customerRepo, AccountRepository accountRepo) {
        this.customerRepo = customerRepo;
        this.accountRepo = accountRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. House Customer & Account 생성
        createAccountIfNotExists("HOUSE", "HOUSE-0001", true);

        // 2. Test User (Alice) & Account 생성
        createAccountIfNotExists("Alice", "A-0001", false);

        // 3. Test User (Bob) & Account 생성
        createAccountIfNotExists("Bob", "B-0001", false);
    }

    private void createAccountIfNotExists(String customerName, String accountNumber, boolean isHouseAccount) {
        Customer customer = customerRepo.findByName(customerName)
                .orElseGet(() -> {
                    Customer newCustomer = new Customer();
                    newCustomer.setName(customerName);
                    return customerRepo.save(newCustomer);
                });

        accountRepo.findByAccountNumber(accountNumber)
                .orElseGet(() -> {
                    Account newAccount = new Account();
                    newAccount.setCustomer(customer);
                    newAccount.setAccountNumber(accountNumber);
                    newAccount.setCurrency("KRW");
                    newAccount.setStatus("ACTIVE");
                    newAccount.setBalance(BigDecimal.ZERO);
                    newAccount.setHouse(isHouseAccount);
                    return accountRepo.save(newAccount);
                });
    }
}
