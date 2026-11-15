package vn.pharmacy;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.*;

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
