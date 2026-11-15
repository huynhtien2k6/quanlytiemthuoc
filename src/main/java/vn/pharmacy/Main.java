package vn.pharmacy;

import java.nio.file.*;
import javax.swing.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.TextDatabase;
import vn.pharmacy.gui.MainFrame;

public final class Main {
  public static void configureAppearance() throws Exception {
    System.setProperty("awt.useSystemAAFontSettings", "on");
    System.setProperty("swing.aatext", "true");
    for (var info : UIManager.getInstalledLookAndFeels())
      if (info.getName().equals("Nimbus")) {
        UIManager.setLookAndFeel(info.getClassName());
        break;
      }
    var defaults = UIManager.getDefaults();
    var keys = java.util.Collections.list(defaults.keys());
    for (Object key : keys)
      if (defaults.get(key) instanceof javax.swing.plaf.FontUIResource)
        defaults.put(key, new javax.swing.plaf.FontUIResource("SansSerif", 0, 14));
    UIManager.put("nimbusBase", new java.awt.Color(24, 105, 112));
    UIManager.put("nimbusSelectionBackground", new java.awt.Color(30, 122, 130));
    UIManager.put("control", new java.awt.Color(245, 248, 250));
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          TextDatabase dao = null;
          try {
            configureAppearance();
            UIManager.put("Table.font", new java.awt.Font("SansSerif", 0, 14));
            UIManager.put("Label.font", new java.awt.Font("SansSerif", 0, 14));
            Path folder = Path.of(System.getProperty("pharmacy.data", "data"));
            dao = new TextDatabase(folder);
            var service = new PharmacyService(dao);
            final var resource = dao;
            Runtime.getRuntime()
                .addShutdownHook(
                    new Thread(
                        () -> {
                          try {
                            resource.close();
                          } catch (Exception ignored) {
                          }
                        }));
            var frame = new MainFrame(service);
            frame.addWindowListener(
                new java.awt.event.WindowAdapter() {
                  public void windowClosed(java.awt.event.WindowEvent e) {
                    System.exit(0);
                  }
                });
            frame.setVisible(true);
          } catch (Exception e) {
            if (dao != null)
              try {
                dao.close();
              } catch (Exception ignored) {
              }
            JOptionPane.showMessageDialog(
                null,
                "Không mở được ứng dụng: " + e.getMessage(),
                "Lỗi khởi động",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
          }
        });
  }
}
