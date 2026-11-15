package vn.pharmacy.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import vn.pharmacy.dto.InvoiceLine;
import vn.pharmacy.dto.InvoiceReport;

/** Presentation-only invoice report. All amounts and business data are supplied by BUS. */
public final class InvoicePrinter implements Printable {
  private static final Color TEAL = new Color(15, 85, 92);
  private static final Color PALE = new Color(240, 247, 247);
  private static final Color INK = new Color(34, 48, 55);
  private static final Font BODY = new Font("SansSerif", Font.PLAIN, 10);
  private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 9);
  private static final Font BOLD = new Font("SansSerif", Font.BOLD, 10);
  private static final float LINE = 15;
  private static final float HEADER_BOTTOM = 70;
  private static final float FOOTER_SPACE = 35;
  private final InvoiceReport report;
  private List<Page> cachedPages;
  private float cachedWidth, cachedHeight;

  public InvoicePrinter(InvoiceReport report) {
    this.report = java.util.Objects.requireNonNull(report);
  }

  /** Standard A4 with a 13 mm margin, also used by the preview. */
  public static PageFormat a4() {
    var paper = new Paper();
    paper.setSize(595.28, 841.89);
    paper.setImageableArea(36, 36, 523.28, 769.89);
    var format = new PageFormat();
    format.setPaper(paper);
    return format;
  }

  private static String money(BigDecimal value) {
    var symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"));
    return new DecimalFormat("#,##0.##", symbols).format(value);
  }

  private record Item(float y, String text, int row, boolean first, InvoiceLine line) {}

  private record Page(List<Item> items) {}

  private static final class Pagination {
    private final List<Page> pages = new ArrayList<>();
    private List<Item> current = new ArrayList<>();
    private final float bottom;
    private float y = HEADER_BOTTOM;
    private boolean tableStarted;

    Pagination(float height) {
      bottom = height - FOOTER_SPACE;
    }

    void room(float height) {
      if (y + height > bottom) {
        pages.add(new Page(List.copyOf(current)));
        current = new ArrayList<>();
        y = HEADER_BOTTOM;
        if (tableStarted) heading();
      }
    }

    private void heading() {
      current.add(new Item(y, "", -2, false, null));
      y += 27;
    }

    void startRows() {
      room(42);
      tableStarted = true;
      heading();
    }

    void text(String text) {
      room(LINE);
      current.add(new Item(y, text, -1, false, null));
      y += LINE;
    }

    void space(float height) {
      room(height);
      y += height;
    }

    void row(String text, int number, boolean first, InvoiceLine line) {
      room(LINE);
      current.add(new Item(y, text, number, first, line));
      y += LINE;
    }

    List<Page> finish() {
      pages.add(new Page(List.copyOf(current)));
      return pages;
    }
  }

  /** Wraps even unbroken IDs/medicine names, keeping every character printable. */
  private static List<String> wrap(String text, float width, FontMetrics fm) {
    var lines = new ArrayList<String>();
    for (String paragraph :
        text.replace('\t', ' ').replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      if (paragraph.isEmpty()) {
        lines.add("");
        continue;
      }
      int start = 0;
      while (start < paragraph.length()) {
        int end = start;
        int lastSpace = -1;
        while (end < paragraph.length()) {
          int next = paragraph.offsetByCodePoints(end, 1);
          if (fm.stringWidth(paragraph.substring(start, next)) > width && end > start) break;
          if (Character.isWhitespace(paragraph.charAt(end))) lastSpace = end;
          end = next;
        }
        if (end < paragraph.length() && lastSpace > start) end = lastSpace + 1;
        lines.add(paragraph.substring(start, end).stripTrailing());
        start = end;
      }
    }
    return lines;
  }

  private synchronized List<Page> layout(PageFormat format) throws PrinterException {
    float width = (float) format.getImageableWidth();
    float height = (float) format.getImageableHeight();
    if (width < 360 || height < 250)
      throw new PrinterException("Vùng in quá nhỏ. Hãy chọn A4 dọc với lề từ 10–15 mm.");
    if (cachedPages != null && width == cachedWidth && height == cachedHeight) return cachedPages;
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setFont(BODY);
    var fm = g.getFontMetrics();
    var flow = new Pagination(height);
    List<String> details =
        List.of(
            "Mã hóa đơn: " + report.id(),
            "Khách hàng: " + report.customerName(),
            "Địa chỉ: " + report.customerAddress(),
            "Điện thoại: " + report.customerPhone() + "     Nhân viên: " + report.employeeName(),
            "Mã đơn bác sĩ: "
                + (report.prescriptionRef().isBlank() ? "—" : report.prescriptionRef()));
    for (String detail : details) for (String line : wrap(detail, width - 12, fm)) flow.text(line);
    flow.space(8);
    flow.startRows();
    int index = 0;
    for (InvoiceLine line : report.lines()) {
      index++;
      var name = new ArrayList<>(wrap(line.medicineName(), width * .43f - 12, fm));
      name.addAll(
          wrap(
              "Lô: "
                  + line.lotId()
                  + " • HSD: "
                  + line.expiryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                  + " • ĐVT: "
                  + line.unit(),
              width * .43f - 12,
              fm));
      // A long medicine name may span pages. Numeric values appear only on its first fragment.
      boolean first = true;
      for (String text : name) {
        flow.row(text, index, first, line);
        first = false;
      }
      flow.space(5);
    }
    flow.space(10);
    flow.room(LINE * 4);
    flow.text("TỔNG THANH TOÁN (VND): " + money(report.total()));
    flow.text("Số dòng thuốc: " + report.lines().size());
    flow.text("Vui lòng kiểm tra thuốc, số lượng và hạn dùng trước khi rời quầy.");
    flow.text("Cảm ơn quý khách! Sử dụng thuốc theo hướng dẫn của nhân viên y tế.");
    g.dispose();
    cachedWidth = width;
    cachedHeight = height;
    cachedPages = flow.finish();
    return cachedPages;
  }

  public int pageCount(PageFormat format) throws PrinterException {
    return layout(format).size();
  }

  private static void fitRight(
      Graphics2D g, String text, float right, float baseline, float maxWidth, Font base) {
    Font font = base;
    while (g.getFontMetrics(font).stringWidth(text) > maxWidth && font.getSize2D() > 4)
      font = font.deriveFont(font.getSize2D() - .5f);
    g.setFont(font);
    g.drawString(text, right - g.getFontMetrics().stringWidth(text), baseline);
  }

  @Override
  public int print(Graphics graphics, PageFormat format, int pageIndex) throws PrinterException {
    var pages = layout(format);
    if (pageIndex < 0 || pageIndex >= pages.size()) return NO_SUCH_PAGE;
    var g = (Graphics2D) graphics.create();
    try {
      g.translate(format.getImageableX(), format.getImageableY());
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      float width = (float) format.getImageableWidth();
      float height = (float) format.getImageableHeight();
      g.setColor(TEAL);
      g.setFont(new Font("SansSerif", Font.BOLD, 23));
      g.drawString("AN TÂM", 0, 24);
      g.setFont(SMALL);
      g.drawString("NHÀ THUỐC • CHĂM SÓC SỨC KHỎE", 0, 42);
      fitRight(
          g, "HÓA ĐƠN BÁN THUỐC", width, 22, width * .55f, new Font("SansSerif", Font.BOLD, 14));
      fitRight(
          g,
          "Ngày " + report.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
          width,
          42,
          width * .55f,
          BODY);
      g.setStroke(new BasicStroke(1.5f));
      g.drawLine(0, 56, Math.round(width), 56);
      float[] x = {0, width * .05f, width * .48f, width * .56f, width * .75f, width};
      for (Item item : pages.get(pageIndex).items()) {
        float baseline = item.y() + 11;
        if (item.row() == -2) {
          g.setColor(TEAL);
          g.fillRect(0, Math.round(item.y()), Math.round(width), 22);
          g.setColor(Color.WHITE);
          g.setFont(BOLD);
          g.drawString("STT", 4, item.y() + 15);
          g.drawString("THUỐC / THÔNG TIN LÔ", x[1] + 5, item.y() + 15);
          fitRight(g, "SL", x[3] - 5, item.y() + 15, x[3] - x[2] - 8, BOLD);
          fitRight(g, "ĐƠN GIÁ", x[4] - 5, item.y() + 15, x[4] - x[3] - 8, BOLD);
          fitRight(g, "THÀNH TIỀN", x[5] - 5, item.y() + 15, x[5] - x[4] - 8, BOLD);
          continue;
        }
        if (item.row() < 0) {
          g.setColor(INK);
          g.setFont(item.text().startsWith("TỔNG THANH TOÁN") ? BOLD : BODY);
          if (item.text().startsWith("TỔNG THANH TOÁN")) {
            g.setColor(PALE);
            g.fillRect(0, Math.round(item.y() - 1), Math.round(width), 16);
            g.setColor(TEAL);
          }
          fitRightOrLeft(g, item.text(), 4, baseline, width - 8);
          continue;
        }
        g.setColor(item.row() % 2 == 1 ? PALE : Color.WHITE);
        g.fillRect(0, Math.round(item.y() - 1), Math.round(width), 16);
        g.setColor(INK);
        g.setFont(BODY);
        g.drawString(item.text(), x[1] + 5, baseline);
        if (item.first()) {
          fitRight(g, Integer.toString(item.row()), x[1] - 4, baseline, x[1] - 8, BODY);
          fitRight(
              g,
              Integer.toString(item.line().quantity()),
              x[3] - 5,
              baseline,
              x[3] - x[2] - 8,
              BODY);
          fitRight(g, money(item.line().unitPrice()), x[4] - 5, baseline, x[4] - x[3] - 8, BODY);
          fitRight(g, money(item.line().amount()), x[5] - 5, baseline, x[5] - x[4] - 8, BODY);
        }
      }
      g.setColor(TEAL);
      g.setStroke(new BasicStroke(.6f));
      g.drawLine(0, Math.round(height - 22), Math.round(width), Math.round(height - 22));
      g.setFont(SMALL);
      g.drawString("AN TÂM • Hóa đơn bán hàng", 0, height - 7);
      fitRight(g, "Trang " + (pageIndex + 1) + " / " + pages.size(), width, height - 7, 120, SMALL);
      return PAGE_EXISTS;
    } finally {
      g.dispose();
    }
  }

  private static void fitRightOrLeft(
      Graphics2D g, String text, float left, float baseline, float width) {
    Font font = g.getFont();
    while (g.getFontMetrics(font).stringWidth(text) > width && font.getSize2D() > 4)
      font = font.deriveFont(font.getSize2D() - .5f);
    g.setFont(font);
    g.drawString(text, left, baseline);
  }

  public BufferedImage renderPage(PageFormat format, int pageIndex, double scale)
      throws PrinterException {
    if (!Double.isFinite(scale) || scale < .25 || scale > 3)
      throw new IllegalArgumentException("Tỷ lệ xem trước phải từ 0.25 đến 3");
    if (pageIndex < 0 || pageIndex >= pageCount(format))
      throw new IllegalArgumentException("Trang không tồn tại");
    var image =
        new BufferedImage(
            (int) Math.ceil(format.getWidth() * scale),
            (int) Math.ceil(format.getHeight() * scale),
            BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.scale(scale, scale);
    print(graphics, format, pageIndex);
    graphics.dispose();
    return image;
  }

  /** Read-only preview; printing uses the same Printable and pagination as the preview. */
  public void preview(Component parent) {
    var owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
    var dialog =
        new JDialog(
            owner, "Xem trước hóa đơn • " + report.id(), Dialog.ModalityType.APPLICATION_MODAL);
    var format = a4();
    var pageImage = new JLabel();
    pageImage.setHorizontalAlignment(SwingConstants.CENTER);
    pageImage.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    pageImage.setOpaque(true);
    pageImage.setBackground(new Color(224, 231, 234));
    final int count;
    try {
      count = pageCount(format);
    } catch (PrinterException ex) {
      JOptionPane.showMessageDialog(
          parent, ex.getMessage(), "Không tạo được báo cáo", JOptionPane.ERROR_MESSAGE);
      return;
    }
    var pageNumber = new JSpinner(new SpinnerNumberModel(1, 1, count, 1));
    Runnable showPage =
        () -> {
          try {
            pageImage.setIcon(
                new ImageIcon(renderPage(format, ((Integer) pageNumber.getValue()) - 1, 1)));
          } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(
                dialog, ex.getMessage(), "Không xem được trang", JOptionPane.ERROR_MESSAGE);
          }
        };
    pageNumber.addChangeListener(e -> showPage.run());
    showPage.run();
    var print = new JButton("In hóa đơn / Lưu PDF");
    print.addActionListener(
        e -> {
          var job = PrinterJob.getPrinterJob();
          job.setJobName("Hóa đơn " + report.id());
          job.setPrintable(this, format);
          if (job.printDialog()) {
            try {
              job.print();
            } catch (PrinterException ex) {
              JOptionPane.showMessageDialog(
                  dialog, ex.getMessage(), "Không in được", JOptionPane.ERROR_MESSAGE);
            }
          }
        });
    var close = new JButton("Đóng");
    close.addActionListener(e -> dialog.dispose());
    var tools = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    tools.add(new JLabel("Trang"));
    tools.add(pageNumber);
    tools.add(new JLabel("/ " + count + " • A4 • VND"));
    tools.add(print);
    tools.add(close);
    dialog.add(new JScrollPane(pageImage), BorderLayout.CENTER);
    dialog.add(tools, BorderLayout.SOUTH);
    dialog.setSize(720, 820);
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
  }
}
