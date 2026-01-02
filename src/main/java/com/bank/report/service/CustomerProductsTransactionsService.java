package com.bank.report.service;

import com.bank.report.client.AccountClient;
import com.bank.report.client.CreditClient;
import com.bank.report.client.DebitClient;
import com.bank.report.client.TransactionClient;
import com.bank.report.model.*;
import com.bank.report.model.dto.AccountResponse;
import com.bank.report.model.dto.CreditResponse;
import com.bank.report.model.dto.DebitResponse;
import com.bank.report.model.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProductsTransactionsService {

    private final AccountClient accountClient;
    private final CreditClient creditClient;
    private final DebitClient debitClient;
    private final TransactionClient transactionClient;

    /**
     * Obtiene todos los productos del cliente con sus transacciones completadas
     */
    public Mono<CustomerProductsTransactionsResponse> getCustomerProductsWithTransactions(String customerId) {
        log.info("Fetching products with transactions for customer: {}", customerId);

        // Obtener cuentas
        Mono<List<AccountResponse>> accountsMono = accountClient
                .getAccountsByCustomer(customerId)
                .collectList()
                .doOnSuccess(accounts -> log.debug("Found {} accounts", accounts.size()));

        // Obtener créditos
        Mono<List<CreditResponse>> creditsMono = creditClient
                .getCreditsByCustomer(customerId)
                .collectList()
                .doOnSuccess(credits -> log.debug("Found {} credits", credits.size()));

        // Obtener debit card - usar Optional en lugar de null
        Mono<DebitResponse> debitMono = debitClient
                .getDebitByCustomerId(customerId)
                .doOnSuccess(debit -> log.debug("Found debit card"))
                .onErrorResume(error -> {
                    log.warn("No debit card found for customer {}: {}", customerId, error.getMessage());
                    return Mono.empty(); // Retorna vacío, no null
                });

        // Obtener transacciones y filtrar solo COMPLETED
        Mono<List<TransactionResponse>> transactionsMono = transactionClient
                .getTransactionsByCustomer(customerId)
                .filter(tx -> "COMPLETED".equalsIgnoreCase(tx.getStatus().toString()))
                .collectList()
                .doOnSuccess(txs -> log.debug("Found {} completed transactions", txs.size()));

        // Combinar todo - usar zipDelayError para que no falle si debit es vacío
        return Mono.zip(
                        accountsMono,
                        creditsMono,
                        debitMono.map(d -> (DebitResponse) d).defaultIfEmpty(new DebitResponse()), // Objeto vacío en lugar de null
                        transactionsMono
                )
                .map(tuple -> {
                    List<AccountResponse> accounts = tuple.getT1();
                    List<CreditResponse> credits = tuple.getT2();
                    DebitResponse debit = tuple.getT3();
                    List<TransactionResponse> transactions = tuple.getT4();

                    // Agrupar transacciones por accountId y creditId
                    Map<String, List<TransactionResponse>> txByAccount = transactions.stream()
                            .filter(tx -> tx.getAccountId() != null)
                            .collect(Collectors.groupingBy(TransactionResponse::getAccountId));

                    Map<String, List<TransactionResponse>> txByCredit = transactions.stream()
                            .filter(tx -> tx.getCreditId() != null)
                            .collect(Collectors.groupingBy(TransactionResponse::getCreditId));

                    // Mapear accounts con transacciones
                    List<AccountWithTransactions> accountsWithTx = accounts.stream()
                            .map(account -> mapToAccountWithTransactions(
                                    account,
                                    txByAccount.getOrDefault(account.getId(), new ArrayList<>())
                            ))
                            .collect(Collectors.toList());

                    // Mapear credits con transacciones
                    List<CreditWithTransactions> creditsWithTx = credits.stream()
                            .map(credit -> mapToCreditWithTransactions(
                                    credit,
                                    txByCredit.getOrDefault(credit.getId(), new ArrayList<>())
                            ))
                            .collect(Collectors.toList());

                    // Mapear debit card - verificar si tiene ID (no es el objeto vacío)
                    List<DebitCardSummary> debitCards = (debit.getId() != null)
                            ? List.of(mapToDebitCardSummary(debit))
                            : new ArrayList<>();

                    // Construir summary
                    ProductsTransactionsSummary summary = buildSummary(
                            accountsWithTx,
                            creditsWithTx,
                            debitCards,
                            transactions
                    );

                    // Construir respuesta
                    CustomerProductsTransactionsResponse response = new CustomerProductsTransactionsResponse();
                    response.setCustomerId(customerId);
                    response.setHasProducts(summary.getTotalProducts() > 0);
                    response.setAccounts(accountsWithTx);
                    response.setCredits(creditsWithTx);
                    response.setDebitCards(debitCards);
                    response.setSummary(summary);
                    return response;
                })
                .doOnSuccess(response -> log.info("Products with transactions retrieved for customer: {}", customerId))
                .doOnError(error -> log.error("Error fetching products with transactions: {}", error.getMessage()));
    }

    private AccountWithTransactions mapToAccountWithTransactions(
            AccountResponse account,
            List<TransactionResponse> transactions
    ) {
        AccountWithTransactions awt = new AccountWithTransactions();
        awt.setId(account.getId());
        awt.setAccountNumber(account.getAccountNumber());
        awt.setAccountType(mapAccountType(account.getAccountType().toString()));
        awt.setBalance(account.getBalance() != null ? account.getBalance().doubleValue() : 0.0);
        awt.setMaintenanceFee(account.getMaintenanceFee() != null ? account.getMaintenanceFee().doubleValue() : null);
        awt.setActive(account.isActive());
        awt.setCreatedAt(account.getCreatedAt() != null ?
                account.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        awt.setTransactions(transactions.stream()
                .map(this::mapToTransactionSummary)
                .collect(Collectors.toList()));
        return awt;
    }

    private CreditWithTransactions mapToCreditWithTransactions(
            CreditResponse credit,
            List<TransactionResponse> transactions
    ) {
        CreditWithTransactions cwt = new CreditWithTransactions();
        cwt.setId(credit.getId());
        cwt.setCreditNumber(credit.getCreditNumber());
        cwt.setCreditType(mapCreditType(credit.getCreditType().toString()));
        cwt.setCreditLimit(credit.getCreditLimit() != null ? credit.getCreditLimit().doubleValue() : 0.0);
        cwt.setBalance(credit.getBalance() != null ? credit.getBalance().doubleValue() : null);
        cwt.setAvailableCredit(credit.getAvailableCredit() != null ? credit.getAvailableCredit().doubleValue() : 0.0);
        cwt.setInterestRate(credit.getInterestRate() != null ? credit.getInterestRate().doubleValue() : null);
        cwt.setActive(credit.getActive() != null ? credit.getActive() : false);
        cwt.setCreatedAt(credit.getCreatedAt() != null ?
                credit.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        cwt.setTransactions(transactions.stream()
                .map(this::mapToTransactionSummary)
                .collect(Collectors.toList()));
        return cwt;
    }

    private TransactionSummary mapToTransactionSummary(TransactionResponse tx) {
        TransactionSummary summary = new TransactionSummary();
        summary.setId(tx.getId());
        summary.setTransactionType(tx.getTransactionType().toString());
        summary.setAmount(tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0);
        summary.setStatus(tx.getStatus().toString());
        summary.setDescription(tx.getDescription());
        summary.setBalanceAfter(tx.getBalanceAfter() != null ? tx.getBalanceAfter().doubleValue() : null);
        summary.setCommission(tx.getCommission() != null ? tx.getCommission().doubleValue() : null);
        summary.setCreatedAt(tx.getCreatedAt() != null ?
                tx.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        summary.setPeriod(tx.getPeriod());
        return summary;
    }

    private DebitCardSummary mapToDebitCardSummary(DebitResponse debit) {
        DebitCardSummary summary = new DebitCardSummary();
        summary.setId(debit.getId());
        summary.setCardNumber(debit.getCardNumber());
        summary.setPrimaryAccountId(debit.getPrimaryAccountId());
        summary.setAssociatedAccountsCount(debit.getAssociatedAccounts() != null ?
                debit.getAssociatedAccounts().size() : 0);
        summary.setActive(debit.isActive());
        summary.setCreatedAt(debit.getCreatedAt() != null ?
                debit.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        return summary;
    }

    private ProductsTransactionsSummary buildSummary(
            List<AccountWithTransactions> accounts,
            List<CreditWithTransactions> credits,
            List<DebitCardSummary> debits,
            List<TransactionResponse> allTransactions
    ) {
        int totalAccountTx = accounts.stream()
                .mapToInt(a -> a.getTransactions().size())
                .sum();

        int totalCreditTx = credits.stream()
                .mapToInt(c -> c.getTransactions().size())
                .sum();

        ProductsTransactionsSummary summary = new ProductsTransactionsSummary();
        summary.setTotalAccounts(accounts.size());
        summary.setTotalCredits(credits.size());
        summary.setTotalDebitCards(debits.size());
        summary.setTotalProducts(accounts.size() + credits.size() + debits.size());
        summary.setTotalTransactions(allTransactions.size());
        summary.setTotalAccountTransactions(totalAccountTx);
        summary.setTotalCreditTransactions(totalCreditTx);
        return summary;
    }

    // Cambia el retorno a AccountWithTransactions.AccountTypeEnum
    private AccountWithTransactions.AccountTypeEnum mapAccountType(String accountType) {
        if (accountType == null) {
            return AccountWithTransactions.AccountTypeEnum.SAVING;
        }

        try {
            return AccountWithTransactions.AccountTypeEnum.fromValue(accountType);
        } catch (IllegalArgumentException e) {
            switch (accountType.toUpperCase()) {
                case "SAVING":
                case "SAVINGS":
                    return AccountWithTransactions.AccountTypeEnum.SAVING;
                case "CHECKING":
                case "CURRENT":
                    return AccountWithTransactions.AccountTypeEnum.CHECKING;
                case "FIXED_TERM":
                case "FIXED":
                    return AccountWithTransactions.AccountTypeEnum.FIXED_TERM;
                default:
                    log.warn("Unknown account type '{}', defaulting to SAVING", accountType);
                    return AccountWithTransactions.AccountTypeEnum.SAVING;
            }
        }
    }

    // Cambia el retorno a CreditWithTransactions.CreditTypeEnum
    private CreditWithTransactions.CreditTypeEnum mapCreditType(String creditType) {
        if (creditType == null) {
            return CreditWithTransactions.CreditTypeEnum.PERSONAL;
        }

        try {
            return CreditWithTransactions.CreditTypeEnum.fromValue(creditType);
        } catch (IllegalArgumentException e) {
            switch (creditType.toUpperCase()) {
                case "PERSONAL_LOAN":
                case "PERSONAL":
                    return CreditWithTransactions.CreditTypeEnum.PERSONAL;
                case "BUSINESS_LOAN":
                case "BUSINESS":
                    return CreditWithTransactions.CreditTypeEnum.BUSINESS;
                case "CREDIT_CARD":
                    return CreditWithTransactions.CreditTypeEnum.CREDIT_CARD;
                default:
                    log.warn("Unknown credit type '{}', defaulting to PERSONAL", creditType);
                    return CreditWithTransactions.CreditTypeEnum.PERSONAL;
            }
        }
    }

}
