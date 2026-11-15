package vn.pharmacy.bus;

import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import vn.pharmacy.dao.TextDatabase;
import vn.pharmacy.dto.Schema;

public final class PharmacyService {
  private final TextDatabase dao;
  private Map<String, List<List<String>>> db;

  public PharmacyService(TextDatabase dao) throws IOException {
    this.dao = dao;
    db = dao.read();
    validate(db);
  }

  public synchronized Map<String, List<List<String>>> snapshot() {
    return copy(db);
  }

  private static Map<String, List<List<String>>> copy(Map<String, List<List<String>>> src) {
    var m = Schema.empty();
    src.forEach((k, v) -> v.forEach(r -> m.get(k).add(new ArrayList<>(r))));
    return m;
  }

  public synchronized List<List<String>> rows(String key, String query) {
    String q = query.toLowerCase(Locale.ROOT);
    return db.get(key).stream()
        .filter(r -> String.join(" ", r).toLowerCase(Locale.ROOT).contains(q))
        .map(r -> (List<String>) new ArrayList<>(r))
        .toList();
  }

  public synchronized void save(String key, String oldId, List<String> row) throws IOException {
    var m = copy(db);
    if (oldId != null) {
      if (!oldId.equals(row.get(0)))
        throw new IllegalArgumentException("Không được đổi mã khi sửa");
      if (!m.get(key).removeIf(r -> r.get(0).equals(oldId)))
        throw new IllegalArgumentException("Bản ghi không còn tồn tại");
    }
    m.get(key).add(new ArrayList<>(row));
    commit(m);
  }

  public synchronized void delete(String key, String id) throws IOException {
    var m = copy(db);
    m.get(key).removeIf(r -> r.get(0).equals(id));
    commit(m);
  }

  public synchronized void replace(Map<String, List<List<String>>> m) throws IOException {
    commit(copy(m));
  }

  private void commit(Map<String, List<List<String>>> m) throws IOException {
    validate(m);
    dao.write(m);
    db = m;
  }

  public static BigDecimal money(String s) {
    try {
      var n = new BigDecimal(s);
      if (n.signum() < 0 || n.scale() > 2 || n.compareTo(new BigDecimal("1000000000000")) > 0)
        throw new Exception();
      return n;
    } catch (Exception e) {
      throw new IllegalArgumentException("Số tiền phải từ 0 đến 10^12, tối đa 2 số lẻ");
    }
  }

  private static int qty(String s, boolean zero) {
    try {
      int n = Integer.parseInt(s);
      if (n < (zero ? 0 : 1) || n > 100000000) throw new Exception();
      return n;
    } catch (Exception e) {
      throw new IllegalArgumentException("Số lượng phải là số nguyên " + (zero ? ">= 0" : "> 0"));
    }
  }

  private static LocalDate date(String s) {
    try {
      return LocalDate.parse(s);
    } catch (Exception e) {
      throw new IllegalArgumentException("Ngày phải hợp lệ theo yyyy-MM-dd: " + s);
    }
  }

  private static List<String> ref(Map<String, List<List<String>>> m, String table, String id) {
    return m.get(table).stream()
        .filter(r -> r.get(0).equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Không tìm thấy " + table + ": " + id + " (hoặc đang được tham chiếu)"));
  }

  public static long stock(Map<String, List<List<String>>> m, String lot) {
    long n = Long.parseLong(ref(m, "lots", lot).get(3));
    for (var r : m.get("purchase_lines")) if (r.get(2).equals(lot)) n += Long.parseLong(r.get(3));
    for (var r : m.get("sale_lines")) if (r.get(2).equals(lot)) n -= Long.parseLong(r.get(3));
    return n;
  }

  public static void validate(Map<String, List<List<String>>> m) {
    if (!m.keySet().equals(Schema.empty().keySet()))
      throw new IllegalArgumentException("Thiếu/thừa bảng dữ liệu");
    for (var t : Schema.TABLES) {
      var ids = new HashSet<String>();
      for (var r : m.get(t.key())) {
        if (r.size() != t.columns().length)
          throw new IllegalArgumentException("Sai số cột: " + t.title());
        if (r.stream().anyMatch(v -> v == null || v.length() > 2000))
          throw new IllegalArgumentException("Ô dữ liệu quá dài");
        if (r.get(0).isBlank() || !r.get(0).matches("[A-Za-z0-9_-]+") || !ids.add(r.get(0)))
          throw new IllegalArgumentException(
              "Mã rỗng, trùng hoặc chứa ký tự không hợp lệ: " + r.get(0));
      }
    }
    for (var r : m.get("employees")) {
      required(r, 1);
      person(r);
      date(r.get(6));
      money(r.get(8));
      if (!r.get(7).matches("[^ @]+@[^ @]+\\.[^ @]+"))
        throw new IllegalArgumentException("Email không hợp lệ");
    }
    for (var r : m.get("customers")) {
      required(r, 1);
      person(r);
    }
    for (var r : m.get("suppliers")) {
      required(r, 1);
      phone(r.get(3));
    }
    for (var r : m.get("medicines")) {
      required(r, 1);
      required(r, 2);
      money(r.get(3));
      if (!Set.of("true", "false").contains(r.get(4)))
        throw new IllegalArgumentException("Kê đơn: true hoặc false");
    }
    for (var r : m.get("lots")) {
      ref(m, "medicines", r.get(1));
      date(r.get(2));
      qty(r.get(3), true);
    }
    for (var r : m.get("purchases")) {
      date(r.get(1));
      ref(m, "suppliers", r.get(2));
      ref(m, "employees", r.get(3));
    }
    for (var r : m.get("sales")) {
      date(r.get(1));
      ref(m, "customers", r.get(2));
      ref(m, "employees", r.get(3));
    }
    for (String key : List.of("purchase_lines", "sale_lines")) {
      var pairs = new HashSet<String>();
      for (var r : m.get(key)) {
        var h = ref(m, key.equals("sale_lines") ? "sales" : "purchases", r.get(1));
        var l = ref(m, "lots", r.get(2));
        qty(r.get(3), false);
        money(r.get(4));
        if (!pairs.add(r.get(1) + "/" + r.get(2)))
          throw new IllegalArgumentException("Một lô chỉ xuất hiện một lần trong mỗi phiếu");
        if (key.equals("sale_lines")) {
          if (!date(l.get(2)).isAfter(date(h.get(1))))
            throw new IllegalArgumentException("Không bán lô đã hết hạn tại ngày hóa đơn");
          if (ref(m, "medicines", l.get(1)).get(4).equals("true") && h.get(4).isBlank())
            throw new IllegalArgumentException("Thuốc kê đơn cần mã đơn bác sĩ");
        }
      }
    }
    // Check stock at every document date, including backdated changes.
    for (var l : m.get("lots")) {
      var movements = new TreeMap<LocalDate, long[]>();
      for (String key : List.of("purchase_lines", "sale_lines"))
        for (var r : m.get(key))
          if (r.get(2).equals(l.get(0))) {
            boolean sale = key.equals("sale_lines");
            LocalDate d = date(ref(m, sale ? "sales" : "purchases", r.get(1)).get(1));
            movements.computeIfAbsent(d, x -> new long[2])[sale ? 1 : 0] += qty(r.get(3), false);
          }
      long n = qty(l.get(3), true);
      for (var delta : movements.values()) {
        n += delta[0] - delta[1];
        if (n < 0) throw new IllegalArgumentException("Xuất vượt tồn kho theo ngày: " + l.get(0));
      }
    }
  }

  private static void required(List<String> r, int i) {
    if (r.get(i).isBlank()) throw new IllegalArgumentException("Không được bỏ trống tên/đơn vị");
  }

  private static void phone(String s) {
    if (!s.matches("0[0-9]{9,10}"))
      throw new IllegalArgumentException("Điện thoại phải có 10–11 chữ số và bắt đầu bằng 0");
  }

  private static void person(List<String> r) {
    if (!Set.of("Nam", "Nữ", "Khác").contains(r.get(3)))
      throw new IllegalArgumentException("Giới tính: Nam, Nữ hoặc Khác");
    if (date(r.get(4)).isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Ngày sinh ở tương lai");
    phone(r.get(5));
  }

  /** Available lots in FEFO order (first expiry, first out). */
  public synchronized List<List<String>> availableLots() {
    return db.get("lots").stream()
        .filter(l -> date(l.get(2)).isAfter(LocalDate.now()) && stock(db, l.get(0)) > 0)
        .sorted(Comparator.comparing(l -> l.get(2)))
        .map(
            l -> {
              var med = ref(db, "medicines", l.get(1));
              return List.of(
                  l.get(0), med.get(1), l.get(2), Long.toString(stock(db, l.get(0))), med.get(3));
            })
        .toList();
  }

  /** Header and all lines become visible together, or nothing changes. */
  public synchronized void checkout(List<String> header, List<List<String>> items)
      throws IOException {
    if (items.isEmpty()) throw new IllegalArgumentException("Giỏ hàng trống");
    var m = copy(db);
    m.get("sales").add(new ArrayList<>(header));
    for (var item : items) {
      if (item.size() != 3) throw new IllegalArgumentException("Dòng giỏ hàng sai định dạng");
      m.get("sale_lines")
          .add(
              new ArrayList<>(
                  List.of(
                      "CT_" + UUID.randomUUID().toString().replace("-", ""),
                      header.get(0),
                      item.get(0),
                      item.get(1),
                      item.get(2))));
    }
    commit(m);
  }

  public synchronized void exportExcel(java.nio.file.Path path) throws IOException {
    vn.pharmacy.dao.ExcelFile.write(path, db);
  }

  public synchronized void importExcel(java.nio.file.Path path) throws IOException {
    replace(vn.pharmacy.dao.ExcelFile.read(path));
  }

  public synchronized List<List<String>> revenue() {
    var map = new TreeMap<String, BigDecimal>();
    for (var r : db.get("sale_lines")) {
      String month = ref(db, "sales", r.get(1)).get(1).substring(0, 7);
      map.merge(month, money(r.get(4)).multiply(new BigDecimal(r.get(3))), BigDecimal::add);
    }
    return map.entrySet().stream()
        .map(e -> List.of(e.getKey(), e.getValue().toPlainString()))
        .toList();
  }

  public synchronized List<List<String>> bestSellers() {
    var sums = new TreeMap<String, Long>();
    for (var r : db.get("sale_lines")) {
      String med = ref(db, "lots", r.get(2)).get(1);
      sums.merge(med, Long.parseLong(r.get(3)), Long::sum);
    }
    return sums.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .map(
            e ->
                List.of(
                    e.getKey(), ref(db, "medicines", e.getKey()).get(1), e.getValue().toString()))
        .toList();
  }

  public synchronized List<List<String>> inventory() {
    return db.get("lots").stream()
        .map(
            l -> {
              long n = stock(db, l.get(0));
              LocalDate d = date(l.get(2));
              String status =
                  !d.isAfter(LocalDate.now())
                      ? "Hết hạn"
                      : d.isBefore(LocalDate.now().plusDays(91))
                          ? "Sắp hết hạn"
                          : n < 10 ? "Sắp hết hàng" : "Bình thường";
              return List.of(
                  l.get(0),
                  ref(db, "medicines", l.get(1)).get(1),
                  l.get(2),
                  Long.toString(n),
                  status);
            })
        .toList();
  }

  public synchronized List<List<String>> invoice(String id) {
    var h = ref(db, "sales", id);
    var out = new ArrayList<List<String>>();
    out.add(List.of("Hóa đơn", id, "Ngày", h.get(1)));
    out.add(
        List.of(
            "Khách hàng",
            ref(db, "customers", h.get(2)).get(1),
            "Nhân viên",
            ref(db, "employees", h.get(3)).get(1)));
    BigDecimal total = BigDecimal.ZERO;
    for (var r : db.get("sale_lines"))
      if (r.get(1).equals(id)) {
        var amount = money(r.get(4)).multiply(new BigDecimal(r.get(3)));
        total = total.add(amount);
        out.add(
            List.of(
                ref(db, "medicines", ref(db, "lots", r.get(2)).get(1)).get(1),
                r.get(3),
                r.get(4),
                amount.toPlainString()));
      }
    out.add(List.of("TỔNG CỘNG (VND)", "", "", total.toPlainString()));
    return out;
  }
}
