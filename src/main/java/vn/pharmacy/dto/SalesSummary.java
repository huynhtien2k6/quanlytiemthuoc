package vn.pharmacy.dto;

import java.math.BigDecimal;

/** Period totals; draft invoices with no lines are excluded from invoiceCount. */
public record SalesSummary(BigDecimal revenue, long invoiceCount, long quantity) {}
