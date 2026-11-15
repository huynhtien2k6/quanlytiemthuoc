package vn.pharmacy;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.TextDatabase;
import vn.pharmacy.gui.InvoicePrinter;
import vn.pharmacy.gui.MainFrame;

/** Display-dependent end-to-end smoke test, run separately from headless BUS checks. */
public final class GuiSmokeTest {
  private static <T extends Component> java.util.List<T> all(Component root, Class<T> type) {
    var out = new ArrayList<T>();
    if (type.isInstance(root)) out.add(type.cast(root));
    if (root instanceof Container c)
      for (var child : c.getComponents()) out.addAll(all(child, type));
    return out;
  }

  private static AbstractButton button(Component root, String text) {
    return all(root, AbstractButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static Window visibleDialog() {
    return Arrays.stream(Window.getWindows())
        .filter(w -> w instanceof JDialog && w.isVisible())
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private static <T extends Component> T named(Component root, String name, Class<T> type) {
    return all(root, type).stream()
        .filter(c -> name.equals(c.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing control " + name));
  }

  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          error.printStackTrace();
          System.exit(1);
        });
    Main.configureAppearance();
    Path temp = Files.createTempDirectory("pharmacy-gui-");
    Files.copy(Path.of("data/store.txt"), temp.resolve("store.txt"));
    try (var dao = new TextDatabase(temp)) {
      var service = new PharmacyService(dao);
      var ref = new AtomicReference<MainFrame>();
      SwingUtilities.invokeAndWait(
          () -> {
            var f = new MainFrame(service);
            f.setVisible(true);
            ref.set(f);
          });
      var frame = ref.get();
      // Render every tab, exercise table models and search document callbacks.
      SwingUtilities.invokeAndWait(
          () -> {
            var tabs = all(frame, JTabbedPane.class).get(0);
            if (tabs.getTabCount() != 11) throw new AssertionError("11 tabs expected");
            for (int i = 0; i < tabs.getTabCount(); i++) {
              tabs.setSelectedIndex(i);
              frame.validate();
            }
            tabs.setSelectedIndex(0);
            var search = all(tabs.getSelectedComponent(), JTextField.class).get(0);
            search.setText("NV01");
            if (all(tabs.getSelectedComponent(), JTable.class).get(0).getRowCount() != 1)
              throw new AssertionError("search");
            search.setText("");
            tabs.setSelectedIndex(7);
          });
      // Add/edit/delete through real dialogs to verify GUI -> BUS -> DAO field mappings.
      SwingUtilities.invokeAndWait(() -> all(frame, JTabbedPane.class).get(0).setSelectedIndex(1));
      SwingUtilities.invokeLater(
          () ->
              button(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), "＋ Thêm")
                  .doClick());
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var dialog = visibleDialog();
            named(dialog, "editor.customers.0", JTextField.class).setText("GUI_KH");
            named(dialog, "editor.customers.1", JTextField.class).setText("Khách giao diện");
            named(dialog, "editor.customers.5", JTextField.class).setText("0901234567");
            button(dialog, "OK").doClick();
          });
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var tab = all(frame, JTabbedPane.class).get(0).getSelectedComponent();
            var table = all(tab, JTable.class).get(0);
            if (service.rows("customers", "GUI_KH").size() != 1)
              throw new AssertionError("GUI add customer");
            for (int r = 0; r < table.getRowCount(); r++)
              if (table.getValueAt(r, 0).equals("GUI_KH")) table.setRowSelectionInterval(r, r);
          });
      SwingUtilities.invokeLater(
          () ->
              button(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), "Sửa").doClick());
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var dialog = visibleDialog();
            named(dialog, "editor.customers.1", JTextField.class).setText("Khách đã sửa");
            button(dialog, "OK").doClick();
          });
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            if (!service.rows("customers", "GUI_KH").get(0).get(1).equals("Khách đã sửa"))
              throw new AssertionError("GUI edit customer");
            var table =
                all(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), JTable.class)
                    .get(0);
            for (int r = 0; r < table.getRowCount(); r++)
              if (table.getValueAt(r, 0).equals("GUI_KH")) table.setRowSelectionInterval(r, r);
          });
      SwingUtilities.invokeLater(
          () ->
              button(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), "Xóa").doClick());
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var pane = all(visibleDialog(), JOptionPane.class).get(0);
            pane.setValue(JOptionPane.YES_OPTION);
          });
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            if (!service.rows("customers", "GUI_KH").isEmpty())
              throw new AssertionError("GUI delete customer");
            all(frame, JTabbedPane.class).get(0).setSelectedIndex(7);
          });
      SwingUtilities.invokeLater(
          () ->
              button(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), "Bán hàng nhanh")
                  .doClick());
      // EDT remains active within modal dialog's nested event loop.
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var dialog = visibleDialog();
            var fields = all(dialog, JTextField.class);
            fields.get(0).setText("GUI_TEST");
            fields.get(1).setText("RX_GUI");
            var spinner = all(dialog, JSpinner.class).get(0);
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("2.5");
          });
      SwingUtilities.invokeLater(() -> button(visibleDialog(), "Thêm lô").doClick());
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> all(visibleDialog(), JOptionPane.class).get(0).setValue(JOptionPane.OK_OPTION));
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var dialog = visibleDialog();
            if (all(dialog, JTable.class).get(0).getRowCount() != 0)
              throw new AssertionError("fractional quantity silently truncated");
            var spinner = all(dialog, JSpinner.class).get(0);
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("2");
            button(dialog, "Thêm lô").doClick();
            button(dialog, "OK").doClick();
          });
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            for (var w : Window.getWindows())
              if (w instanceof JDialog && w.isVisible()) button(w, "OK").doClick();
            if (service.rows("sales", "GUI_TEST").size() != 1)
              throw new AssertionError("GUI checkout header");
            if (service.rows("sale_lines", "GUI_TEST").size() != 1)
              throw new AssertionError("GUI checkout line");
            if (!service.rows("sale_lines", "GUI_TEST").get(0).get(3).equals("2"))
              throw new AssertionError("typed spinner quantity not committed");
            all(frame, JTabbedPane.class).get(0).setSelectedIndex(9);
          });
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              var printer = new InvoicePrinter(service.invoiceReport("GUI_TEST"));
              var image = printer.renderPage(InvoicePrinter.a4(), 0, 1.3);
              ImageIO.write(image, "png", Path.of("docs/ban-in-mau.png").toFile());
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      SwingUtilities.invokeAndWait(
          () -> {
            var stats = all(frame, JTabbedPane.class).get(0).getSelectedComponent();
            named(stats, "stats.from", JTextField.class).setText("2026-11-01");
            named(stats, "stats.to", JTextField.class).setText("2026-11-03");
            button(stats, "Áp dụng").doClick();
            var tables = all(stats, JTable.class);
            if (tables.size() != 3
                || new java.math.BigDecimal(tables.get(0).getValueAt(0, 1).toString())
                        .compareTo(new java.math.BigDecimal("580000"))
                    != 0)
              throw new AssertionError(
                  "GUI period-filtered reports: "
                      + tables.stream()
                          .map(
                              t ->
                                  t.getColumnName(0)
                                      + ":"
                                      + (t.getRowCount() > 0 ? t.getValueAt(0, 1) : "EMPTY"))
                          .toList());
            button(stats, "Tất cả").doClick();
          });
      new Robot().waitForIdle();
      Path screenshot = Path.of(args.length > 0 ? args[0] : "docs/giao-dien.png");
      ImageIO.write(new Robot().createScreenCapture(frame.getBounds()), "png", screenshot.toFile());
      SwingUtilities.invokeAndWait(frame::dispose);
      System.out.println(
          "PASS: GUI 11 tabs, search, customer CRUD, cart -> TXT, filtered stats, invoice"
              + " rendering");
    }
    System.exit(0);
  }
}
