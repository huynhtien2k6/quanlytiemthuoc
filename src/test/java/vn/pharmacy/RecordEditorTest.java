package vn.pharmacy;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.TextDatabase;
import vn.pharmacy.dto.LookupOption;
import vn.pharmacy.dto.Schema;
import vn.pharmacy.gui.RecordEditor;

/**
 * Headless tests verify field bindings and persisted IDs rather than only their displayed labels.
 */
public final class RecordEditorTest {
  private static int checks;

  private static void check(boolean valid, String message) {
    checks++;
    if (!valid) throw new AssertionError(message);
  }

  private static Component named(Component root, String name) {
    if (name.equals(root.getName())) return root;
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        Component found = named(child, name);
        if (found != null) return found;
      }
    }
    return null;
  }

  public static void main(String[] args) throws Exception {
    Path temp = Files.createTempDirectory("pharmacy-editor-");
    Files.copy(Path.of("data/store.txt"), temp.resolve("store.txt"));
    try (var dao = new TextDatabase(temp)) {
      var service = new PharmacyService(dao);
      SwingUtilities.invokeAndWait(
          () -> {
            var lotEditor = new RecordEditor(Schema.table("lots"), null, service);
            var medicine = (JComboBox<?>) named(lotEditor, "editor.lots.1");
            check(medicine.getSelectedItem() instanceof LookupOption, "FK uses typed lookup");
            var selected = (LookupOption) medicine.getSelectedItem();
            check(lotEditor.values().get(1).equals(selected.id()), "Persist FK ID, never label");
            check(
                lotEditor.values().get(3).equals("0"), "New lot opening balance defaults to zero");

            var row = service.rows("customers", "").get(0);
            var customerEditor = new RecordEditor(Schema.table("customers"), row, service);
            check(
                !((JTextField) named(customerEditor, "editor.customers.0")).isEditable(),
                "Existing PK is immutable");
            check(customerEditor.values().equals(row), "Editing a record preserves ordered values");
            var birth = (JTextField) named(customerEditor, "editor.customers.4");
            birth.setText("2026-02-30");
            try {
              customerEditor.values();
              throw new AssertionError("Invalid date accepted");
            } catch (IllegalArgumentException expected) {
              checks++;
            }

            birth.setText("2000-02-29");
            check(
                customerEditor.values().get(4).equals("2000-02-29"),
                "Valid leap-day date preserved exactly");

            var drugRow = service.rows("medicines", "").get(0);
            var drugEditor = new RecordEditor(Schema.table("medicines"), drugRow, service);
            var type = (JComboBox<?>) named(drugEditor, "editor.medicines.4");
            type.setSelectedIndex(0);
            check(
                drugEditor.values().get(4).equals("false"),
                "Readable non-prescription choice stores false");
            type.setSelectedIndex(1);
            check(
                drugEditor.values().get(4).equals("true"),
                "Readable prescription choice stores true");

            var quantity = (JSpinner) named(lotEditor, "editor.lots.3");
            ((JSpinner.NumberEditor) quantity.getEditor()).getTextField().setText("2.5");
            try {
              lotEditor.values();
              throw new AssertionError("Decimal quantity silently truncated");
            } catch (IllegalArgumentException expected) {
              checks++;
            }

            var saleLine = new RecordEditor(Schema.table("sale_lines"), null, service);
            check(!saleLine.values().get(4).isBlank(), "New sale line suggests catalog price");
          });
    }
    try (var dao = new TextDatabase(Files.createTempDirectory("pharmacy-empty-editor-"))) {
      var empty = new PharmacyService(dao);
      SwingUtilities.invokeAndWait(
          () -> {
            var editor = new RecordEditor(Schema.table("lots"), null, empty);
            try {
              editor.values();
              throw new AssertionError("Missing reference accepted");
            } catch (IllegalArgumentException expected) {
              check(
                  expected.getMessage().contains("Hãy tạo danh mục"),
                  "Missing FK explains prerequisite");
            }
          });
    }
    System.out.println("PASS: " + checks + " RecordEditor field-binding checks");
  }
}
