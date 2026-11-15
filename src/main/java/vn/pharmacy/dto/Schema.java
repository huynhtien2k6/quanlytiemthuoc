package vn.pharmacy.dto;

import java.util.*;

public final class Schema {
  public record Table(String key, String title, String... columns) {
    public Table {
      columns = columns.clone();
    }

    @Override
    public String[] columns() {
      return columns.clone();
    }
  }

  public static final List<Table> TABLES =
      List.of(
          new Table(
              "employees",
              "Nhân viên",
              "Mã",
              "Họ tên",
              "Địa chỉ",
              "Giới tính",
              "Ngày sinh",
              "Điện thoại",
              "Ngày vào làm",
              "Email",
              "Lương"),
          new Table(
              "customers",
              "Khách hàng",
              "Mã",
              "Họ tên",
              "Địa chỉ",
              "Giới tính",
              "Ngày sinh",
              "Điện thoại"),
          new Table("suppliers", "Nhà cung cấp", "Mã", "Tên", "Địa chỉ", "Điện thoại"),
          new Table(
              "medicines",
              "Thuốc",
              "Mã",
              "Tên thuốc",
              "Đơn vị",
              "Giá bán",
              "Kê đơn (true/false)",
              "Hoạt chất / công dụng",
              "Hướng dẫn"),
          new Table("lots", "Lô thuốc", "Mã lô", "Mã thuốc", "Hạn dùng", "Tồn đầu"),
          new Table("purchases", "Phiếu nhập", "Mã phiếu", "Ngày", "Mã NCC", "Mã NV"),
          new Table(
              "purchase_lines",
              "Chi tiết nhập",
              "Mã dòng",
              "Mã phiếu",
              "Mã lô",
              "Số lượng",
              "Đơn giá"),
          new Table(
              "sales", "Hóa đơn", "Mã hóa đơn", "Ngày", "Mã KH", "Mã NV", "Mã đơn bác sĩ (nếu có)"),
          new Table(
              "sale_lines",
              "Chi tiết bán",
              "Mã dòng",
              "Mã hóa đơn",
              "Mã lô",
              "Số lượng",
              "Đơn giá"));

  public static Table table(String key) {
    return TABLES.stream()
        .filter(t -> t.key().equals(key))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Bảng dữ liệu không tồn tại: " + key));
  }

  public static Map<String, List<List<String>>> empty() {
    var m = new LinkedHashMap<String, List<List<String>>>();
    TABLES.forEach(t -> m.put(t.key(), new ArrayList<>()));
    return m;
  }

  private Schema() {}
}
