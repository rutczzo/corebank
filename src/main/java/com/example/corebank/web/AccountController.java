package com.example.corebank.web;

import com.example.corebank.domain.Transfer;
import com.example.corebank.service.TransferService;
import com.example.corebank.web.dto.DepositRequest;
import com.example.corebank.web.dto.DepositResponse;
import com.example.corebank.web.dto.TransferRequest;
import com.example.corebank.web.dto.TransferResponse;
import com.example.corebank.web.dto.WithdrawRequest;
import com.example.corebank.web.dto.WithdrawResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/transactions")
public class AccountController {

    private final TransferService svc;
    public AccountController(TransferService svc) { this.svc = svc; }

    @PostMapping("/deposit")
    public ResponseEntity<DepositResponse> deposit(@RequestBody DepositRequest r) {
        Transfer t = svc.deposit(r.accountNo(), BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new DepositResponse(t.getId(), t.getStatus()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(@RequestBody WithdrawRequest r) {
        Transfer t = svc.withdraw(r.accountNo(), BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new WithdrawResponse(t.getId(), t.getStatus()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest r) {
        Transfer t = svc.accountTransfer(
                r.fromAccountNo(), r.toAccountNo(),
                BigDecimal.valueOf(r.amount()), r.idempotencyKey());
        return ResponseEntity.ok(new TransferResponse(t.getId(), t.getStatus()));
    }
}