package vn.pharmacy.bus;

import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import vn.pharmacy.dao.TextDatabase;
import vn.pharmacy.dto.InvoiceLine;
import vn.pharmacy.dto.InvoiceReport;
import vn.pharmacy.dto.LookupOption;
import vn.pharmacy.dto.SalesSummary;
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
    checkShape(src);
    var m = Schema.empty();
    src.forEach((k, v) -> v.forEach(r -> m.get(k).add(new ArrayList<>(r))));
    return m;
  }

  public synchronized List<List<String>> rows(String key, String query) {
    Schema.table(key);
    String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
    return db.get(key).stream()
        .filter(r -> String.join(" ", r).toLowerCase(Locale.ROOT).contains(q))
        .map(r -> (List<String>) new ArrayList<>(r))
        .toList();
  }

  public synchronized void save(String key, String oldId, List<String> row) throws IOException {
    Schema.table(key);
    if (row == null || row.size() != Schema.table(key).columns().length)
      throw new IllegalArgumentException("Sai số cột: " + Schema.table(key).title());
    if (oldId != null && key.equals("lots")) {
      var oldLot = ref(db, key, oldId);
      if (!Objects.equals(oldLot.get(1), row.get(1)) && hasMovements(oldId))
        throw new IllegalArgumentException(
            "Không đổi thuốc của lô đã có chi tiết nhập/bán; hãy tạo lô mới");
    }
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
    Schema.table(key);
    var m = copy(db);
    if (!m.get(key).removeIf(r -> r.get(0).equals(id)))
      throw new IllegalArgumentException("Bản ghi không còn tồn tại: " + id);
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
      if (s == null || !s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
        throw new IllegalArgumentException();
      LocalDate value = LocalDate.parse(s);
      if (value.getYear() < 1) throw new IllegalArgumentException();
      return value;
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

  /** Reject malformed snapshots before a copy can silently invent a missing empty table. */
  private static void checkShape(Map<String, List<List<String>>> m) {
    if (m == null || !m.keySet().equals(Schema.empty().keySet()))
      throw new IllegalArgumentException("Thiếu/thừa bảng dữ liệu");
    for (var t : Schema.TABLES) {
      var rows = m.get(t.key());
      if (rows == null || rows.stream().anyMatch(Objects::isNull))
        throw new IllegalArgumentException("Bảng hoặc dòng dữ liệu rỗng: " + t.title());
    }
  }

  public static void validate(Map<String, List<List<String>>> m) {
    checkShape(m);
    for (var t : Schema.TABLES) {
      var ids = new HashSet<String>();
      for (var r : m.get(t.key())) {
        if (r.size() != t.columns().length)
          throw new IllegalArgumentException("Sai số cột: " + t.title());
        if (r.stream()
            .anyMatch(
                v ->
                    v == null
                        || v.length() > 2000
                        || v.codePoints()
                            .anyMatch(
                                c ->
                                    (c < 32 && c != 9 && c != 10 && c != 13)
                                        || (c >= 0xd800 && c <= 0xdfff)
                                        || c == 0xfffe
                                        || c == 0xffff)))
          throw new IllegalArgumentException(
              "Ô dữ liệu không hợp lệ: null, quá 2.000 ký tự hoặc ký tự điều khiển không hỗ trợ");
        if (r.get(0).isBlank() || !r.get(0).matches("[A-Za-z0-9_-]+") || !ids.add(r.get(0)))
          throw new IllegalArgumentException(
              "Mã rỗng, trùng hoặc chứa ký tự không hợp lệ: " + r.get(0));
      }
    }
    for (var r : m.get("employees")) {
      required(r, 1);
      person(r);
      if (date(r.get(6)).isBefore(date(r.get(4))))
        throw new IllegalArgumentException("Ngày vào làm không được trước ngày sinh");
      money(r.get(8));
      if (!r.get(7).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
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
      documentDate(r.get(1));
      ref(m, "suppliers", r.get(2));
      ref(m, "employees", r.get(3));
    }
    for (var r : m.get("sales")) {
      documentDate(r.get(1));
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
        if (!date(l.get(2)).isAfter(date(h.get(1))))
          throw new IllegalArgumentException("Không nhập/bán lô đã hết hạn tại ngày chứng từ");
        if (key.equals("sale_lines")) {
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
    if (header == null
        || header.size() != Schema.table("sales").columns().length
        || header.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Thông tin hóa đơn sai định dạng");
    if (items == null || items.isEmpty()) throw new IllegalArgumentException("Giỏ hàng trống");
    var m = copy(db);
    m.get("sales").add(new ArrayList<>(header));
    for (var item : items) {
      if (item == null || item.size() != 3 || item.stream().anyMatch(Objects::isNull))
        throw new IllegalArgumentException("Dòng giỏ hàng sai định dạng");
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

  private boolean hasMovements(String lotId) {
    return List.of("purchase_lines", "sale_lines").stream()
        .flatMap(key -> db.get(key).stream())
        .anyMatch(r -> r.get(2).equals(lotId));
  }

  private static LocalDate documentDate(String value) {
    LocalDate d = date(value);
    if (d.isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Ngày chứng từ không được ở tương lai");
    return d;
  }

  public synchronized List<LookupOption> lookups(String table) {
    Schema.table(table);
    return db.get(table).stream()
        .map(
            row -> {
              String label = row.get(1);
              if (table.equals("lots")) {
                label =
                    ref(db, "medicines", row.get(1)).get(1)
                        + " | HSD "
                        + row.get(2)
                        + " | Tồn "
                        + stock(db, row.get(0));
              }
              return new LookupOption(row.get(0), label);
            })
        .toList();
  }

  /**
   * Suggested current catalogue price; the committed sale line retains its own historical price.
   */
  public synchronized BigDecimal sellingPriceForLot(String lotId) {
    var lot = ref(db, "lots", lotId);
    return money(ref(db, "medicines", lot.get(1)).get(3));
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to))
      throw new IllegalArgumentException("Khoảng ngày không hợp lệ: từ ngày phải <= đến ngày");
  }

  private static boolean within(LocalDate value, LocalDate from, LocalDate to) {
    return !value.isBefore(from) && !value.isAfter(to);
  }

  public synchronized List<List<String>> revenue() {
    return revenue(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31));
  }

  /** Inclusive document-date range; amount uses each historical sale-line price. */
  public synchronized List<List<String>> revenue(LocalDate from, LocalDate to) {
    validateRange(from, to);
    var map = new TreeMap<YearMonth, BigDecimal>();
    for (var r : db.get("sale_lines")) {
      LocalDate soldOn = date(ref(db, "sales", r.get(1)).get(1));
      if (within(soldOn, from, to))
        map.merge(
            YearMonth.from(soldOn),
            money(r.get(4)).multiply(new BigDecimal(r.get(3))),
            BigDecimal::add);
    }
    return map.entrySet().stream()
        .map(e -> List.of(e.getKey().toString(), e.getValue().toPlainString()))
        .toList();
  }

  public synchronized List<List<String>> bestSellers() {
    return bestSellers(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31));
  }

  public synchronized List<List<String>> bestSellers(LocalDate from, LocalDate to) {
    validateRange(from, to);
    var sums = new TreeMap<String, Long>();
    for (var r : db.get("sale_lines")) {
      LocalDate soldOn = date(ref(db, "sales", r.get(1)).get(1));
      if (within(soldOn, from, to)) {
        String med = ref(db, "lots", r.get(2)).get(1);
        sums.merge(med, Long.parseLong(r.get(3)), Long::sum);
      }
    }
    return sums.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .map(
            e ->
                List.of(
                    e.getKey(), ref(db, "medicines", e.getKey()).get(1), e.getValue().toString()))
        .toList();
  }

  /** Inclusive period totals, excluding draft headers without sale lines. */
  public synchronized SalesSummary summary(LocalDate from, LocalDate to) {
    validateRange(from, to);
    BigDecimal total = BigDecimal.ZERO;
    long quantity = 0;
    var completed = new HashSet<String>();
    for (var row : db.get("sale_lines")) {
      if (!within(date(ref(db, "sales", row.get(1)).get(1)), from, to)) continue;
      completed.add(row.get(1));
      quantity += Long.parseLong(row.get(3));
      total = total.add(money(row.get(4)).multiply(new BigDecimal(row.get(3))));
    }
    return new SalesSummary(total, completed.size(), quantity);
  }

  /** Typed invoice report: no GUI/print code needs indexes, joins, or monetary calculations. */
  public synchronized InvoiceReport invoiceReport(String id) {
    var header = ref(db, "sales", id);
    var customer = ref(db, "customers", header.get(2));
    var employee = ref(db, "employees", header.get(3));
    var lines = new ArrayList<InvoiceLine>();
    BigDecimal total = BigDecimal.ZERO;
    for (var row : db.get("sale_lines")) {
      if (!row.get(1).equals(id)) continue;
      var lot = ref(db, "lots", row.get(2));
      var medicine = ref(db, "medicines", lot.get(1));
      int quantity = qty(row.get(3), false);
      BigDecimal price = money(row.get(4));
      BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
      lines.add(
          new InvoiceLine(
              medicine.get(0),
              medicine.get(1),
              medicine.get(2),
              lot.get(0),
              date(lot.get(2)),
              quantity,
              price,
              amount));
      total = total.add(amount);
    }
    return new InvoiceReport(
        header.get(0),
        date(header.get(1)),
        customer.get(1),
        customer.get(2),
        customer.get(5),
        employee.get(1),
        header.get(4),
        lines,
        total);
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
