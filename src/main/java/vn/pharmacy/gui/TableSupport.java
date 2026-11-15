package vn.pharmacy.gui;

import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.*;
import javax.swing.table.*;

/** Display and sorting rules only; stored values stay in their original exact form. */
public final class TableSupport {
  private static final Set<String> NUMBERS =
      Set.of(
          "Giá bán", "Lương", "Tồn đầu", "Số lượng", "Đơn giá", "Doanh thu", "Tồn", "Thành tiền");
  private static final Set<String> MONEY =
      Set.of("Giá bán", "Lương", "Đơn giá", "Doanh thu", "Thành tiền");

  public static String money(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    format.setMaximumFractionDigits(2);
    return format.format(value);
  }

  public static void load(JTable table, String[] columns, List<List<String>> rows) {
    var model =
        new DefaultTableModel(rows.stream().map(List::toArray).toArray(Object[][]::new), columns) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };
    table.setModel(model);
    var sorter = new TableRowSorter<>(model);
    for (int c = 0; c < columns.length; c++) {
      if (NUMBERS.contains(columns[c]))
        sorter.setComparator(
            c, (a, b) -> new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())));
      final boolean money = MONEY.contains(columns[c]);
      final boolean number = NUMBERS.contains(columns[c]);
      table
          .getColumnModel()
          .getColumn(c)
          .setCellRenderer(
              new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean selected, boolean focus, int row, int col) {
                  super.getTableCellRendererComponent(t, v, selected, focus, row, col);
                  setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                  setHorizontalAlignment(number ? SwingConstants.RIGHT : SwingConstants.LEFT);
                  setText(
                      money ? TableSupport.money(new BigDecimal(v.toString())) : String.valueOf(v));
                  if (!selected) {
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(244, 248, 250));
                    setForeground(new Color(35, 52, 62));
                    if (columns[col].equals("Trạng thái") && !v.equals("Bình thường"))
                      setForeground(
                          v.equals("Hết hạn") ? new Color(174, 38, 51) : new Color(159, 92, 12));
                  }
                  return this;
                }
              });
    }
    table.setRowSorter(sorter);
    table.setRowHeight(30);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getTableHeader().setReorderingAllowed(false);
  }

  private TableSupport() {}
}
