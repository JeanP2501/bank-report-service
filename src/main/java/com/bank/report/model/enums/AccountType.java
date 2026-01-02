package com.bank.report.model.enums;

/**
 * Account type enumeration
 * Defines the types of bank accounts (passive products)
 */
public enum AccountType {
    /**
     * Savings account - No maintenance fee, limited monthly transactions
     */
    SAVING,

    /**
     * Checking account - Has maintenance fee, unlimited transactions
     */
    CHECKING,

    /**
     * Fixed term account - No maintenance fee, one transaction per month on specific day
     */
    FIXED_TERM
}
