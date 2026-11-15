package vn.pharmacy.gui;

import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dto.Schema;

public final class MainFrame extends JFrame {
  private final PharmacyService service;
  private final JTabbedPane tabs = new JTabbedPane();
  private final List<Runnable> refreshers = new ArrayList<>();
  private final JLabel status = new JLabel();

  public MainFrame(PharmacyService service) {
    super("AN TÂM • Quản lý cửa hàng thuốc");
    this.service = service;
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setSize(1280, 800);
    setMinimumSize(new Dimension(950, 600));
    setLocationRelativeTo(null);
    var header = new JPanel(new BorderLayout());
    header.setBackground(new Color(15, 85, 92));
    header.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
    var brand = new JLabel("AN TÂM  /  NHÀ THUỐC");
    brand.setFont(new Font("SansSerif", Font.BOLD, 25));
    brand.setForeground(Color.WHITE);
    header.add(brand, BorderLayout.WEST);
    var clock = new JLabel();
    clock.setForeground(Color.WHITE);
    header.add(clock, BorderLayout.EAST);
    var timer =
        new javax.swing.Timer(
            1000,
            e ->
                clock.setText(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm:ss")
                        .format(LocalDateTime.now())));
    timer.start();
    add(header, BorderLayout.NORTH);
    for (var t : Schema.TABLES) tabs.addTab(t.title(), tablePanel(t));
    tabs.addTab("Thống kê", stats());
    tabs.addTab("Hướng dẫn", help());
    add(tabs, BorderLayout.CENTER);
    status.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    add(status, BorderLayout.SOUTH);
    setJMenuBar(menu());
    refresh();
  }

  private JMenuBar menu() {
    var bar = new JMenuBar();
    var file = new JMenu("Dữ liệu");
    for (String label : List.of("Nhập Excel (.xlsx)", "Xuất Excel (.xlsx)", "Làm mới", "Thoát")) {
      var item = new JMenuItem(label);
      item.addActionListener(
          e -> {
            if (label.startsWith("Nhập")) excel(true);
            else if (label.startsWith("Xuất")) excel(false);
            else if (label.equals("Thoát")) dispose();
            else refresh();
          });
      file.add(item);
    }
    bar.add(file);
    return bar;
  }

  private JPanel tablePanel(Schema.Table spec) {
    var panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    var table = new JTable();
    table.setRowHeight(30);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    var search = new JTextField(22);
    Runnable load =
        () -> {
          table.setModel(model(spec.columns(), service.rows(spec.key(), search.getText())));
          for (int i = 0; i < table.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(i == 1 ? 190 : 150);
        };
    refreshers.add(load);
    search
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                load.run();
              }

              public void removeUpdate(DocumentEvent e) {
                load.run();
              }

              public void changedUpdate(DocumentEvent e) {
                load.run();
              }
            });
    var tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
    tools.add(new JLabel("Tìm kiếm:"));
    tools.add(search);
    button(tools, "＋ Thêm", () -> edit(spec, null));
    button(
        tools,
        "Sửa",
        () -> {
          var row = selected(table);
          if (row != null) edit(spec, row);
        });
    button(
        tools,
        "Xóa",
        () -> {
          var row = selected(table);
          if (row != null
              && JOptionPane.showConfirmDialog(
                      this,
                      "Xóa " + row.get(0) + "? Dữ liệu đang được tham chiếu sẽ bị chặn.",
                      "Xác nhận xóa",
                      JOptionPane.YES_NO_OPTION)
                  == 0)
            run(
                () -> {
                  service.delete(spec.key(), row.get(0));
                  refresh();
                });
        });
    button(tools, "In bảng", () -> print(table, spec.title()));
    if (spec.key().equals("sales")) button(tools, "Bán hàng nhanh", this::checkout);
    if (spec.key().equals("sales"))
      button(
          tools,
          "In hóa đơn",
          () -> {
            var row = selected(table);
            if (row != null) {
              var invoice =
                  new JTable(
                      model(
                          new String[] {
                            "Nội dung / Thuốc",
                            "Số lượng / Giá trị",
                            "Đơn giá / Nội dung",
                            "Thành tiền / Giá trị"
                          },
                          service.invoice(row.get(0))));
              print(invoice, "HÓA ĐƠN NHÀ THUỐC AN TÂM • " + row.get(0));
            }
          });
    panel.add(tools, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    var hint =
        new JLabel(
            "Ngày: yyyy-MM-dd • Tiền: VND, dấu chấm thập phân • Mã liên kết phải tồn tại • Kê đơn:"
                + " true/false");
    panel.add(hint, BorderLayout.SOUTH);
    return panel;
  }

  private void checkout() {
    var lots = service.availableLots();
    if (lots.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Không có lô còn hạn và còn tồn");
      return;
    }
    var customers = service.rows("customers", "");
    var employees = service.rows("employees", "");
    if (customers.isEmpty() || employees.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Cần có nhân viên và khách hàng trước");
      return;
    }
    var panel = new JPanel(new BorderLayout(8, 8));
    var top = new JPanel(new GridLayout(0, 2, 8, 8));
    var id =
        new JTextField(
            "HD_"
                + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(LocalDateTime.now()));
    var customer =
        new JComboBox<>(
            customers.stream().map(r -> r.get(0) + " — " + r.get(1)).toArray(String[]::new));
    var employee =
        new JComboBox<>(
            employees.stream().map(r -> r.get(0) + " — " + r.get(1)).toArray(String[]::new));
    var prescription = new JTextField();
    top.add(new JLabel("Mã hóa đơn"));
    top.add(id);
    top.add(new JLabel("Khách hàng"));
    top.add(customer);
    top.add(new JLabel("Nhân viên"));
    top.add(employee);
    top.add(new JLabel("Mã đơn bác sĩ (nếu có)"));
    top.add(prescription);
    panel.add(top, BorderLayout.NORTH);
    var cartModel = model(new String[] {"Mã lô", "Số lượng", "Đơn giá"}, List.of());
    var cart = new JTable(cartModel);
    cart.setRowHeight(28);
    var center = new JPanel(new BorderLayout());
    center.add(
        new JLabel("Lô gợi ý theo FEFO: hạn gần nhất trước. Kiểm tra lại đơn giá trước khi bán."),
        BorderLayout.NORTH);
    var scroll = new JScrollPane(cart);
    scroll.setPreferredSize(new Dimension(740, 200));
    center.add(scroll, BorderLayout.CENTER);
    panel.add(center, BorderLayout.CENTER);
    var bottom = new JPanel(new GridLayout(0, 1));
    var picker = new JPanel(new FlowLayout(FlowLayout.LEFT));
    var lotBox =
        new JComboBox<>(
            lots.stream()
                .map(l -> l.get(0) + " | " + l.get(1) + " | HSD " + l.get(2) + " | Tồn " + l.get(3))
                .toArray(String[]::new));
    picker.add(lotBox);
    var qty = new JSpinner(new SpinnerNumberModel(1, 1, 100000000, 1));
    picker.add(qty);
    var price = new JTextField(lots.get(0).get(4), 10);
    picker.add(price);
    lotBox.addActionListener(e -> price.setText(lots.get(lotBox.getSelectedIndex()).get(4)));
    var total = new JLabel("Tổng tiền: 0 VND");
    Runnable updateTotal =
        () -> {
          var sum = java.math.BigDecimal.ZERO;
          for (int r = 0; r < cartModel.getRowCount(); r++)
            sum =
                sum.add(
                    new java.math.BigDecimal(cartModel.getValueAt(r, 1).toString())
                        .multiply(new java.math.BigDecimal(cartModel.getValueAt(r, 2).toString())));
          total.setText("Tổng tiền: " + sum.toPlainString() + " VND");
        };
    button(
        picker,
        "Thêm lô",
        () ->
            run(
                () -> {
                  String lot = lots.get(lotBox.getSelectedIndex()).get(0);
                  String amount = PharmacyService.money(price.getText().trim()).toPlainString();
                  for (int r = 0; r < cartModel.getRowCount(); r++)
                    if (cartModel.getValueAt(r, 0).equals(lot))
                      throw new IllegalArgumentException(
                          "Lô đã có trong giỏ; xóa rồi thêm lại nếu cần sửa");
                  cartModel.addRow(new Object[] {lot, qty.getValue().toString(), amount});
                  updateTotal.run();
                }));
    button(
        picker,
        "Bỏ dòng",
        () -> {
          if (cart.getSelectedRow() >= 0) cartModel.removeRow(cart.getSelectedRow());
          updateTotal.run();
        });
    bottom.add(picker);
    bottom.add(total);
    panel.add(bottom, BorderLayout.SOUTH);
    while (JOptionPane.showConfirmDialog(
            this,
            panel,
            "Bán hàng nhanh • " + LocalDate.now(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      try {
        var items = new ArrayList<List<String>>();
        for (int r = 0; r < cartModel.getRowCount(); r++)
          items.add(
              List.of(
                  cartModel.getValueAt(r, 0).toString(),
                  cartModel.getValueAt(r, 1).toString(),
                  cartModel.getValueAt(r, 2).toString()));
        service.checkout(
            List.of(
                id.getText().trim(),
                LocalDate.now().toString(),
                customers.get(customer.getSelectedIndex()).get(0),
                employees.get(employee.getSelectedIndex()).get(0),
                prescription.getText().trim()),
            items);
        refresh();
        JOptionPane.showMessageDialog(
            this, "Đã lưu hóa đơn " + id.getText() + ". Chọn hóa đơn và bấm In hóa đơn để in.");
        return;
      } catch (Exception ex) {
        error(ex);
      }
    }
  }

  private void edit(Schema.Table spec, List<String> old) {
    var form = new JPanel(new GridBagLayout());
    var fields = new ArrayList<JTextField>();
    for (int i = 0; i < spec.columns().length; i++) {
      var c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = i;
      c.anchor = GridBagConstraints.WEST;
      c.insets = new Insets(6, 6, 6, 6);
      form.add(new JLabel(spec.columns()[i]), c);
      var field = new JTextField(old == null ? "" : old.get(i), 28);
      if (old == null
          && (spec.columns()[i].equals("Ngày") || spec.columns()[i].startsWith("Hạn dùng")))
        field.setText(LocalDate.now().toString());
      if (old == null && spec.columns()[i].startsWith("Kê đơn")) field.setText("false");
      if (old != null && i == 0) field.setEditable(false);
      fields.add(field);
      c.gridx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      form.add(field, c);
    }
    while (JOptionPane.showConfirmDialog(
            this,
            form,
            (old == null ? "Thêm " : "Sửa ") + spec.title(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      try {
        service.save(
            spec.key(),
            old == null ? null : old.get(0),
            fields.stream().map(f -> f.getText().trim()).toList());
        refresh();
        break;
      } catch (Exception ex) {
        error(ex);
      }
    }
  }

  private JPanel stats() {
    var p = new JPanel(new GridLayout(3, 1, 8, 8));
    p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
    stat(
        p,
        "Doanh thu theo tháng (VND)",
        new String[] {"Tháng", "Doanh thu"},
        () -> service.revenue());
    stat(
        p,
        "Thuốc bán chạy",
        new String[] {"Mã thuốc", "Tên thuốc", "Số lượng"},
        () -> service.bestSellers());
    stat(
        p,
        "Tồn kho và cảnh báo hạn dùng",
        new String[] {"Mã lô", "Tên thuốc", "Hạn dùng", "Tồn", "Trạng thái"},
        () -> service.inventory());
    return p;
  }

  private void stat(
      JPanel parent,
      String title,
      String[] columns,
      java.util.function.Supplier<List<List<String>>> rows) {
    var box = new JPanel(new BorderLayout());
    var tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
    tools.add(new JLabel(title));
    var table = new JTable();
    table.setRowHeight(25);
    table.setAutoCreateRowSorter(true);
    button(tools, "In báo cáo", () -> print(table, title));
    box.add(tools, BorderLayout.NORTH);
    box.add(new JScrollPane(table), BorderLayout.CENTER);
    parent.add(box);
    refreshers.add(() -> table.setModel(model(columns, rows.get())));
  }

  private JScrollPane help() {
    var text =
        new JTextArea(
            "HƯỚNG DẪN SỬ DỤNG\n\n"
                + "1. Quản lý danh mục: Nhân viên, Khách hàng, Nhà cung cấp, Thuốc.\n"
                + "2. Tạo Lô thuốc, khai báo hạn dùng và tồn đầu tại thời điểm bắt đầu quản lý.\n"
                + "3. Nhập kho: tạo Phiếu nhập rồi thêm các dòng Chi tiết nhập.\n"
                + "4. Bán hàng: tạo Hóa đơn rồi thêm các dòng Chi tiết bán (mã lô, số lượng, đơn"
                + " giá).\n"
                + "   Thuốc kê đơn bắt buộc có mã đơn bác sĩ ở hóa đơn.\n"
                + "5. Chọn hóa đơn → In hóa đơn. Có thể chọn máy in PDF của hệ điều hành.\n"
                + "6. Thống kê gồm doanh thu theo tháng, thuốc bán chạy, tồn kho/cảnh báo.\n"
                + "7. Dữ liệu → Xuất Excel tạo file mẫu đủ 9 sheet.\n"
                + "   Giữ nguyên tên sheet và tiêu đề; ngày nhập dạng văn bản yyyy-MM-dd;\n"
                + "   điện thoại dạng văn bản để giữ số 0 đầu; không dùng công thức.\n"
                + "   Nhập Excel thay thế toàn bộ dữ liệu sau khi kiểm tra và xác nhận.\n"
                + "8. Xóa bản ghi cha chỉ được phép sau khi xóa các bản ghi con liên quan.\n\n"
                + "GIẢ ĐỊNH NGHIỆP VỤ\n"
                + "• Giá tiền không âm, tối đa 2 số lẻ. Đơn vị bán và nhập của mỗi thuốc thống"
                + " nhất.\n"
                + "• Tồn đầu là số dư tại thời điểm bắt đầu quản lý; không ghi nhận nó thành doanh"
                + " thu.\n"
                + "• Hàng hết hạn vào đúng ngày ghi trên lô không được bán.\n"
                + "• Nhập kho được tính trước xuất kho nếu cùng ngày.\n"
                + "• Mỗi lô chỉ có một dòng trong một phiếu. Sửa số lượng thay vì thêm dòng"
                + " trùng.\n"
                + "• Đây là đồ án học tập, không kết nối hệ thống đơn thuốc điện tử.\n\n"
                + "KIẾN TRÚC\n"
                + "GUI → BUS → DAO, DTO dùng chung. Dữ liệu TXT được lưu theo một snapshot nguyên"
                + " tử.\n"
                + "Không phân loại KH VIP/thường hoặc nhân viên dược sĩ/thu ngân.\n"
                + "Tài liệu mô hình quan hệ và kịch bản bảo vệ ở thư mục docs.\n");
    text.setEditable(false);
    text.setFont(new Font("SansSerif", Font.PLAIN, 16));
    text.setMargin(new Insets(20, 20, 20, 20));
    return new JScrollPane(text);
  }

  private void excel(boolean input) {
    var chooser = new JFileChooser();
    chooser.setFileFilter(
        new javax.swing.filechooser.FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx"));
    if ((input ? chooser.showOpenDialog(this) : chooser.showSaveDialog(this))
        != JFileChooser.APPROVE_OPTION) return;
    var path = chooser.getSelectedFile().toPath();
    if (!input && !path.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      path = java.nio.file.Path.of(path + ".xlsx");
    if (input
        && JOptionPane.showConfirmDialog(
                this,
                "Thay thế toàn bộ dữ liệu bằng Excel? Nên xuất bản sao trước khi nhập.",
                "Nhập dữ liệu",
                JOptionPane.YES_NO_OPTION)
            != 0) return;
    if (!input
        && java.nio.file.Files.exists(path)
        && JOptionPane.showConfirmDialog(
                this, "Ghi đè file đã có?", "Xuất Excel", JOptionPane.YES_NO_OPTION)
            != 0) return;
    final var target = path;
    run(
        () -> {
          if (input) service.importExcel(target);
          else service.exportExcel(target);
          refresh();
          JOptionPane.showMessageDialog(this, "Hoàn tất: " + target);
        });
  }

  private static DefaultTableModel model(String[] columns, List<List<String>> rows) {
    return new DefaultTableModel(
        rows.stream().map(List::toArray).toArray(Object[][]::new), columns) {
      public boolean isCellEditable(int r, int c) {
        return false;
      }
    };
  }

  private List<String> selected(JTable table) {
    int r = table.getSelectedRow();
    if (r < 0) {
      JOptionPane.showMessageDialog(this, "Hãy chọn một dòng");
      return null;
    }
    r = table.convertRowIndexToModel(r);
    var row = new ArrayList<String>();
    for (int c = 0; c < table.getModel().getColumnCount(); c++)
      row.add(table.getModel().getValueAt(r, c).toString());
    return row;
  }

  private void print(JTable table, String title) {
    run(
        () ->
            table.print(
                JTable.PrintMode.FIT_WIDTH,
                new MessageFormat(title.replace("'", "''")),
                new MessageFormat("Trang {0}")));
  }

  private void refresh() {
    refreshers.forEach(Runnable::run);
    status.setText(
        "Đã tải dữ liệu • "
            + service.inventory().size()
            + " lô thuốc • Cảnh báo: "
            + service.inventory().stream().filter(r -> !r.get(4).equals("Bình thường")).count()
            + " • Lưu TXT tự động sau mỗi thay đổi");
  }

  private static void button(JPanel p, String label, Runnable action) {
    var b = new JButton(label);
    b.addActionListener(e -> action.run());
    p.add(b);
  }

  private void error(Exception e) {
    JOptionPane.showMessageDialog(
        this, e.getMessage(), "Không thể thực hiện", JOptionPane.ERROR_MESSAGE);
  }

  @FunctionalInterface
  private interface Job {
    void run() throws Exception;
  }

  private void run(Job job) {
    try {
      job.run();
    } catch (Exception e) {
      error(e);
    }
  }
}
