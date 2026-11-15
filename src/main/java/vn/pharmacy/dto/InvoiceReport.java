package vn.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** BUS assembles related data; the GUI/report renderer never joins persistence rows itself. */
public record InvoiceReport(
    String id,
    LocalDate date,
    String customerName,
    String customerAddress,
    String customerPhone,
    String employeeName,
    String prescriptionRef,
    List<InvoiceLine> lines,
    BigDecimal total) {
  public InvoiceReport {
    lines = List.copyOf(lines);
  }
}
