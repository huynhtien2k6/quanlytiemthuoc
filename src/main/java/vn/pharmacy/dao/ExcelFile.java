package vn.pharmacy.dao;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import vn.pharmacy.dto.Schema;

/** Reads/writes real OOXML workbooks. Data is returned to BUS for relational validation. */
public final class ExcelFile {
  private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String REL =
      "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final int MAX_XML_BYTES = 8 * 1024 * 1024;

  private static String xml(String s) throws IOException {
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (!(cp == 9
          || cp == 10
          || cp == 13
          || cp >= 0x20 && cp <= 0xD7FF
          || cp >= 0xE000 && cp <= 0xFFFD
          || cp >= 0x10000 && cp <= 0x10FFFF))
        throw new IOException(
            "Dữ liệu chứa ký tự không được Excel/XML hỗ trợ (U+" + Integer.toHexString(cp) + ")");
      i += Character.charCount(cp);
    }
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\r", "&#13;");
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

  /** All data cells are text, preserving IDs, phone zeros and ISO dates when edited. */
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
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override"
                    + " PartName=\"/xl/styles.xml\""
                    + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        StringBuilder wb =
            new StringBuilder("<workbook xmlns=\"" + NS + "\" xmlns:r=\"" + REL + "\"><sheets>");
        StringBuilder rels =
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
                  + "\" Type=\""
                  + REL
                  + "/worksheet\" Target=\"worksheets/sheet"
                  + i
                  + ".xml\"/>");
          var sheet =
              new StringBuilder(
                  "<worksheet xmlns=\""
                      + NS
                      + "\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\""
                      + " topLeftCell=\"A2\" activePane=\"bottomLeft\""
                      + " state=\"frozen\"/></sheetView></sheetViews><cols>");
          for (int c = 0; c < t.columns().length; c++)
            sheet.append(
                "<col min=\""
                    + (c + 1)
                    + "\" max=\""
                    + (c + 1)
                    + "\" width=\""
                    + (c == 0 ? 20 : 27)
                    + "\" customWidth=\"1\" style=\"2\"/>");
          sheet.append("</cols><sheetData>");
          var rows = new ArrayList<List<String>>();
          rows.add(Arrays.asList(t.columns()));
          rows.addAll(db.get(t.key()));
          int ri = 0;
          for (var row : rows) {
            ri++;
            sheet.append(
                "<row r=\"" + ri + "\"" + (ri == 1 ? " ht=\"26\" customHeight=\"1\"" : "") + ">");
            for (int c = 0; c < row.size(); c++)
              sheet.append(
                  "<c r=\""
                      + col(c)
                      + ri
                      + "\" s=\""
                      + (ri == 1 ? 1 : 2)
                      + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                      + xml(row.get(c))
                      + "</t></is></c>");
            sheet.append("</row>");
          }
          sheet.append(
              "</sheetData><autoFilter ref=\"A1:"
                  + col(t.columns().length - 1)
                  + ri
                  + "\"/></worksheet>");
          entry(z, "xl/worksheets/sheet" + i + ".xml", sheet.toString());
        }
        rels.append(
            "<Relationship Id=\"styles\" Type=\"" + REL + "/styles\" Target=\"styles.xml\"/>");
        entry(z, "[Content_Types].xml", types + "</Types>");
        entry(z, "xl/workbook.xml", wb + "</sheets></workbook>");
        entry(z, "xl/_rels/workbook.xml.rels", rels + "</Relationships>");
        entry(
            z,
            "xl/styles.xml",
            "<styleSheet xmlns=\""
                + NS
                + "\"><fonts count=\"2\"><font><sz val=\"11\"/><name"
                + " val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color"
                + " rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts><fills"
                + " count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill"
                + " patternType=\"gray125\"/></fill><fill><patternFill"
                + " patternType=\"solid\"><fgColor rgb=\"FF14555C\"/><bgColor"
                + " indexed=\"64\"/></patternFill></fill></fills><borders"
                + " count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\""
                + " fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs"
                + " count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\""
                + " xfId=\"0\"/><xf numFmtId=\"49\" fontId=\"1\" fillId=\"2\" borderId=\"0\""
                + " xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyNumberFormat=\"1\"/><xf"
                + " numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\""
                + " applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle"
                + " name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
        entry(
            z,
            "_rels/.rels",
            "<Relationships"
                + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
                + " Id=\"rId1\" Type=\""
                + REL
                + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
      }
      Files.move(temp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static NodeList nodes(Element e, String tag) {
    return e.getElementsByTagNameNS("*", tag);
  }

  private static NodeList nodes(Document d, String tag) {
    return d.getElementsByTagNameNS("*", tag);
  }

  private static Document parse(ZipFile z, String path) throws Exception {
    if (path == null) throw new IOException("Sheet không có quan hệ worksheet hợp lệ");
    var e = z.getEntry(path);
    if (e == null) throw new IOException("Thiếu thành phần Excel: " + path);
    try (var in = z.getInputStream(e)) {
      byte[] bytes = in.readNBytes(MAX_XML_BYTES + 1);
      if (bytes.length > MAX_XML_BYTES) throw new IOException("XML quá lớn (tối đa 8 MB)");
      var f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      var builder = f.newDocumentBuilder();
      builder.setErrorHandler(
          new ErrorHandler() {
            public void warning(SAXParseException e) throws SAXParseException {
              throw e;
            }

            public void error(SAXParseException e) throws SAXParseException {
              throw e;
            }

            public void fatalError(SAXParseException e) throws SAXParseException {
              throw e;
            }
          });
      return builder.parse(new ByteArrayInputStream(bytes));
    }
  }

  private static String text(Element e, String tag) {
    NodeList n = nodes(e, tag);
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < n.getLength(); i++) {
      // Phonetic guides are pronunciation hints, not part of the cell value.
      Node parent = n.item(i).getParentNode();
      if (parent instanceof Element pe && pe.getLocalName().equals("rPh")) continue;
      s.append(n.item(i).getTextContent());
    }
    return s.toString();
  }

  private static boolean dateColumn(String table, int c) {
    return table.equals("employees") && (c == 4 || c == 6)
        || table.equals("customers") && c == 4
        || table.equals("lots") && c == 2
        || (table.equals("sales") || table.equals("purchases")) && c == 1;
  }

  private static Set<Integer> dateStyles(ZipFile z) throws Exception {
    var out = new HashSet<Integer>();
    if (z.getEntry("xl/styles.xml") == null) return out;
    var d = parse(z, "xl/styles.xml");
    var formats = new HashMap<Integer, String>();
    var custom = nodes(d, "numFmt");
    for (int i = 0; i < custom.getLength(); i++) {
      var e = (Element) custom.item(i);
      formats.put(Integer.parseInt(e.getAttribute("numFmtId")), e.getAttribute("formatCode"));
    }
    var groups = nodes(d, "cellXfs");
    if (groups.getLength() == 0) return out;
    var xfs = nodes((Element) groups.item(0), "xf");
    for (int i = 0; i < xfs.getLength(); i++) {
      int id = Integer.parseInt(((Element) xfs.item(i)).getAttribute("numFmtId"));
      String fmt =
          formats
              .getOrDefault(id, "")
              .replaceAll("\"[^\"]*\"|\\\\.|\\[[^]]*]", "")
              .toLowerCase(Locale.ROOT);
      if (id >= 14 && id <= 22
          || id >= 27 && id <= 36
          || id >= 50 && id <= 58
          || fmt.matches(".*[yd].*")) out.add(i);
    }
    return out;
  }

  private static String excelDate(String value, boolean date1904) throws IOException {
    try {
      long serial = new BigDecimal(value).longValueExact();
      if (serial < 0 || serial > (date1904 ? 2957003 : 2958465) || !date1904 && serial == 60)
        throw new ArithmeticException();
      return (date1904
              ? LocalDate.of(1904, 1, 1).plusDays(serial)
              : LocalDate.of(1899, 12, 31).plusDays(serial > 60 ? serial - 1 : serial))
          .toString();
    } catch (RuntimeException e) {
      throw new IOException("Ngày Excel không hợp lệ hoặc chứa giờ: " + value, e);
    }
  }

  public static Map<String, List<List<String>>> read(Path path) throws IOException {
    try (var z = new ZipFile(path.toFile())) {
      if (Files.size(path) > 20 * 1024 * 1024) throw new IOException("Excel tối đa 20 MB");
      var db = Schema.empty();
      var shared = new ArrayList<String>();
      if (z.getEntry("xl/sharedStrings.xml") != null) {
        var list = nodes(parse(z, "xl/sharedStrings.xml"), "si");
        for (int i = 0; i < list.getLength(); i++) shared.add(text((Element) list.item(i), "t"));
      }
      var rels = new HashMap<String, String>();
      var rr = nodes(parse(z, "xl/_rels/workbook.xml.rels"), "Relationship");
      for (int i = 0; i < rr.getLength(); i++) {
        var e = (Element) rr.item(i);
        if (!e.getAttribute("Type").equals(REL + "/worksheet")) continue;
        if (e.getAttribute("TargetMode").equals("External"))
          throw new IOException("Không nhận sheet liên kết bên ngoài");
        String target = e.getAttribute("Target");
        String name = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        name = Path.of(name).normalize().toString().replace('\\', '/');
        if (!name.startsWith("xl/") || rels.put(e.getAttribute("Id"), name) != null)
          throw new IOException("Quan hệ worksheet không hợp lệ");
      }
      var workbook = parse(z, "xl/workbook.xml");
      var props = nodes(workbook, "workbookPr");
      boolean date1904 =
          props.getLength() > 0
              && Set.of("1", "true").contains(((Element) props.item(0)).getAttribute("date1904"));
      var dates = dateStyles(z);
      var sheets = nodes(workbook, "sheet");
      var seen = new HashSet<String>();
      for (int i = 0; i < sheets.getLength(); i++) {
        var sh = (Element) sheets.item(i);
        String key = sh.getAttribute("name");
        var spec = Schema.table(key);
        if (!seen.add(key)) throw new IOException("Sheet trùng tên: " + key);
        var sheet = parse(z, rels.get(sh.getAttributeNS(REL, "id")));
        if (nodes(sheet, "mergeCell").getLength() > 0)
          throw new IOException("Không nhận ô gộp: " + key);
        var rows = nodes(sheet, "row");
        boolean header = false;
        var usedRows = new HashSet<Integer>();
        int previousRow = 0;
        for (int n = 0; n < rows.getLength(); n++) {
          var rowElement = (Element) rows.item(n);
          int rowIndex =
              rowElement.hasAttribute("r")
                  ? Integer.parseInt(rowElement.getAttribute("r"))
                  : previousRow + 1;
          if (rowIndex < 1 || !usedRows.add(rowIndex))
            throw new IOException("Chỉ số dòng trùng/sai ở " + key);
          previousRow = rowIndex;
          var cells = nodes(rowElement, "c");
          var row = new ArrayList<>(Collections.nCopies(spec.columns().length, ""));
          var usedCells = new HashSet<Integer>();
          for (int j = 0; j < cells.getLength(); j++) {
            var cell = (Element) cells.item(j);
            if (nodes(cell, "f").getLength() > 0)
              throw new IOException("Không nhận công thức ở " + key + ": hãy dán giá trị");
            String addr = cell.getAttribute("r");
            if (!addr.matches("[A-Z]{1,3}[1-9][0-9]*"))
              throw new IOException("Địa chỉ ô sai: " + addr);
            int c = 0, pos = 0;
            while (pos < addr.length() && Character.isLetter(addr.charAt(pos)))
              c = c * 26 + addr.charAt(pos++) - 'A' + 1;
            c--;
            if (c < 0
                || c >= row.size()
                || Integer.parseInt(addr.substring(pos)) != rowIndex
                || !usedCells.add(c))
              throw new IOException("Ô sai cột/dòng hoặc trùng địa chỉ: " + key + "!" + addr);
            String type = cell.getAttribute("t");
            String v = type.equals("inlineStr") ? text(cell, "t") : text(cell, "v");
            if (type.equals("s")) v = shared.get(Integer.parseInt(v));
            else if (type.equals("b")) {
              if (!Set.of("0", "1").contains(v))
                throw new IOException("Boolean không hợp lệ tại " + addr);
              v = v.equals("1") ? "true" : "false";
            } else if (type.equals("e"))
              throw new IOException("Ô Excel đang có lỗi: " + key + "!" + addr);
            else if (type.equals("d")) {
              if (!dateColumn(key, c)) throw new IOException("Ô ngày sai cột: " + addr);
              if (v.length() == 10) v = LocalDate.parse(v).toString();
              else {
                var parsed = DateTimeFormatter.ISO_DATE_TIME.parse(v);
                if (!LocalTime.from(parsed).equals(LocalTime.MIDNIGHT))
                  throw new IOException("Ngày chứa giờ tại " + addr);
                v = LocalDate.from(parsed).toString();
              }
            } else if ((type.isEmpty() || type.equals("n")) && !v.isEmpty()) {
              if (header && dateColumn(key, c)) {
                int style = cell.hasAttribute("s") ? Integer.parseInt(cell.getAttribute("s")) : 0;
                if (!dates.contains(style))
                  throw new IOException(
                      "Ngày dạng số phải có định dạng Date, hoặc nhập Text yyyy-MM-dd: "
                          + key
                          + "!"
                          + addr);
                v = excelDate(v, date1904);
              } else {
                var number = new BigDecimal(v).stripTrailingZeros();
                if (number.precision() + Math.abs((long) number.scale()) > 2000)
                  throw new IOException("Số quá lớn: " + addr);
                v = number.toPlainString();
              }
            } else if (!Set.of("s", "str", "inlineStr", "b", "d", "n", "").contains(type))
              throw new IOException("Kiểu ô không hỗ trợ: " + type);
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
        throw new IOException(
            "File cần đủ " + Schema.TABLES.size() + " sheet; dùng Export để lấy mẫu");
      return db;
    } catch (Exception e) {
      throw new IOException("Không đọc được Excel: " + e.getMessage(), e);
    }
  }

  private ExcelFile() {}
}
