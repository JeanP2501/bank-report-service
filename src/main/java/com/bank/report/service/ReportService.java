package com.bank.report.service;

import com.bank.report.client.TransactionClient;
import com.bank.report.model.CommissionAvgResponse;
import com.bank.report.model.DailyAvgResponse;
import com.bank.report.model.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionClient transactionClient;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    public Mono<DailyAvgResponse> calculateDailyAverage(String customerId, String period) {
        log.info("Calculating daily average for customer: {} in period: {}", customerId, period);

        return transactionClient.findByCustomerId(customerId)
                .filter(transaction -> matchesPeriod(transaction, period))
                .collectList()
                .flatMap(transactions -> calculateBalanceAverage(transactions))
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.info("Daily average calculated successfully: {}", result);
                    }
                })
                .doOnError(error -> log.error("Error calculating daily average: {}", error.getMessage()));
    }

    public Mono<CommissionAvgResponse> calculateAverageCommissions(String customerId, String period) {
        log.info("Calculating average commissions for customer: {} in period: {}", customerId, period);

        return transactionClient.findByCustomerId(customerId)
                .filter(transaction -> matchesPeriod(transaction, period))
                .filter(transaction -> hasCommission(transaction))
                .collectList()
                .flatMap(this::calculateCommissionAverage)
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.info("Average commission calculated successfully: {}", result);
                    }
                })
                .doOnError(error -> log.error("Error calculating average commission: {}", error.getMessage()));
    }

    private boolean hasCommission(TransactionResponse transaction) {
        return transaction.getCommission() != null &&
                transaction.getCommission().compareTo(BigDecimal.ZERO) > 0;
    }

    private Mono<CommissionAvgResponse> calculateCommissionAverage(List<TransactionResponse> transactions) {
        if (transactions.isEmpty()) {
            log.warn("No transactions with commissions found for the given period");
            return Mono.empty();
        }

        // Sumar todas las comisiones
        BigDecimal total = transactions.stream()
                .map(TransactionResponse::getCommission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcular promedio
        BigDecimal count = BigDecimal.valueOf(transactions.size());
        BigDecimal average = total.divide(count, 2, RoundingMode.HALF_UP);

        // Obtener el accountId de la primera transacción
        String accountId = transactions.get(0).getAccountId();

        // Crear response
        CommissionAvgResponse response = new CommissionAvgResponse();
        response.setAccountId(accountId);
        response.setAvgCommissions(average.doubleValue());

        return Mono.just(response);
    }

    private boolean matchesPeriod(TransactionResponse transaction, String period) {
        if (transaction.getCreatedAt() == null) {
            return false;
        }
        String transactionPeriod = transaction.getCreatedAt()
                .format(PERIOD_FORMATTER);
        return period.equals(transactionPeriod);
    }

    private Mono<DailyAvgResponse> calculateBalanceAverage(List<TransactionResponse> transactions) {
        if (transactions.isEmpty()) {
            log.warn("No transactions found for the given period");
            return Mono.empty();
        }

        // Sumar todos los balanceAfter
        BigDecimal total = transactions.stream()
                .map(TransactionResponse::getBalanceAfter)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcular promedio
        BigDecimal count = BigDecimal.valueOf(transactions.size());
        BigDecimal average = total.divide(count, 2, RoundingMode.HALF_UP);

        // Obtener el accountId de la primera transacción
        String accountId = transactions.get(0).getAccountId();

        // Crear response (con o sin builder dependiendo de tu configuración)
        DailyAvgResponse response = new DailyAvgResponse();
        response.setAccountId(accountId);
        response.setAvgDaily(average.toBigInteger().doubleValue());

        return Mono.just(response);
    }

}
