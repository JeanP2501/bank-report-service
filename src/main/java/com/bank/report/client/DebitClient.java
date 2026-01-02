package com.bank.report.client;

import com.bank.report.exception.CreditNotFoundException;
import com.bank.report.model.dto.CreditResponse;
import com.bank.report.model.dto.DebitResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Client for communicating with Debit Service */
@Slf4j
@Component
public class DebitClient {

    private final WebClient webClient;

    public DebitClient(
            WebClient.Builder webClientBuilder, @Value("${debit.service.url}") String creditServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(creditServiceUrl).build();
    }

    public Mono<DebitResponse> getDebitByCustomerId(String customerId) {
        log.debug("Calling Debit Service to get debit card with id: {}", customerId);

        return webClient
                .get()
                .uri("/api/debit-cards/customer/{customerId}", customerId)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        response -> Mono.error(new CreditNotFoundException(customerId)))
                .bodyToMono(DebitResponse.class)
                .timeout(Duration.ofSeconds(2))
                .doOnSuccess(credit -> log.debug("Debit found: {}", credit.getId()))
                .doOnError(
                        ex -> {
                            log.error(
                                    "Error calling Debit Service for DebitId {}: {}", customerId, ex.getMessage());
                        });
    }


}
