package vn.pharmacy;

import java.nio.file.*;
import vn.pharmacy.bus.PharmacyService;
import vn.pharmacy.dao.*;

public final class ExcelCompatibilityTest {
  public static void main(String[] args) throws Exception {
    try (var dao = new TextDatabase(Path.of("data"))) {
      var s = new PharmacyService(dao);
      if (args[0].equals("export")) s.exportExcel(Path.of(args[1]));
      else if (!ExcelFile.read(Path.of(args[1])).equals(s.snapshot()))
        throw new AssertionError("Excel external roundtrip differs");
      System.out.println("PASS: Excel " + args[0]);
    }
  }
}
