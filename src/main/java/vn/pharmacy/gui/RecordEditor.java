package vn.pharmacy.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dto.LookupOption;
import vn.pharmacy.dto.Schema;

/**
 * A reusable input form. It displays references by ID and human-readable name, but returns only
 * IDs. Business rules remain in PharmacyService; this class handles entry controls and input
 * formatting.
 */
public final class RecordEditor extends JPanel {
  private record Field(JComponent input, Supplier<String> value) {}

  private static final Map<String, String> PREFIXES =
      Map.of(
          "employees",
          "NV",
          "customers",
          "KH",
          "suppliers",
          "NCC",
          "medicines",
          "T",
          "lots",
          "LO",
          "purchases",
          "PN",
          "purchase_lines",
          "CTN",
          "sales",
          "HD",
          "sale_lines",
          "CTB");

  private final List<Field> fields = new ArrayList<>();
  private final Schema.Table spec;

  public RecordEditor(Schema.Table spec, List<String> old, PharmacyService service) {
    super(new BorderLayout(0, 14));
    this.spec = spec;
    setName("editor." + spec.key());
    setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    var note = new JLabel("Các ô có * bắt buộc. Chọn mã liên kết theo tên trong danh sách.");
    note.setForeground(new Color(75, 93, 103));
    add(note, BorderLayout.NORTH);
    var grid = new JPanel(new GridBagLayout());
    for (int i = 0; i < spec.columns().length; i++) {
      String value = old == null ? defaultValue(spec.key(), i, service) : old.get(i);
      Field field = createField(i, value, old != null, service);
      fields.add(field);
      field.input().setName("editor." + spec.key() + "." + i);
      var label = new JLabel(label(i) + (required(i) ? " *" : ""));
      label.setLabelFor(field.input());
      var left = constraints(0, i);
      left.anchor = GridBagConstraints.NORTHWEST;
      grid.add(label, left);
      var right = constraints(1, i);
      right.fill = GridBagConstraints.HORIZONTAL;
      right.weightx = 1;
      var entry = new JPanel(new BorderLayout(0, 3));
      if (field.input() instanceof JTextArea area) {
        var scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(380, 70));
        entry.add(scroll, BorderLayout.CENTER);
      } else {
        field.input().setPreferredSize(new Dimension(380, 31));
        entry.add(field.input(), BorderLayout.CENTER);
      }
      String hint = hint(i, old != null);
      if (!hint.isBlank()) {
        var help = new JLabel(hint);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 11f));
        help.setForeground(new Color(90, 107, 117));
        entry.add(help, BorderLayout.SOUTH);
        field.input().setToolTipText(hint);
      }
      grid.add(entry, right);
    }
    add(grid, BorderLayout.CENTER);
    if (spec.key().equals("sale_lines") && old == null) suggestSalePrice(service);
  }

  private void suggestSalePrice(PharmacyService service) {
    var lot = (JComboBox<?>) fields.get(2).input();
    var price = (JTextField) fields.get(4).input();
    Runnable suggest =
        () -> {
          if (!(lot.getSelectedItem() instanceof LookupOption selected)) return;
          price.setText(service.sellingPriceForLot(selected.id()).toPlainString());
        };
    lot.addActionListener(event -> suggest.run());
    suggest.run();
  }

  /** Values follow Schema column order. Foreign key labels are never persisted in data files. */
  public List<String> values() {
    var values = new ArrayList<String>();
    for (Field field : fields) {
      try {
        values.add(field.value().get().trim());
      } catch (IllegalArgumentException ex) {
        field.input().requestFocusInWindow();
        throw ex;
      }
    }
    return values;
  }

  public void focusFirst() {
    SwingUtilities.invokeLater(
        () -> {
          for (Field field : fields) {
            if (field.input().isEnabled()
                && (!(field.input() instanceof JTextField text) || text.isEditable())) {
              field.input().requestFocusInWindow();
              break;
            }
          }
        });
  }

  private Field createField(int column, String value, boolean editing, PharmacyService service) {
    String reference = referenceTable(spec.key(), column);
    if (reference != null) {
      var choices = new ArrayList<>(service.lookups(reference));
      if (!value.isBlank() && choices.stream().noneMatch(c -> c.id().equals(value))) {
        // Preserve a loaded value in the form; BUS still rejects a missing reference on save.
        choices.add(new LookupOption(value, "Không tìm thấy trong danh mục"));
      }
      var combo = new JComboBox<>(choices.toArray(LookupOption[]::new));
      combo.setMaximumRowCount(8);
      if (!value.isBlank()) {
        choices.stream()
            .filter(c -> c.id().equals(value))
            .findFirst()
            .ifPresent(combo::setSelectedItem);
      }
      return new Field(
          combo,
          () -> {
            if (!(combo.getSelectedItem() instanceof LookupOption selected)) {
              throw new IllegalArgumentException(
                  "Chưa có "
                      + Schema.table(reference).title().toLowerCase()
                      + ". Hãy tạo danh mục trước khi lưu "
                      + spec.title().toLowerCase()
                      + ".");
            }
            return selected.id();
          });
    }
    if ((spec.key().equals("employees") || spec.key().equals("customers")) && column == 3) {
      var combo = new JComboBox<>(new String[] {"Nam", "Nữ", "Khác"});
      combo.setSelectedItem(value.isBlank() ? "Nam" : value);
      return new Field(combo, () -> (String) combo.getSelectedItem());
    }
    if (spec.key().equals("medicines") && column == 4) {
      var combo = new JComboBox<>(new String[] {"Không kê đơn", "Thuốc kê đơn"});
      combo.setSelectedIndex(value.equals("true") ? 1 : 0);
      return new Field(combo, () -> Boolean.toString(combo.getSelectedIndex() == 1));
    }
    if (quantity(column)) {
      int initial =
          value.isBlank()
              ? (column == 3 && spec.key().equals("lots") ? 0 : 1)
              : Integer.parseInt(value);
      int minimum = spec.key().equals("lots") ? 0 : 1;
      var spinner = new JSpinner(new SpinnerNumberModel(initial, minimum, 100_000_000, 1));
      var editor = new JSpinner.NumberEditor(spinner, "0");
      spinner.setEditor(editor);
      return new Field(
          spinner,
          () -> {
            try {
              String input = editor.getTextField().getText().trim();
              if (!input.matches("[0-9]+")) throw new NumberFormatException();
              int number = Integer.parseInt(input);
              if (number < minimum || number > 100_000_000) throw new NumberFormatException();
              spinner.setValue(number);
              return Integer.toString(number);
            } catch (NumberFormatException e) {
              throw new IllegalArgumentException(label(column) + " phải là số nguyên hợp lệ.");
            }
          });
    }
    if ((spec.key().equals("medicines") && column >= 5)
        || ((spec.key().equals("employees")
                || spec.key().equals("customers")
                || spec.key().equals("suppliers"))
            && column == 2)) {
      var area = new JTextArea(value, 2, 28);
      area.setLineWrap(true);
      area.setWrapStyleWord(true);
      return new Field(area, area::getText);
    }
    var text = new JTextField(value, 28);
    if (column == 0 && editing) {
      text.setEditable(false);
      text.setBackground(new Color(232, 239, 242));
    }
    if (date(column)) {
      return new Field(
          text,
          () -> {
            String input = text.getText().trim();
            try {
              if (!input.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
              LocalDate.parse(input);
              return input;
            } catch (RuntimeException e) {
              throw new IllegalArgumentException(
                  label(column) + " phải theo yyyy-MM-dd, ví dụ 2026-11-15.");
            }
          });
    }
    return new Field(text, text::getText);
  }

  private String label(int column) {
    if (spec.key().equals("medicines") && column == 4) return "Loại thuốc";
    return spec.columns()[column];
  }

  private String hint(int column, boolean editing) {
    if (column == 0)
      return editing
          ? "Mã định danh cố định, không thay đổi khi sửa."
          : "Mã gợi ý có thể sửa; dùng chữ không dấu, số, _ hoặc -.";
    if (date(column)) return "Định dạng yyyy-MM-dd, ví dụ 2026-11-15.";
    if (money(column)) return "VND • không có dấu tách hàng nghìn • tối đa 2 số lẻ.";
    if (referenceTable(spec.key(), column) != null)
      return "Hiển thị mã và tên; dữ liệu chỉ lưu mã liên kết.";
    if (spec.key().equals("sales") && column == 4) return "Bắt buộc nếu hóa đơn có thuốc kê đơn.";
    if ((spec.key().equals("employees") || spec.key().equals("customers")) && column == 5
        || spec.key().equals("suppliers") && column == 3) return "Giữ số 0 đầu, gồm 10–11 chữ số.";
    if (spec.key().equals("lots") && column == 3)
      return "Số dư khi bắt đầu quản lý; hàng mua mới ghi bằng phiếu nhập.";
    return "";
  }

  private boolean date(int column) {
    return switch (spec.key()) {
      case "employees" -> column == 4 || column == 6;
      case "customers" -> column == 4;
      case "lots" -> column == 2;
      case "purchases", "sales" -> column == 1;
      default -> false;
    };
  }

  private boolean money(int column) {
    return switch (spec.key()) {
      case "employees" -> column == 8;
      case "medicines" -> column == 3;
      case "purchase_lines", "sale_lines" -> column == 4;
      default -> false;
    };
  }

  private boolean quantity(int column) {
    return (spec.key().equals("lots")
            || spec.key().equals("purchase_lines")
            || spec.key().equals("sale_lines"))
        && column == 3;
  }

  private boolean required(int column) {
    if (column == 0
        || referenceTable(spec.key(), column) != null
        || date(column)
        || money(column)
        || quantity(column)) return true;
    return switch (spec.key()) {
      case "employees" -> Set.of(1, 3, 5, 7).contains(column);
      case "customers" -> Set.of(1, 3, 5).contains(column);
      case "suppliers" -> column == 1 || column == 3;
      case "medicines" -> Set.of(1, 2, 4).contains(column);
      default -> false;
    };
  }

  private static String referenceTable(String table, int column) {
    return switch (table) {
      case "lots" -> column == 1 ? "medicines" : null;
      case "purchases" -> column == 2 ? "suppliers" : column == 3 ? "employees" : null;
      case "sales" -> column == 2 ? "customers" : column == 3 ? "employees" : null;
      case "purchase_lines" -> column == 1 ? "purchases" : column == 2 ? "lots" : null;
      case "sale_lines" -> column == 1 ? "sales" : column == 2 ? "lots" : null;
      default -> null;
    };
  }

  private static String defaultValue(String table, int column, PharmacyService service) {
    if (column == 0) {
      String prefix = PREFIXES.get(table);
      var used = new HashSet<String>();
      service.rows(table, "").forEach(row -> used.add(row.get(0)));
      for (int number = 1; number < Integer.MAX_VALUE; number++) {
        String suggestion = prefix + String.format(java.util.Locale.ROOT, "%03d", number);
        if (!used.contains(suggestion)) return suggestion;
      }
    }
    if ((table.equals("employees") || table.equals("customers")) && column == 4)
      return "2000-01-01";
    if (table.equals("employees") && column == 6
        || (table.equals("purchases") || table.equals("sales")) && column == 1)
      return LocalDate.now().toString();
    if (table.equals("lots") && column == 2) return LocalDate.now().plusYears(1).toString();
    if (table.equals("medicines") && column == 3 || table.equals("employees") && column == 8)
      return "0";
    return "";
  }

  private static GridBagConstraints constraints(int x, int y) {
    var c = new GridBagConstraints();
    c.gridx = x;
    c.gridy = y;
    c.insets = new Insets(5, 4, 5, 10);
    return c;
  }
}
