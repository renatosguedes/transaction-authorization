package com.example.authorization.infrastructure.rest;

import com.example.authorization.domain.model.AuthorizationResult;
import com.example.authorization.domain.model.Money;
import com.example.authorization.domain.model.Transaction;
import com.example.authorization.domain.port.inbound.AuthorizeTransactionUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthorizationController.class)
@ExtendWith(MockitoExtension.class)
class AuthorizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthorizeTransactionUseCase useCase;

    @MockitoBean
    KafkaStreamsHealthGuard streamsHealthGuard;

    static final String ENDPOINT = "/api/v1/authorize";

    static final String VALID_BODY = """
            {
              "transactionId": "txn-001",
              "accountId": "acc-123",
              "merchantId": "merchant-1",
              "amount": 100.00,
              "currency": "BRL"
            }
            """;

    @Test
    void should_return_200_with_approved_result_when_streams_is_ready() throws Exception {
        when(streamsHealthGuard.isReady()).thenReturn(true);
        when(useCase.authorize(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            return AuthorizationResult.approved(t);
        });

        mockMvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-001"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void should_return_200_with_denied_result_when_rate_limit_exceeded() throws Exception {
        when(streamsHealthGuard.isReady()).thenReturn(true);
        when(useCase.authorize(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            return AuthorizationResult.denied(t, "Rate limit exceeded");
        });

        mockMvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.reason").value("Rate limit exceeded"));
    }

    @Test
    void should_return_503_when_streams_is_not_ready() throws Exception {
        when(streamsHealthGuard.isReady()).thenReturn(false);

        mockMvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void should_not_invoke_use_case_when_streams_is_not_ready() throws Exception {
        when(streamsHealthGuard.isReady()).thenReturn(false);

        mockMvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON).content(VALID_BODY));

        verify(useCase, org.mockito.Mockito.never()).authorize(any());
    }

    @Test
    void should_map_request_fields_to_transaction_correctly() throws Exception {
        when(streamsHealthGuard.isReady()).thenReturn(true);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        when(useCase.authorize(captor.capture())).thenAnswer(inv ->
                AuthorizationResult.approved(inv.getArgument(0)));

        mockMvc.perform(post(ENDPOINT).contentType(APPLICATION_JSON).content(VALID_BODY));

        Transaction tx = captor.getValue();
        assertThat(tx.transactionId()).isEqualTo("txn-001");
        assertThat(tx.accountId()).isEqualTo("acc-123");
        assertThat(tx.merchantId()).isEqualTo("merchant-1");
        assertThat(tx.amount().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(tx.amount().currency()).isEqualTo("BRL");
    }
}
