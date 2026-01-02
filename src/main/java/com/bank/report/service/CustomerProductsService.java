package com.bank.report.service;

import com.bank.report.client.AccountClient;
import com.bank.report.client.CreditClient;
import com.bank.report.client.DebitClient;
import com.bank.report.model.*;
import com.bank.report.model.dto.AccountResponse;
import com.bank.report.model.dto.CreditResponse;
import com.bank.report.model.dto.DebitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Servicio para consolidar información de productos del cliente
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProductsService {

    private final AccountClient accountServiceClient;
    private final CreditClient creditServiceClient;
    private final DebitClient debitServiceClient;

    /**
     * Obtiene todos los productos de un cliente consolidados
     */
    public Mono<CustomerProductsResponse> getCustomerProducts(String customerId) {
        log.info("Fetching all products for customer: {}", customerId);

        // Obtener cuentas usando /api/accounts/customer/{customerId}
        Mono<List<AccountSummary>> accountsMono = accountServiceClient
                .getAccountsByCustomer(customerId)
                .map(this::mapToAccountSummary)
                .collectList()
                .doOnSuccess(accounts -> log.debug("Found {} accounts for customer {}", accounts.size(), customerId));

        // Obtener créditos usando /api/credits/customer/{customerId}
        Mono<List<CreditSummary>> creditsMono = creditServiceClient
                .getCreditsByCustomer(customerId)
                .map(this::mapToCreditSummary)
                .collectList()
                .doOnSuccess(credits -> log.debug("Found {} credits for customer {}", credits.size(), customerId));

        // Obtener tarjeta de débito usando /api/debit-cards/{debitId}
        // Usando el customerId como debitId (ajustar según tu lógica)
        Mono<List<DebitCardSummary>> debitsMono = debitServiceClient
                .getDebitByCustomerId(customerId)
                .map(this::mapToDebitCardSummary)
                .map(List::of) // Convertir a lista de 1 elemento
                .onErrorResume(error -> {
                    log.warn("No debit card found for customer {}: {}", customerId, error.getMessage());
                    return Mono.just(List.of()); // Retornar lista vacía si no existe
                })
                .doOnSuccess(debits -> log.debug("Found {} debit cards for customer {}", debits.size(), customerId));

        // Combinar todos los resultados
        return Mono.zip(accountsMono, creditsMono, debitsMono)
                .map(tuple -> {
                    List<AccountSummary> accounts = tuple.getT1();
                    List<CreditSummary> credits = tuple.getT2();
                    List<DebitCardSummary> debits = tuple.getT3();

                    ProductsSummary summary = buildProductsSummary(accounts, credits, debits);
                    boolean hasProducts = summary.getTotalProducts() > 0;

                    CustomerProductsResponse response = new CustomerProductsResponse();
                    response.setCustomerId(customerId);
                    response.setHasProducts(hasProducts);
                    response.setAccounts(accounts);
                    response.setCredits(credits);
                    response.setDebitCards(debits);
                    response.setSummary(summary);
                    return response;
                })
                .doOnSuccess(response -> log.info("Customer products retrieved successfully for customer: {}", customerId))
                .doOnError(error -> log.error("Error fetching products for customer {}: {}", customerId, error.getMessage()));
    }

    /**
     * Mapea AccountResponse a AccountSummary
     */
    private AccountSummary mapToAccountSummary(AccountResponse account) {
        AccountSummary summary = new AccountSummary();
        summary.setId(account.getId());
        summary.setAccountNumber(account.getAccountNumber());
        summary.setAccountType(AccountSummary.AccountTypeEnum.fromValue(account.getAccountType().toString()));
        summary.setBalance(account.getBalance() != null ? account.getBalance().doubleValue() : 0.0);
        summary.setMaintenanceFee(account.getMaintenanceFee() != null ? account.getMaintenanceFee().doubleValue() : null);
        summary.setActive(account.isActive());
        summary.setCreatedAt(account.getCreatedAt() != null ?
                account.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        return summary;
    }

    /**
     * Mapea CreditResponse a CreditSummary
     */
    private CreditSummary mapToCreditSummary(CreditResponse credit) {
        CreditSummary summary = new CreditSummary();
        summary.setId(credit.getId());
        summary.setCreditNumber(credit.getCreditNumber());
        summary.setCreditType(CreditSummary.CreditTypeEnum.fromValue(credit.getCreditType().toString()));
        summary.setCreditLimit(credit.getCreditLimit() != null ? credit.getCreditLimit().doubleValue() : 0.0);
        summary.setBalance(credit.getBalance() != null ? credit.getBalance().doubleValue() : null);
        summary.setAvailableCredit(credit.getAvailableCredit() != null ? credit.getAvailableCredit().doubleValue() : 0.0);
        summary.setInterestRate(credit.getInterestRate() != null ? credit.getInterestRate().doubleValue() : null);
        summary.setActive(credit.getActive() != null ? credit.getActive() : false);
        summary.setCreatedAt(credit.getCreatedAt() != null ?
                credit.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        return summary;
    }

    /**
     * Mapea DebitResponse a DebitCardSummary
     */
    private DebitCardSummary mapToDebitCardSummary(DebitResponse debit) {
        // Enmascarar el número de tarjeta (mostrar solo los últimos 4 dígitos)
        String maskedCardNumber = maskCardNumber(debit.getCardNumber());

        DebitCardSummary summary = new DebitCardSummary();
        summary.setId(debit.getId());
        summary.setCardNumber(maskedCardNumber);
        summary.setPrimaryAccountId(debit.getPrimaryAccountId());
        summary.setAssociatedAccountsCount(debit.getAssociatedAccounts() != null ?
                debit.getAssociatedAccounts().size() : 0);
        summary.setActive(debit.isActive());
        summary.setCreatedAt(debit.getCreatedAt() != null ?
                debit.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
        return summary;
    }

    /**
     * Construye el resumen de productos
     */
    private ProductsSummary buildProductsSummary(
            List<AccountSummary> accounts,
            List<CreditSummary> credits,
            List<DebitCardSummary> debits
    ) {
        int totalAccounts = accounts.size();
        int totalCredits = credits.size();
        int totalDebitCards = debits.size();
        int totalProducts = totalAccounts + totalCredits + totalDebitCards;

        ProductsSummary summary = new ProductsSummary();
        summary.setTotalAccounts(totalAccounts);
        summary.setTotalCredits(totalCredits);
        summary.setTotalDebitCards(totalDebitCards);
        summary.setTotalProducts(totalProducts);
        return summary;
    }

    /**
     * Enmascara el número de tarjeta mostrando solo los últimos 4 dígitos
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }

        // Si ya está enmascarado, retornar tal cual
        if (cardNumber.startsWith("*")) {
            return cardNumber;
        }

        // Enmascarar dejando solo los últimos 4 dígitos
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        return "****" + lastFour;
    }

}
