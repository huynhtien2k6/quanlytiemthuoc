package vn.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Typed, read-only sale-line data prepared by BUS for a receipt/report. */
public record InvoiceLine(
    String medicineId,
    String medicineName,
    String unit,
    String lotId,
    LocalDate expiryDate,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal amount) {}
