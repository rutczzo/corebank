package com.example.corebank.web;

import com.example.corebank.domain.Transfer;
import com.example.corebank.service.TransferService;
import com.example.corebank.web.dto.Requests;
import com.example.corebank.web.dto.Responses.TransferResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transactions")
public class AccountController {

    private final TransferService svc;
    public AccountController(TransferService svc) { this.svc = svc; }

    @PostMapping("/deposit")
    public ResponseEntity<TransferResult> deposit(@RequestBody Requests.DepositWithdraw r) {
        Transfer t = svc.deposit(r.accountNo(), BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new TransferResult(t.getId(), t.getStatus()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransferResult> withdraw(@RequestBody Requests.DepositWithdraw r) {
        Transfer t = svc.withdraw(r.accountNo(), BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new TransferResult(t.getId(), t.getStatus()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResult> transfer(@RequestBody Requests.Transfer r) {
        Transfer t = svc.accountTransfer(
                r.fromAccountNo(), r.toAccountNo(),
                BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new TransferResult(t.getId(), t.getStatus()));
    }
}
