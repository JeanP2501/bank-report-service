package com.bank.report.client;

import com.bank.report.exception.CreditNotFoundException;
import com.bank.report.exception.ServiceUnavailableException;
import com.bank.report.model.dto.CreditResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Client for communicating with Credit Service */
@Slf4j
@Component
public class CreditClient {

  private final WebClient webClient;

  public CreditClient(
      WebClient.Builder webClientBuilder, @Value("${credit.service.url}") String creditServiceUrl) {
    this.webClient = webClientBuilder.baseUrl(creditServiceUrl).build();
  }

  /**
   * Get credit by ID from Credit Service
   *
   * @param creditId the credit id
   * @return Mono of CreditResponse
   */
  @CircuitBreaker(name = "creditService", fallbackMethod = "getCreditFallback")
  @Retry(name = "creditService")
  @TimeLimiter(name = "creditService")
  public Mono<CreditResponse> getCredit(String creditId) {
    log.debug("Calling Credit Service to get credit with id: {}", creditId);

    return webClient
        .get()
        .uri("/api/credits/{id}", creditId)
        .retrieve()
        .onStatus(
            status -> status.value() == 404,
            response -> Mono.error(new CreditNotFoundException(creditId)))
        .bodyToMono(CreditResponse.class)
        .timeout(Duration.ofSeconds(2))
        .doOnSuccess(credit -> log.debug("Credit found: {}", credit.getId()))
        .doOnError(
            ex -> {
              log.error(
                  "Error calling Credit Service for credit {}: {}", creditId, ex.getMessage());
            });
  }

  /** Fallback for getCredit */
  private Mono<CreditResponse> getCreditFallback(String creditId, Exception ex) {
    log.warn(
        "Circuit breaker activated for getCredit. CreditId: {}. Reason: {}",
        creditId,
        ex.getClass().getSimpleName());

    // Si es CreditNotFoundException, propagarla
    if (ex instanceof CreditNotFoundException) {
      return Mono.error(ex);
    }

    return Mono.error(
        new ServiceUnavailableException(
            "Credit service is currently unavailable. Please try again later."));
  }

    /**
     * Obtiene todos los créditos de un cliente usando el endpoint correcto
     * GET /api/credits/customer/{customerId}
     */
    public Flux<CreditResponse> getCreditsByCustomer(String customerId) {
        log.debug("Calling Credit Service: GET /api/credits/customer/{}", customerId);

        return webClient
                .get()
                .uri("/api/credits/customer/{customerId}", customerId)
                .retrieve()
                .bodyToFlux(CreditResponse.class)
                .timeout(Duration.ofSeconds(3))
                .doOnNext(credit -> log.debug("Credit found: {}", credit.getId()))
                .doOnError(ex -> {
                    log.error("Error calling Credit Service for customer {}: {}", customerId, ex.getMessage());
                })
                .onErrorResume(error -> {
                    log.warn("Returning empty list due to error: {}", error.getMessage());
                    return Flux.empty();
                });
    }

}
