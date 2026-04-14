package com.example.authorization.infrastructure.rest;

import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.example.authorization.infrastructure.rest.dto.AuthorizationRequest;
import com.example.authorization.infrastructure.rest.dto.AuthorizationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

    private final AuthorizeTransactionUseCase useCase;
    private final KafkaStreamsHealthGuard streamsHealthGuard;

    public AuthorizationController(AuthorizeTransactionUseCase useCase,
                                   KafkaStreamsHealthGuard streamsHealthGuard) {
        this.useCase = useCase;
        this.streamsHealthGuard = streamsHealthGuard;
    }

    @PostMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestBody AuthorizationRequest request) {
        if (!streamsHealthGuard.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Authorization service is starting up, please retry in a few seconds"));
        }

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
