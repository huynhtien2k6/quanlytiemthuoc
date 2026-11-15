package vn.pharmacy;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.*;
import vn.pharmacy.dto.Schema;

public final class ServiceTest {
  private static int checks;

  @FunctionalInterface
  interface Action {
    void run() throws Exception;
  }

  static void check(boolean b, String msg) {
    checks++;
    if (!b) throw new AssertionError(msg);
  }

  static void rejects(Action a, String message) throws Exception {
    try {
      a.run();
    } catch (IllegalArgumentException | IOException e) {
      checks++;
      return;
    }
    throw new AssertionError("Should reject: " + message);
  }

  static void add(PharmacyService s, String t, String... r) throws Exception {
    s.save(t, null, List.of(r));
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("pharmacy-test-");
    try (var dao = new TextDatabase(dir)) {
      var s = new PharmacyService(dao);
      add(
          s,
          "employees",
          "NV1",
          "Nguyễn An",
          "HCM",
          "Nam",
          "1995-01-01",
          "0901234567",
          "2022-01-01",
          "an@example.com",
          "10000000");
      add(s, "customers", "KH1", "Lê Bình", "HCM", "Nữ", "2000-01-01", "0912345678");
      add(s, "suppliers", "NCC1", "Công ty A", "HCM", "0923456789");
      add(s, "medicines", "T1", "Panadol", "Hộp", "10000", "false", "Giảm đau", "Theo hướng dẫn");
      add(s, "lots", "L1", "T1", "2099-12-31", "10");
      add(s, "sales", "HD1", "2026-01-02", "KH1", "NV1", "");
      add(s, "sale_lines", "D1", "HD1", "L1", "3", "10000");
      check(PharmacyService.stock(s.snapshot(), "L1") == 7, "stock");
      check(s.revenue().get(0).equals(List.of("2026-01", "30000")), "revenue");
      check(s.bestSellers().get(0).get(2).equals("3"), "best seller");
      check(s.invoice("HD1").get(3).get(3).equals("30000"), "invoice total");
      check(s.rows("customers", "bình").size() == 1, "unicode search");
      var baseline = s.snapshot();
      byte[] bytes = Files.readAllBytes(dir.resolve("store.txt"));
      rejects(
          () -> add(s, "customers", "KH1", "X", "HCM", "Nam", "2000-01-01", "0901234567"),
          "duplicate PK");
      rejects(() -> s.delete("customers", "KH1"), "FK protection");
      rejects(() -> add(s, "lots", "BAD", "UNKNOWN", "2099-01-01", "1"), "missing FK");
      rejects(
          () -> s.save("sale_lines", "D1", List.of("D1", "HD1", "L1", "11", "10000")), "oversell");
      rejects(
          () -> s.save("sale_lines", "D1", List.of("D1", "HD1", "L1", "0", "10000")), "zero qty");
      rejects(
          () -> s.save("sale_lines", "D1", List.of("D1", "HD1", "L1", "1", "-1")),
          "negative price");
      rejects(
          () -> s.save("sale_lines", "D1", List.of("D1", "HD1", "L1", "1", "1.234")),
          "money precision");
      rejects(
          () -> s.save("sales", "HD1", List.of("HD1", "2100-01-01", "KH1", "NV1", "")), "expiry");
      rejects(() -> add(s, "sale_lines", "D2", "HD1", "L1", "1", "10000"), "duplicate lot");
      check(s.snapshot().equals(baseline), "rollback memory");
      check(Arrays.equals(bytes, Files.readAllBytes(dir.resolve("store.txt"))), "rollback disk");
      s.exportExcel(dir.resolve("roundtrip.xlsx"));
      check(ExcelFile.read(dir.resolve("roundtrip.xlsx")).equals(baseline), "xlsx roundtrip");
      s.importExcel(dir.resolve("roundtrip.xlsx"));
      check(s.snapshot().equals(baseline), "import commit");
      var broken = s.snapshot();
      broken.get("employees").clear();
      rejects(() -> s.replace(broken), "invalid batch import");
      check(s.snapshot().equals(baseline), "atomic batch rejection");
      add(s, "medicines", "T2", "Kháng sinh", "Hộp", "20000", "true", "Amoxicillin", "Theo đơn");
      add(s, "lots", "L2", "T2", "2099-01-01", "5");
      rejects(() -> add(s, "sale_lines", "RX", "HD1", "L2", "1", "20000"), "prescription required");
      s.save("sales", "HD1", List.of("HD1", "2026-01-02", "KH1", "NV1", "RX001"));
      add(s, "sale_lines", "RX", "HD1", "L2", "1", "20000");
      check(PharmacyService.stock(s.snapshot(), "L2") == 4, "prescription success");
      add(s, "purchases", "PN1", "2026-02-01", "NCC1", "NV1");
      add(s, "purchase_lines", "N1", "PN1", "L1", "20", "5000");
      add(s, "sales", "HD2", "2026-02-02", "KH1", "NV1", "");
      add(s, "sale_lines", "D3", "HD2", "L1", "12", "10000");
      rejects(
          () -> s.save("sales", "HD2", List.of("HD2", "2026-01-01", "KH1", "NV1", "")),
          "backdated negative stock");
      rejects(() -> s.delete("purchase_lines", "N1"), "cannot remove consumed purchase");
      s.delete("sale_lines", "D3");
      s.delete("sales", "HD2");
      s.delete("purchase_lines", "N1");
      s.delete("purchases", "PN1");
      check(PharmacyService.stock(s.snapshot(), "L1") == 7, "CRUD reverses stock");

      var beforeCart = s.snapshot();
      rejects(
          () ->
              s.checkout(
                  List.of("CART", "2026-03-01", "KH1", "NV1", ""),
                  List.of(List.of("L1", "1", "10000"), List.of("L2", "999", "10000"))),
          "multi-line checkout rollback");
      check(s.snapshot().equals(beforeCart), "checkout leaves no orphan header");
      s.checkout(
          List.of("CART", "2026-03-01", "KH1", "NV1", "RX2"),
          List.of(List.of("L1", "1", "10000"), List.of("L2", "1", "20000")));
      check(s.rows("sale_lines", "CART").size() == 2, "checkout all lines");
      rejects(
          () -> s.checkout(List.of("EMPTY", "2026-03-01", "KH1", "NV1", ""), List.of()),
          "empty checkout");
      check(s.availableLots().get(0).get(0).equals("L2"), "FEFO expiry ordering");
      check(
          s.sellingPriceForLot("L1").compareTo(new BigDecimal("10000")) == 0,
          "suggested sale price resolved in BUS");
      // Date ranges include both endpoints; drafts do not inflate sales KPIs.
      var jan2 = LocalDate.of(2026, 1, 2);
      check(
          s.revenue(jan2, jan2).get(0).equals(List.of("2026-01", "50000")),
          "inclusive revenue date");
      check(s.revenue(jan2.plusDays(1), jan2.plusDays(2)).isEmpty(), "empty revenue range");
      check(
          s.bestSellers(jan2, jan2).stream().mapToLong(r -> Long.parseLong(r.get(2))).sum() == 4,
          "period best sellers");
      add(s, "sales", "DRAFT", "2026-01-02", "KH1", "NV1", "");
      var summary = s.summary(jan2, jan2);
      check(
          summary.invoiceCount() == 1
              && summary.quantity() == 4
              && summary.revenue().compareTo(new BigDecimal("50000")) == 0,
          "period summary excludes draft");
      rejects(() -> s.revenue(jan2.plusDays(1), jan2), "reversed date range");
      rejects(() -> s.bestSellers(null, jan2), "missing report range");
      rejects(() -> s.summary(jan2.plusDays(1), jan2), "invalid KPI range");
      var report = s.invoiceReport("HD1");
      check(
          report.id().equals("HD1")
              && report.date().equals(jan2)
              && report.customerPhone().equals("0912345678")
              && report.lines().size() == 2,
          "typed report relations");
      check(
          report.total().compareTo(new BigDecimal("50000")) == 0
              && report.lines().get(0).amount().compareTo(new BigDecimal("30000")) == 0,
          "typed invoice historical prices");
      try {
        report.lines().clear();
        throw new AssertionError("report lines mutable");
      } catch (UnsupportedOperationException expected) {
        checks++;
      }
      check(
          s.lookups("lots").get(0).label().contains("Panadol")
              && s.lookups("lots").get(0).label().contains("Tồn"),
          "readable lookup labels");
      var missingTable = s.snapshot();
      missingTable.remove("suppliers");
      rejects(() -> s.replace(missingTable), "missing table is not silently invented");
      var extraTable = s.snapshot();
      extraTable.put("injected", new ArrayList<>());
      rejects(() -> s.replace(extraTable), "extra snapshot table");
      var nullRows = s.snapshot();
      nullRows.put("suppliers", null);
      rejects(() -> s.replace(nullRows), "null snapshot rows");
      rejects(() -> s.save("customers", null, List.of()), "malformed CRUD row");
      rejects(() -> s.delete("customers", "MISSING"), "delete missing row");
      rejects(
          () -> s.checkout(List.of(), List.of(List.of("L1", "1", "10000"))),
          "malformed cart header");
      rejects(
          () -> s.checkout(List.of("BADCART", "2026-01-02", "KH1", "NV1", ""), null), "null cart");
      rejects(
          () -> add(s, "sales", "FUTURE", LocalDate.now().plusDays(1).toString(), "KH1", "NV1", ""),
          "future sale would change current inventory");
      rejects(
          () ->
              add(s, "purchases", "FUTURE", LocalDate.now().plusDays(1).toString(), "NCC1", "NV1"),
          "future purchase would change current inventory");
      rejects(() -> add(s, "sales", "EXPANDED", "+02026-01-02", "KH1", "NV1", ""), "expanded year");
      rejects(
          () ->
              s.save(
                  "employees",
                  "NV1",
                  List.of(
                      "NV1",
                      "Nguyễn An",
                      "HCM",
                      "Nam",
                      "1995-01-01",
                      "0901234567",
                      "1990-01-01",
                      "an@example.com",
                      "10000000")),
          "employment before birth");
      rejects(
          () -> s.save("lots", "L1", List.of("L1", "T2", "2099-12-31", "10")),
          "relabeling already transacted lot");
      add(s, "lots", "EXPIRED", "T1", "2026-01-01", "0");
      add(s, "purchases", "PN_EXPIRED", "2026-01-02", "NCC1", "NV1");
      rejects(
          () -> add(s, "purchase_lines", "EXPIRED_LINE", "PN_EXPIRED", "EXPIRED", "1", "1"),
          "receiving expired stock");
      rejects(
          () -> add(s, "sale_lines", "EXPIRED_SALE", "HD1", "EXPIRED", "1", "1"),
          "selling stock expired on invoice date");
      rejects(
          () ->
              s.checkout(
                  Arrays.asList(null, "2026-01-02", "KH1", "NV1", ""),
                  List.of(List.of("L1", "1", "1"))),
          "null cart header cells");
      rejects(
          () ->
              add(
                  s,
                  "customers",
                  "BADXML",
                  "Tên\u0001không hợp lệ",
                  "HCM",
                  "Nam",
                  "2000-01-01",
                  "0912345678"),
          "invalid XML character cannot break Excel export");
      String originalTitle = Schema.table("sales").columns()[0];
      Schema.table("sales").columns()[0] = "INJECTED";
      check(Schema.table("sales").columns()[0].equals(originalTitle), "immutable schema columns");

      s.save(
          "customers",
          "KH1",
          List.of(
              "KH1", "Tên mới; có tab\t và xuống\ndòng", "HCM", "Nữ", "2000-01-01", "0912345678"));
      check(dao.read().equals(s.snapshot()), "UTF8 control chars roundtrip");
      try {
        new TextDatabase(dir);
        throw new AssertionError("lock missing");
      } catch (IOException expected) {
        checks++;
      }
      check(new PharmacyService(dao).snapshot().equals(s.snapshot()), "restart persistence");
    }
    try (var reopened = new TextDatabase(dir)) {
      check(!reopened.read().get("sales").isEmpty(), "unlock and reopen");
    }
    System.out.println("PASS: " + checks + " business/persistence/Excel checks");
  }
}
