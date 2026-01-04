package com.bank.report.service;

import com.bank.report.client.AccountClient;
import com.bank.report.client.DebitClient;
import com.bank.report.model.DebitPrimaryAccountBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebitPrimaryAccountBalanceService {

    private final DebitClient debitClient;
    private final AccountClient accountClient;

    /**
     * Obtiene el balance de la cuenta principal asociada a una tarjeta de débito
     */
    public Mono<DebitPrimaryAccountBalanceResponse> getPrimaryAccountBalance(String debitId) {
        log.info("Fetching primary account balance for debit card: {}", debitId);

        return debitClient.getDebitById(debitId)
                .flatMap(debit -> {
                    String primaryAccountId = debit.getPrimaryAccountId();
                    log.debug("Primary account ID for debit {}: {}", debitId, primaryAccountId);

                    return accountClient.getAccount(primaryAccountId)
                            .map(account -> {
                                DebitPrimaryAccountBalanceResponse response = new DebitPrimaryAccountBalanceResponse();
                                response.setDebitId(debit.getId());
                                response.setAccountId(account.getId());
                                response.setAccountNumber(account.getAccountNumber());
                                response.setAccountType(account.getAccountType() != null ?
                                        account.getAccountType().toString() : null);
                                response.setBalance(account.getBalance() != null ?
                                        account.getBalance().doubleValue() : 0.0);
                                response.setCardNumber(debit.getCardNumber());
                                response.setActive(account.isActive());
                                return response;
                            });
                })
                .doOnSuccess(response -> log.info("Primary account balance retrieved - accountId: {}, balance: {}",
                        response.getAccountId(), response.getBalance()))
                .doOnError(error -> log.error("Error fetching primary account balance for debit {}: {}",
                        debitId, error.getMessage()));
    }

}
