package vn.pharmacy;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.TextDatabase;
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

  public static void main(String[] args) throws Exception {
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
      SwingUtilities.invokeLater(
          () ->
              button(all(frame, JTabbedPane.class).get(0).getSelectedComponent(), "Bán hàng nhanh")
                  .doClick());
      // EDT remains active within modal dialog's nested event loop.
      new Robot().waitForIdle();
      SwingUtilities.invokeAndWait(
          () -> {
            var dialog =
                Arrays.stream(Window.getWindows())
                    .filter(w -> w instanceof JDialog && w.isVisible())
                    .findFirst()
                    .orElseThrow();
            var fields = all(dialog, JTextField.class);
            fields.get(0).setText("GUI_TEST");
            fields.get(1).setText("RX_GUI");
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
            all(frame, JTabbedPane.class).get(0).setSelectedIndex(9);
          });
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              var paper = new java.awt.print.Paper();
              paper.setSize(595, 842);
              paper.setImageableArea(36, 36, 523, 770);
              var format = new java.awt.print.PageFormat();
              format.setPaper(paper);
              var image =
                  new java.awt.image.BufferedImage(
                      595, 842, java.awt.image.BufferedImage.TYPE_INT_RGB);
              var graphics = image.createGraphics();
              graphics.setColor(Color.WHITE);
              graphics.fillRect(0, 0, 595, 842);
              var invoice =
                  new JTable(
                      new javax.swing.table.DefaultTableModel(
                          service.invoice("GUI_TEST").stream()
                              .map(java.util.List::toArray)
                              .toArray(Object[][]::new),
                          new String[] {"Nội dung", "Số lượng", "Đơn giá", "Thành tiền"}));
              invoice.setSize(800, 300);
              invoice.doLayout();
              var printable =
                  invoice.getPrintable(
                      JTable.PrintMode.FIT_WIDTH,
                      new java.text.MessageFormat("HÓA ĐƠN AN TÂM"),
                      new java.text.MessageFormat("Trang {0}"));
              if (printable.print(graphics, format, 0) != java.awt.print.Printable.PAGE_EXISTS)
                throw new AssertionError("invoice print rendering");
              graphics.dispose();
              ImageIO.write(image, "png", Path.of("docs/ban-in-mau.png").toFile());
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      new Robot().waitForIdle();
      Path screenshot = Path.of(args.length > 0 ? args[0] : "docs/giao-dien.png");
      ImageIO.write(new Robot().createScreenCapture(frame.getBounds()), "png", screenshot.toFile());
      SwingUtilities.invokeAndWait(frame::dispose);
      System.out.println("PASS: GUI 11 tabs, live search, cart checkout -> BUS -> TXT, screenshot");
    }
    System.exit(0);
  }
}
