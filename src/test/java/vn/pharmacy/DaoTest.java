package vn.pharmacy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.zip.*;
import vn.pharmacy.dao.*;
import vn.pharmacy.dto.Schema;

/** Boundary and no-data-loss checks for TXT and real OOXML files. */
public final class DaoTest {
  private static int checks;

  @FunctionalInterface
  interface Action {
    void run() throws Exception;
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static void rejects(Action action, String message) throws Exception {
    try {
      action.run();
    } catch (IOException e) {
      checks++;
      return;
    }
    throw new AssertionError("Expected rejection: " + message);
  }

  private static String entry(Path file, String name) throws Exception {
    try (var zip = new ZipFile(file.toFile())) {
      return new String(
          zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Path mutate(Path source, String name, UnaryOperator<String> edit)
      throws Exception {
    Path target = Files.createTempFile(source.getParent(), "mutated-", ".xlsx");
    try (var input = new ZipFile(source.toFile());
        var output = new ZipOutputStream(Files.newOutputStream(target))) {
      for (var e : Collections.list(input.entries())) {
        byte[] bytes = input.getInputStream(e).readAllBytes();
        if (e.getName().equals(name))
          bytes =
              edit.apply(new String(bytes, StandardCharsets.UTF_8))
                  .getBytes(StandardCharsets.UTF_8);
        output.putNextEntry(new ZipEntry(e.getName()));
        output.write(bytes);
        output.closeEntry();
      }
    }
    return target;
  }

  private static Path dateCell(Path source, String value) throws Exception {
    Path styled =
        mutate(
            source,
            "xl/styles.xml",
            s ->
                s.replace("<cellXfs count=\"3\">", "<cellXfs count=\"4\">")
                    .replace(
                        "</cellXfs>",
                        "<xf numFmtId=\"14\" fontId=\"0\" fillId=\"0\" borderId=\"0\""
                            + " xfId=\"0\"/></cellXfs>"));
    return mutate(
        styled,
        "xl/worksheets/sheet1.xml",
        s ->
            s.replaceAll(
                "<c r=\"E2\"[^>]*>.*?</c>", "<c r=\"E2\" s=\"3\"><v>" + value + "</v></c>"));
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("pharmacy-dao-");
    var db = Schema.empty();
    db.get("employees")
        .add(
            new ArrayList<>(
                List.of(
                    "NV1",
                    "Tên\r\nngười\t dùng",
                    "HCM",
                    "Nam",
                    "1995-01-01",
                    "0901234567",
                    "2022-01-01",
                    "a@example.com",
                    "10000")));
    db.get("medicines")
        .add(
            new ArrayList<>(
                List.of("T1", "Thuốc & <mẫu>", "Hộp", "10000", "false", "A", "Hướng dẫn")));
    db.get("lots").add(new ArrayList<>(List.of("L1", "T1", "2099-01-01", "4")));
    Path excel = dir.resolve("valid.xlsx");
    ExcelFile.write(excel, db);
    check(ExcelFile.read(excel).equals(db), "Excel strings with CR/LF/TAB and XML escaping");
    check(entry(excel, "xl/styles.xml").contains("numFmtId=\"49\""), "text-preserving styles");
    check(
        entry(excel, "xl/worksheets/sheet1.xml").contains("state=\"frozen\"")
            && entry(excel, "xl/worksheets/sheet1.xml").contains("autoFilter"),
        "header freeze and filter");
    byte[] original = Files.readAllBytes(excel);
    var bad = Schema.empty();
    db.forEach((key, rows) -> rows.forEach(row -> bad.get(key).add(new ArrayList<>(row))));
    bad.get("medicines").get(0).set(1, "Invalid\u0001XML");
    rejects(() -> ExcelFile.write(excel, bad), "unsupported XML character");
    check(
        Arrays.equals(original, Files.readAllBytes(excel)), "failed export preserves old workbook");
    Path bool =
        mutate(
            excel,
            "xl/worksheets/sheet4.xml",
            s -> s.replaceAll("<c r=\"E2\"[^>]*>.*?</c>", "<c r=\"E2\" t=\"b\"><v>1</v></c>"));
    check(
        ExcelFile.read(bool).get("medicines").get(0).get(4).equals("true"),
        "Excel boolean conversion");
    Path numeric =
        mutate(
            excel,
            "xl/worksheets/sheet5.xml",
            s -> s.replaceAll("<c r=\"D2\"[^>]*>.*?</c>", "<c r=\"D2\"><v>4.0</v></c>"));
    check(ExcelFile.read(numeric).get("lots").get(0).get(3).equals("4"), "integer numeric cell");
    check(
        ExcelFile.read(dateCell(excel, "61")).get("employees").get(0).get(4).equals("1900-03-01"),
        "Excel 1900 leap bug offset");
    Path epoch1904 =
        mutate(
            dateCell(excel, "0"),
            "xl/workbook.xml",
            s -> s.replace("<sheets>", "<workbookPr date1904=\"1\"/><sheets>"));
    check(
        ExcelFile.read(epoch1904).get("employees").get(0).get(4).equals("1904-01-01"),
        "Excel 1904 epoch");
    rejects(() -> ExcelFile.read(dateCell(excel, "60")), "fictional 1900 leap day");
    rejects(() -> ExcelFile.read(dateCell(excel, "61.5")), "dates containing time");
    Path isoDate =
        mutate(
            excel,
            "xl/worksheets/sheet1.xml",
            value ->
                value.replaceAll(
                    "<c r=\"E2\"[^>]*>.*?</c>",
                    "<c r=\"E2\" t=\"d\"><v>1995-01-01T00:00:00Z</v></c>"));
    check(
        ExcelFile.read(isoDate).get("employees").get(0).get(4).equals("1995-01-01"),
        "ISO OOXML date with UTC suffix");
    Path withTime =
        mutate(
            isoDate, "xl/worksheets/sheet1.xml", value -> value.replace("00:00:00Z", "12:00:00Z"));
    rejects(() -> ExcelFile.read(withTime), "ISO date cannot silently discard time");
    Path dup =
        mutate(
            excel,
            "xl/worksheets/sheet1.xml",
            s ->
                s.replace(
                    "<row r=\"2\">",
                    "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>OTHER</t></is></c>"));
    rejects(() -> ExcelFile.read(dup), "duplicate cell address");
    Path merged =
        mutate(
            excel,
            "xl/worksheets/sheet1.xml",
            s ->
                s.replace(
                    "</worksheet>",
                    "<mergeCells count=\"1\"><mergeCell ref=\"A2:B2\"/></mergeCells></worksheet>"));
    rejects(() -> ExcelFile.read(merged), "merged cells");
    Path formula =
        mutate(
            excel,
            "xl/worksheets/sheet1.xml",
            s -> s.replace("<row r=\"2\">", "<row r=\"2\"><c r=\"J2\"><f>1+1</f><v>2</v></c>"));
    rejects(() -> ExcelFile.read(formula), "formula rejected instead of cached value");
    Path doctype =
        mutate(
            excel,
            "xl/workbook.xml",
            s -> "<!DOCTYPE workbook [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>" + s);
    rejects(() -> ExcelFile.read(doctype), "external entity/DTD");
    try (var dao = new TextDatabase(dir.resolve("txt"))) {
      dao.write(db);
      check(dao.read().equals(db), "TXT roundtrip");
      var txt = dir.resolve("txt/store.txt");
      byte[] snapshot = Files.readAllBytes(txt);
      var malformed = Schema.empty();
      malformed.get("employees").add(List.of("SHORT"));
      rejects(() -> dao.write(malformed), "invalid TXT row shape");
      check(
          Arrays.equals(snapshot, Files.readAllBytes(txt)),
          "failed TXT write preserves old snapshot");
      String valid = Files.readString(txt);
      Files.writeString(
          txt, valid.replace("TlYx", Base64.getEncoder().encodeToString(new byte[] {(byte) 0xc3})));
      rejects(dao::read, "malformed UTF8 decoded field");
      Files.write(txt, snapshot);
      dao.close();
      dao.close();
      rejects(dao::read, "read after close");
      try (var reopened = new TextDatabase(dir.resolve("txt"))) {
        check(reopened.read().equals(db), "idempotent close releases lock");
      }
    }
    check(
        Files.list(dir).noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
        "no abandoned export temp file");
    System.out.println("PASS: " + checks + " DAO boundary/interoperability checks");
  }
}
