package vn.pharmacy.dao;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import vn.pharmacy.dto.Schema;

/** Real OOXML .xlsx, inline/shared strings. No external runtime libraries. */
public final class ExcelFile {
  private static String xml(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static void entry(ZipOutputStream z, String name, String text) throws IOException {
    z.putNextEntry(new ZipEntry(name));
    z.write(text.getBytes(StandardCharsets.UTF_8));
    z.closeEntry();
  }

  private static String col(int n) {
    String s = "";
    for (n++; n > 0; n = (n - 1) / 26) s = (char) ('A' + (n - 1) % 26) + s;
    return s;
  }

  public static void write(Path path, Map<String, List<List<String>>> db) throws IOException {
    Path abs = path.toAbsolutePath(),
        temp = Files.createTempFile(abs.getParent(), "excel-", ".tmp");
    try {
      try (var z = new ZipOutputStream(Files.newOutputStream(temp))) {
        StringBuilder types =
            new StringBuilder(
                "<?xml version=\"1.0\"?><Types"
                    + " xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
                    + " Extension=\"rels\""
                    + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
                    + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
                    + " PartName=\"/xl/workbook.xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        StringBuilder
            wb =
                new StringBuilder(
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                        + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"),
            rels =
                new StringBuilder(
                    "<Relationships"
                        + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        int i = 0;
        for (var t : Schema.TABLES) {
          i++;
          types.append(
              "<Override PartName=\"/xl/worksheets/sheet"
                  + i
                  + ".xml\""
                  + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
          wb.append(
              "<sheet name=\"" + t.key() + "\" sheetId=\"" + i + "\" r:id=\"rId" + i + "\"/>");
          rels.append(
              "<Relationship Id=\"rId"
                  + i
                  + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
                  + " Target=\"worksheets/sheet"
                  + i
                  + ".xml\"/>");
          var sheet =
              new StringBuilder(
                  "<worksheet"
                      + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
          var rows = new ArrayList<List<String>>();
          rows.add(Arrays.asList(t.columns()));
          rows.addAll(db.get(t.key()));
          int ri = 0;
          for (var row : rows) {
            ri++;
            sheet.append("<row r=\"" + ri + "\">");
            for (int c = 0; c < row.size(); c++)
              sheet.append(
                  "<c r=\""
                      + col(c)
                      + ri
                      + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                      + xml(row.get(c))
                      + "</t></is></c>");
            sheet.append("</row>");
          }
          sheet.append("</sheetData></worksheet>");
          entry(z, "xl/worksheets/sheet" + i + ".xml", sheet.toString());
        }
        entry(z, "[Content_Types].xml", types + "</Types>");
        entry(z, "xl/workbook.xml", wb + "</sheets></workbook>");
        entry(z, "xl/_rels/workbook.xml.rels", rels + "</Relationships>");
        entry(
            z,
            "_rels/.rels",
            "<Relationships"
                + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
                + " Id=\"rId1\""
                + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                + " Target=\"xl/workbook.xml\"/></Relationships>");
      }
      Files.move(temp, abs, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static Document parse(ZipFile z, String path) throws Exception {
    var e = z.getEntry(path);
    if (e == null) throw new IOException("Thiếu thành phần Excel: " + path);
    try (var in = z.getInputStream(e)) {
      byte[] bytes = in.readNBytes(8 * 1024 * 1024 + 1);
      if (bytes.length > 8 * 1024 * 1024) throw new IOException("Sheet quá lớn (tối đa 8 MB)");
      var f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
  }

  private static String text(Element e, String tag) {
    NodeList n = e.getElementsByTagName(tag);
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < n.getLength(); i++) s.append(n.item(i).getTextContent());
    return s.toString();
  }

  public static Map<String, List<List<String>>> read(Path path) throws IOException {
    try (var z = new ZipFile(path.toFile())) {
      if (Files.size(path) > 20 * 1024 * 1024) throw new IOException("Excel tối đa 20 MB");
      var db = Schema.empty();
      var shared = new ArrayList<String>();
      if (z.getEntry("xl/sharedStrings.xml") != null) {
        var nodes = parse(z, "xl/sharedStrings.xml").getElementsByTagName("si");
        for (int i = 0; i < nodes.getLength(); i++) shared.add(text((Element) nodes.item(i), "t"));
      }
      var rels = new HashMap<String, String>();
      var rr = parse(z, "xl/_rels/workbook.xml.rels").getElementsByTagName("Relationship");
      for (int i = 0; i < rr.getLength(); i++) {
        var e = (Element) rr.item(i);
        String target = e.getAttribute("Target");
        String name = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        rels.put(e.getAttribute("Id"), Path.of(name).normalize().toString().replace('\\', '/'));
      }
      var sheets = parse(z, "xl/workbook.xml").getElementsByTagName("sheet");
      var seen = new HashSet<String>();
      for (int i = 0; i < sheets.getLength(); i++) {
        var sh = (Element) sheets.item(i);
        String key = sh.getAttribute("name");
        var spec = Schema.table(key);
        if (!seen.add(key)) throw new IOException("Sheet trùng tên");
        var rows = parse(z, rels.get(sh.getAttribute("r:id"))).getElementsByTagName("row");
        boolean header = false;
        for (int n = 0; n < rows.getLength(); n++) {
          var cells = ((Element) rows.item(n)).getElementsByTagName("c");
          var row = new ArrayList<>(Collections.nCopies(spec.columns().length, ""));
          for (int j = 0; j < cells.getLength(); j++) {
            var cell = (Element) cells.item(j);
            if (cell.getElementsByTagName("f").getLength() > 0)
              throw new IOException("Không nhận công thức Excel; hãy dán giá trị");
            String addr = cell.getAttribute("r");
            int c = 0;
            for (char ch : addr.toCharArray()) {
              if (ch < 'A' || ch > 'Z') break;
              c = c * 26 + ch - 'A' + 1;
            }
            c--;
            if (c < 0 || c >= row.size()) throw new IOException("Sai cột Excel " + addr);
            String type = cell.getAttribute("t");
            String v = type.equals("inlineStr") ? text(cell, "t") : text(cell, "v");
            if (type.equals("s")) v = shared.get(Integer.parseInt(v));
            row.set(c, v);
          }
          if (row.stream().allMatch(String::isBlank)) continue;
          if (!header) {
            if (!row.equals(Arrays.asList(spec.columns())))
              throw new IOException("Sai tiêu đề sheet " + key);
            header = true;
          } else {
            db.get(key).add(row);
            if (db.get(key).size() > 10000) throw new IOException("Tối đa 10.000 dòng/sheet");
          }
        }
        if (!header) throw new IOException("Sheet thiếu tiêu đề: " + key);
      }
      if (seen.size() != Schema.TABLES.size())
        throw new IOException("File cần đủ 9 sheet; dùng Export để lấy mẫu");
      return db;
    } catch (Exception e) {
      throw new IOException("Không đọc được Excel: " + e.getMessage(), e);
    }
  }

  private ExcelFile() {}
}
