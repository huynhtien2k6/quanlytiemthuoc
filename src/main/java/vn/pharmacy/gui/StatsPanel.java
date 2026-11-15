package vn.pharmacy.gui;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.*;
import vn.pharmacy.bus.PharmacyService;

/** Period controls, three reports and a lightweight revenue chart; calculations stay in BUS. */
public final class StatsPanel extends JPanel {
  private final PharmacyService service;
  private final JTextField from = new JTextField(10);
  private final JTextField to = new JTextField(10);
  private final JTable revenue = new JTable(), best = new JTable(), inventory = new JTable();
  private final JLabel revenueKpi = new JLabel(), countKpi = new JLabel(), qtyKpi = new JLabel();
  private final RevenueChart chart = new RevenueChart();
  private final JLabel periodLabel = new JLabel();
  private LocalDate appliedFrom, appliedTo;

  public StatsPanel(PharmacyService service) {
    super(new BorderLayout(10, 10));
    this.service = service;
    setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
    from.setName("stats.from");
    to.setName("stats.to");
    from.setToolTipText("yyyy-MM-dd hoặc để trống để lấy mọi ngày");
    to.setToolTipText(from.getToolTipText());
    var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(new JLabel("Từ ngày"));
    controls.add(from);
    controls.add(new JLabel("Đến ngày"));
    controls.add(to);
    action(controls, "Áp dụng", () -> apply(from.getText(), to.getText()));
    action(
        controls,
        "Tháng này",
        () -> apply(LocalDate.now().withDayOfMonth(1).toString(), LocalDate.now().toString()));
    action(controls, "Tất cả", () -> apply("", ""));
    controls.add(new JLabel("  yyyy-MM-dd"));
    var kpis = new JPanel(new GridLayout(1, 3, 12, 0));
    kpis.add(card("DOANH THU (VND)", revenueKpi));
    kpis.add(card("HÓA ĐƠN CÓ CHI TIẾT", countKpi));
    kpis.add(card("SỐ LƯỢNG BÁN", qtyKpi));
    var north = new JPanel(new BorderLayout(0, 8));
    north.add(controls, BorderLayout.NORTH);
    north.add(kpis, BorderLayout.CENTER);
    periodLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
    north.add(periodLabel, BorderLayout.SOUTH);
    add(north, BorderLayout.NORTH);
    var grid = new JPanel(new GridLayout(2, 2, 12, 12));
    grid.add(report("Doanh thu theo tháng (VND)", revenue, true));
    var chartBox = new JPanel(new BorderLayout());
    chartBox.setBorder(
        BorderFactory.createTitledBorder(
            "Biểu đồ doanh thu • tối đa 12 tháng gần nhất trong kết quả"));
    chartBox.add(chart);
    grid.add(chartBox);
    grid.add(report("Thuốc bán chạy", best, true));
    grid.add(report("Tồn kho hiện tại và hạn dùng (không lọc theo ngày)", inventory, false));
    add(grid, BorderLayout.CENTER);
  }

  private JPanel card(String title, JLabel value) {
    var p = new JPanel(new BorderLayout(0, 8));
    p.setBackground(Color.WHITE);
    p.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(214, 229, 232)),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)));
    var heading = new JLabel(title);
    heading.setForeground(new Color(80, 102, 110));
    value.setFont(value.getFont().deriveFont(Font.BOLD, 24f));
    value.setForeground(new Color(15, 85, 92));
    p.add(heading, BorderLayout.NORTH);
    p.add(value, BorderLayout.CENTER);
    return p;
  }

  private JPanel report(String title, JTable table, boolean filtered) {
    var p = new JPanel(new BorderLayout(0, 4));
    var tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
    tools.add(new JLabel(title));
    action(
        tools,
        "In",
        () -> {
          try {
            table.print(
                JTable.PrintMode.FIT_WIDTH,
                new java.text.MessageFormat(
                    (title + (filtered ? " • " + period() : "")).replace("'", "''")),
                new java.text.MessageFormat("Trang {0}"));
          } catch (Exception e) {
            error(e);
          }
        });
    p.add(tools, BorderLayout.NORTH);
    p.add(new JScrollPane(table), BorderLayout.CENTER);
    return p;
  }

  private String period() {
    return (appliedFrom == null ? "Từ đầu" : appliedFrom.toString())
        + " → "
        + (appliedTo == null ? "tất cả" : appliedTo.toString());
  }

  private static LocalDate parse(String value) {
    if (value.isBlank()) return null;
    if (!value.matches("\\d{4}-\\d{2}-\\d{2}"))
      throw new IllegalArgumentException("Ngày phải có định dạng yyyy-MM-dd");
    return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private void apply(String start, String end) {
    try {
      LocalDate f = parse(start.trim()), t = parse(end.trim());
      // Validate before changing the active period, so invalid controls never corrupt the report.
      service.summary(
          f == null ? LocalDate.of(1, 1, 1) : f, t == null ? LocalDate.of(9999, 12, 31) : t);
      appliedFrom = f;
      appliedTo = t;
      from.setText(start.trim());
      to.setText(end.trim());
      reload();
    } catch (Exception e) {
      error(e);
    }
  }

  private LocalDate rangeFrom() {
    return appliedFrom == null ? LocalDate.of(1, 1, 1) : appliedFrom;
  }

  private LocalDate rangeTo() {
    return appliedTo == null ? LocalDate.of(9999, 12, 31) : appliedTo;
  }

  public void reload() {
    var summary = service.summary(rangeFrom(), rangeTo());
    revenueKpi.setText(TableSupport.money(summary.revenue()));
    countKpi.setText(Long.toString(summary.invoiceCount()));
    qtyKpi.setText(Long.toString(summary.quantity()));
    var months = service.revenue(rangeFrom(), rangeTo());
    TableSupport.load(revenue, new String[] {"Tháng", "Doanh thu"}, months);
    TableSupport.load(
        best,
        new String[] {"Mã thuốc", "Tên thuốc", "Số lượng"},
        service.bestSellers(rangeFrom(), rangeTo()));
    TableSupport.load(
        inventory,
        new String[] {"Mã lô", "Tên thuốc", "Hạn dùng", "Tồn", "Trạng thái"},
        service.inventory());
    chart.setRows(months);
    periodLabel.setText(
        "Kỳ báo cáo: "
            + period()
            + " • Bộ lọc áp dụng cho doanh thu và lượng bán; tồn kho luôn là hiện tại.");
  }

  private void error(Exception e) {
    JOptionPane.showMessageDialog(
        this,
        "Không áp dụng được: " + e.getMessage(),
        "Kiểm tra bộ lọc",
        JOptionPane.ERROR_MESSAGE);
  }

  private static void action(JPanel p, String title, Runnable action) {
    var b = new JButton(title);
    b.addActionListener(e -> action.run());
    p.add(b);
  }

  static final class RevenueChart extends JPanel {
    private List<List<String>> rows = List.of();

    RevenueChart() {
      setBackground(Color.WHITE);
    }

    void setRows(List<List<String>> source) {
      rows = List.copyOf(source.subList(Math.max(0, source.size() - 12), source.size()));
      repaint();
    }

    @Override
    protected void paintComponent(Graphics original) {
      super.paintComponent(original);
      var g = (Graphics2D) original.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (rows.isEmpty()) {
          g.setColor(new Color(100, 115, 120));
          g.drawString("Chưa có doanh thu trong kỳ đã chọn", 20, 45);
          return;
        }
        int left = 64,
            top = 24,
            bottom = getHeight() - 42,
            width = getWidth() - left - 20,
            height = bottom - top;
        if (width < 20 || height < 30) return;
        double max =
            rows.stream().mapToDouble(r -> new BigDecimal(r.get(1)).doubleValue()).max().orElse(1);
        if (max == 0) max = 1;
        g.setFont(g.getFont().deriveFont(11f));
        for (int i = 0; i <= 4; i++) {
          int y = bottom - height * i / 4;
          g.setColor(new Color(228, 236, 239));
          g.drawLine(left, y, left + width, y);
          g.setColor(new Color(90, 107, 113));
          g.drawString(compact(max * i / 4), 4, y + 4);
        }
        double cell = (double) width / rows.size();
        int bar = (int) Math.max(4, Math.min(52, cell * .6));
        for (int i = 0; i < rows.size(); i++) {
          int x = left + (int) ((i + .5) * cell) - bar / 2;
          int h = (int) (height * new BigDecimal(rows.get(i).get(1)).doubleValue() / max);
          g.setColor(new Color(27, 126, 132));
          g.fillRoundRect(x, bottom - h, bar, Math.max(1, h), 5, 5);
          String label = rows.get(i).get(0);
          if (rows.size() > 6) label = label.substring(5);
          int text = g.getFontMetrics().stringWidth(label);
          g.setColor(new Color(70, 90, 98));
          g.drawString(label, x + bar / 2 - text / 2, bottom + 18);
        }
      } finally {
        g.dispose();
      }
    }

    private static String compact(double n) {
      return n >= 1e9
          ? String.format(java.util.Locale.ROOT, "%.1f tỷ", n / 1e9)
          : n >= 1e6
              ? String.format(java.util.Locale.ROOT, "%.1f tr", n / 1e6)
              : n >= 1e3
                  ? String.format(java.util.Locale.ROOT, "%.0f k", n / 1e3)
                  : String.format(java.util.Locale.ROOT, "%.0f", n);
    }
  }
}
