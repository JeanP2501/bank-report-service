package com.bank.report.client;

import com.bank.report.model.dto.TransactionResponse;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.security.auth.login.AccountNotFoundException;
import java.time.Duration;

/**
 * Client for communicating with Transaction Service
 */
@Slf4j
@Component
public class TransactionClient {

    private final WebClient webClient;

    public TransactionClient(@LoadBalanced WebClient.Builder webClientBuilder,
                             @Value("${transaction.service.url}") String transactionServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(transactionServiceUrl)
                .build();
    }

    public Flux<TransactionResponse> findByCustomerId(String customerId) {
        log.info("Calling transaction service for customer: {}", customerId);

        return webClient
                .get()
                .uri("/api/transactions/customer/{customerId}", customerId)
                .retrieve()
                .bodyToFlux(TransactionResponse.class)
                .doOnError(error -> log.error("Error fetching transactions for customer {}: {}",
                        customerId, error.getMessage()))
                .doOnComplete(() -> log.info("Completed fetching transactions for customer {}", customerId));
    }

    /**
     * Obtiene todas las transacciones de un cliente
     * GET /api/transactions/customer/{customerId}
     */
    public Flux<TransactionResponse> getTransactionsByCustomer(String customerId) {
        log.debug("Calling Transaction Service: GET /api/transactions/customer/{}", customerId);

        return webClient
                .get()
                .uri("/api/transactions/customer/{customerId}", customerId)
                .retrieve()
                .bodyToFlux(TransactionResponse.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(transaction -> log.debug("Transaction found: {}", transaction.getId()))
                .doOnError(ex -> {
                    log.error("Error calling Transaction Service for customer {}: {}", customerId, ex.getMessage());
                })
                .onErrorResume(error -> {
                    log.warn("Returning empty list due to error: {}", error.getMessage());
                    return Flux.empty();
                });
    }

}
