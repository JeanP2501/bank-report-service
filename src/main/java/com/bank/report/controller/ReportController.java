package com.bank.report.controller;

import com.bank.report.api.ReportsApi;
import com.bank.report.model.*;
import com.bank.report.service.CustomerProductsService;
import com.bank.report.service.CustomerProductsTransactionsService;
import com.bank.report.service.DebitPrimaryAccountBalanceService;
import com.bank.report.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;
    private final CustomerProductsService customerProductsService;
    private final CustomerProductsTransactionsService customerProductsTransactionsService;
    private final DebitPrimaryAccountBalanceService debitPrimaryAccountBalanceService;

    @Override
    public Mono<ResponseEntity<CommissionAvgResponse>> getAverageCommissions(
            String customerId,
            String period,
            ServerWebExchange exchange) {
        return reportService.calculateAverageCommissions(customerId, period)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<DailyAvgResponse>> getDailyAverage(
            String customerId,
            String period,
            ServerWebExchange exchange) {

        log.info("GET /api/reports/daily-avg - customerId: {}, period: {}", customerId, period);

        return reportService.calculateDailyAverage(customerId, period)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<CustomerProductsResponse>> getCustomerProducts(
            String customerId,
            ServerWebExchange exchange
    ) {
        log.info("Received request for customer products - customerId: {}", customerId);

        return customerProductsService.getCustomerProducts(customerId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Customer products retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving customer products", error));
    }

    @Override
    public Mono<ResponseEntity<CustomerProductsTransactionsResponse>> getCustomerProductsWithTransactions(
            String customerId,
            ServerWebExchange exchange
    ) {
        log.info("Received request for customer products with transactions - customerId: {}", customerId);

        return customerProductsTransactionsService.getCustomerProductsWithTransactions(customerId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Customer products with transactions retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving customer products with transactions", error));
    }

    @Override
    public Mono<ResponseEntity<DebitPrimaryAccountBalanceResponse>> getDebitPrimaryAccountBalance(
            String debitId,
            ServerWebExchange exchange
    ) {
        log.info("Received request for debit primary account balance - debitId: {}", debitId);

        return debitPrimaryAccountBalanceService.getPrimaryAccountBalance(debitId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("Debit primary account balance retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving debit primary account balance", error));
    }

}
