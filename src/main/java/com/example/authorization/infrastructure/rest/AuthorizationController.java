package com.example.authorization.infrastructure.rest;

import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.example.authorization.infrastructure.rest.dto.AuthorizationRequest;
import com.example.authorization.infrastructure.rest.dto.AuthorizationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

    private final AuthorizeTransactionUseCase useCase;

    public AuthorizationController(AuthorizeTransactionUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizationResponse> authorize(@RequestBody AuthorizationRequest request) {
        var transaction = new Transaction(
                request.transactionId(),
                request.accountId(),
                request.merchantId(),
                Money.of(request.amount(), request.currency()),
                Instant.now()
        );

        var result = useCase.authorize(transaction);
        return ResponseEntity.ok(AuthorizationResponse.from(result));
    }
}
