package vn.pharmacy;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import vn.pharmacy.dto.InvoiceLine;
import vn.pharmacy.dto.InvoiceReport;
import vn.pharmacy.gui.InvoicePrinter;

/** Report rendering checks run without a desktop or physical printer. */
public final class InvoicePrintTest {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  private static InvoiceReport report(List<InvoiceLine> lines) {
    BigDecimal total =
        lines.stream().map(InvoiceLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new InvoiceReport(
        "HD20261115",
        LocalDate.of(2026, 11, 15),
        "Nguyễn Minh Anh",
        "123 Nguyễn Trãi, TP. Hồ Chí Minh",
        "0901234567",
        "Trần Thị Bình",
        "DT20261115",
        lines,
        total);
  }

  private static InvoiceLine line(String id, String name) {
    return new InvoiceLine(
        "T" + id,
        name,
        "Hộp",
        "LO_" + id,
        LocalDate.of(2028, 12, 31),
        2,
        new BigDecimal("180000"),
        new BigDecimal("360000"));
  }

  private static void marginsWhite(BufferedImage image, PageFormat format) {
    int left = (int) format.getImageableX() - 2;
    int top = (int) format.getImageableY() - 2;
    int right = (int) (format.getImageableX() + format.getImageableWidth()) + 2;
    int bottom = (int) (format.getImageableY() + format.getImageableHeight()) + 2;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        if (x < left || x > right || y < top || y > bottom) {
          if ((image.getRGB(x, y) & 0xffffff) != 0xffffff)
            throw new AssertionError("Report paints outside printable bounds at " + x + "," + y);
        }
      }
    checks++;
  }

  public static void main(String[] args) throws Exception {
    var format = InvoicePrinter.a4();
    var simple =
        new InvoicePrinter(
            report(
                List.of(
                    line("01", "Panadol Extra"),
                    line("02", "Augmentin 625mg — dùng theo đơn bác sĩ"))));
    check(simple.pageCount(format) == 1, "Small invoice should fit one A4 page");
    var image = simple.renderPage(format, 0, 1);
    marginsWhite(image, format);
    if (args.length > 0) ImageIO.write(image, "png", Path.of(args[0]).toFile());
    var many = new ArrayList<InvoiceLine>();
    for (int i = 0; i < 80; i++)
      many.add(
          line(
              String.format("%03d", i),
              "Thuốc số "
                  + i
                  + " có tên dài để kiểm tra xuống dòng khi in nhiều trang, không làm mất thông tin"
                  + " lô và hạn dùng"));
    // An unbroken token larger than a single page must wrap and continue on the next page.
    many.add(line("LONG", "X".repeat(2000)));
    var large = new InvoicePrinter(report(many));
    int pages = large.pageCount(format);
    check(pages > 2 && pages < 40, "Long invoice should paginate sensibly");
    for (int i = 0; i < pages; i++) {
      BufferedImage page = large.renderPage(format, i, 1);
      marginsWhite(page, format);
    }
    var graphics = image.createGraphics();
    check(
        large.print(graphics, format, pages) == Printable.NO_SUCH_PAGE,
        "Printing must stop after final page");
    check(
        large.print(graphics, format, -1) == Printable.NO_SUCH_PAGE,
        "Negative page does not exist");
    graphics.dispose();
    var empty = new InvoicePrinter(report(List.of()));
    check(empty.pageCount(format) == 1, "An empty draft still has an informative report");
    var small = new PageFormat();
    var paper = new Paper();
    paper.setSize(200, 200);
    paper.setImageableArea(10, 10, 180, 180);
    small.setPaper(paper);
    try {
      simple.pageCount(small);
      throw new AssertionError("Unsupported paper should give a useful error");
    } catch (PrinterException expected) {
      checks++;
    }
    System.out.println(
        "PASS: " + checks + " invoice pagination/rendering checks (" + pages + " pages)");
  }
}
