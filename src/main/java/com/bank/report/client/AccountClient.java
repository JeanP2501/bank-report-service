package com.bank.report.client;



import com.bank.report.exception.ServiceUnavailableException;
import com.bank.report.model.dto.AccountResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.time.Duration;
import javax.security.auth.login.AccountNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client for communicating with Account Service
 */
@Slf4j
@Component
public class AccountClient {

    private final WebClient webClient;

    public AccountClient(WebClient.Builder webClientBuilder,
                         @Value("${account.service.url}") String accountServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(accountServiceUrl)
                .build();
    }

    /**
     * Get account by ID from Account Service
     * @param accountId the account id
     * @return Mono of AccountResponse
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountFallback")
    @Retry(name = "accountService")
    @TimeLimiter(name = "accountService")
    public Mono<AccountResponse> getAccount(String accountId) {
        log.debug("Calling Account Service to get account with id: {}", accountId);

        return webClient.get()
                .uri("/api/accounts/{id}", accountId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(new AccountNotFoundException(accountId)))
                .bodyToMono(AccountResponse.class)
                .timeout(Duration.ofSeconds(2))
                .doOnSuccess(account -> log.debug("Account found: {}", account.getId()))
                .doOnError(WebClientResponseException.class, ex -> {
                    log.error("Error calling Account Service: {} - {}", ex.getStatusCode(), ex.getMessage());
                });
    }

    /**
     * Fallback method when circuit is open or service fails
     */
    private Mono<AccountResponse> getAccountFallback(String accountId, Exception ex) {
        log.warn("Circuit breaker activated for getAccount. AccountId: {}. Reason: {}",
                accountId, ex.getClass().getSimpleName());

        // Si es AccountNotFoundException, propagarla (no es fallo del servicio)
        if (ex instanceof AccountNotFoundException) {
            return Mono.error(ex);
        }

    // Para otros errores, retornar error de servicio no disponible
    return Mono.error(
        new ServiceUnavailableException(
            "Account service is currently unavailable. Please try again later."));
    }

    /**
     * Obtiene todas las cuentas de un cliente usando el endpoint correcto
     * GET /api/accounts/customer/{customerId}
     */
    public Flux<AccountResponse> getAccountsByCustomer(String customerId) {
        log.debug("Calling Account Service: GET /api/accounts/customer/{}", customerId);

        return webClient.get()
                .uri("/api/accounts/customer/{customerId}", customerId)
                .retrieve()
                .bodyToFlux(AccountResponse.class)
                .timeout(Duration.ofSeconds(3))
                .doOnNext(account -> log.debug("Account found: {}", account.getId()))
                .doOnError(ex -> {
                    log.error("Error calling Account Service for customer {}: {}", customerId, ex.getMessage());
                })
                .onErrorResume(error -> {
                    log.warn("Returning empty list due to error: {}", error.getMessage());
                    return Flux.empty();
                });
    }

}
