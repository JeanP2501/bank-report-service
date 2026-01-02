package com.bank.report.model.enums;

/**
 * Credit type enumeration
 * Defines the types of credit products (active products)
 */
public enum CreditType {
    /**
     * Personal loan - Only one per personal customer
     */
    PERSONAL_LOAN,

    /**
     * Business loan - Multiple allowed per business customer
     */
    BUSINESS_LOAN,

    /**
     * Credit card - Can be personal or business
     */
    CREDIT_CARD
}
