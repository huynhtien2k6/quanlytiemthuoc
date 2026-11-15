package vn.pharmacy;

import java.math.BigDecimal;
import java.util.List;
import javax.swing.*;
import vn.pharmacy.gui.TableSupport;

public final class TableSupportTest {
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var t = new JTable();
          TableSupport.load(
              t,
              new String[] {"Mã", "Số lượng", "Đơn giá"},
              List.of(
                  List.of("A", "10", "10000"),
                  List.of("B", "2", "2500"),
                  List.of("C", "100", "9.5")));
          t.getRowSorter().toggleSortOrder(1);
          if (!t.getValueAt(0, 0).equals("B") || !t.getValueAt(2, 0).equals("C"))
            throw new AssertionError("quantities must sort numerically");
          t.getRowSorter().toggleSortOrder(2);
          if (!t.getValueAt(0, 0).equals("C")) throw new AssertionError("money numeric sort");
          var renderer = t.prepareRenderer(t.getCellRenderer(0, 2), 0, 2);
          if (!((JLabel) renderer).getText().equals("9,5"))
            throw new AssertionError("localized exact decimal");
          if (t.getModel().isCellEditable(0, 0))
            throw new AssertionError("table bypasses BUS edits");
          if (!TableSupport.money(new BigDecimal("1250000.25")).equals("1.250.000,25"))
            throw new AssertionError("VND presentation");
          System.out.println("PASS: 5 numeric sorting/rendering checks");
        });
  }
}
