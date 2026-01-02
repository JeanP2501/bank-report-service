package com.bank.report.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitResponse {

  private String id;

  private String customerId;

  private String primaryAccountId;

  private List<String> associatedAccounts;

  private String cardNumber;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private boolean active;
}
