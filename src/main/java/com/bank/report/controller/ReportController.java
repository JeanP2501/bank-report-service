package com.bank.report.controller;

import com.bank.report.api.ReportsApi;
import com.bank.report.model.CommissionAvgResponse;
import com.bank.report.model.DailyAvgResponse;
import com.bank.report.service.ReportService;
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

}
