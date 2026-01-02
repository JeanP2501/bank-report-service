package com.bank.report.model.dto;

import com.bank.report.model.enums.CreditType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for credit response */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditResponse {

  private String id;
  private String creditNumber;
  private CreditType creditType;
  private String customerId;
  private BigDecimal creditAmount;
  private BigDecimal balance;
  private BigDecimal creditLimit;
  private BigDecimal availableCredit;
  private BigDecimal interestRate;
  private BigDecimal minimumPayment;
  private Integer paymentDueDay;
  private Boolean active;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
