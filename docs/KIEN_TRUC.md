# Kiến trúc GUI–BUS–DAO và thiết kế form

```text
Main (composition root: khởi tạo và nối các lớp)
  ├─ TextDatabase (DAO)
  ├─ PharmacyService (BUS, nhận DAO qua constructor)
  └─ MainFrame (GUI, chỉ nhận BUS)

GUI → PharmacyService → TextDatabase / ExcelFile
           ↕                    ↕
    Schema + LookupOption + InvoiceReport/InvoiceLine + SalesSummary (DTO)
```

## Trách nhiệm

| Package | Lớp | Trách nhiệm |
|---|---|---|
| dto | Schema, Table record | Metadata tên bảng/cột, cấu trúc snapshot và CRUD chung; mỗi hàng lưu trữ là List<String> |
| dto | LookupOption | Mã và nhãn hiển thị cho combobox khóa ngoại; chỉ mã được lưu |
| dto | InvoiceReport, InvoiceLine | Hóa đơn đọc từ BUS, có LocalDate, số lượng nguyên, đơn giá/thành tiền BigDecimal, danh sách chi tiết không cho sửa |
| dto | SalesSummary | Doanh thu, số hóa đơn có chi tiết và lượng bán của một khoảng ngày |
| gui | MainFrame | Điều phối các tab, nhận thao tác, xác nhận, chọn file, gọi BUS và tải lại bảng |
| gui | RecordEditor | Form nhập/sửa; combobox khóa ngoại, giới tính, loại thuốc; kiểm tra định dạng nhập |
| gui | StatsPanel | Bộ lọc ngày, KPI, ba bảng thống kê và biểu đồ doanh thu; gọi BUS để lấy số liệu |
| gui | InvoicePrinter | Kết xuất hóa đơn từ InvoiceReport; bố cục, ngắt trang và tổng cộng do BUS cung cấp |
| bus | PharmacyService | CRUD, PK/FK, ngày/tiền, tồn theo ngày, hết hạn, đơn bác sĩ, FEFO, tổng tiền, thống kê, import/export |
| dao | TextDatabase | Đọc/ghi TXT, lock, đổi snapshot nguyên tử |
| dao | ExcelFile | Đọc/ghi OOXML bằng ZIP/XML, không truy cập giao diện |
| gốc | Main | Ghép các lớp, khởi động Swing trên EDT, đóng tài nguyên khi thoát |

GUI không import DAO và không ghi file dữ liệu trực tiếp. `Main` là nơi lắp ghép phụ thuộc, không chứa nghiệp vụ. CRUD và snapshot tiếp tục dùng cấu trúc chung để tái sử dụng form của 9 bảng; không có tuyên bố rằng mỗi thực thể lưu trữ đã có một class riêng. Các luồng chọn khóa ngoại, thống kê tổng quan và in hóa đơn sử dụng DTO có kiểu cụ thể, giúp GUI không phải tự ghép bảng hay tính tổng tiền hóa đơn. Cách tổ chức này phân chia ba trách nhiệm GUI–BUS–DAO; cần đối chiếu thêm với quy ước triển khai cụ thể của giảng viên.

`lookups(table)` trả mã kèm nhãn dễ đọc (lô có tên thuốc, hạn dùng, tồn). `sellingPriceForLot(lotId)` lấy giá bán gợi ý trong BUS. `invoiceReport(id)` ghép khách hàng, nhân viên, lô và thuốc thành báo cáo; GUI chỉ định dạng và in. `summary(from, to)`, `revenue(from, to)` và `bestSellers(from, to)` tính số liệu trong khoảng ngày, không giao việc lọc/tổng hợp nghiệp vụ cho GUI.

## Luồng thêm/sửa/xóa

GUI nhận nội dung → BUS sao chép snapshot → sửa bản sao → validate toàn bộ → DAO ghi file tạm và atomic replace → BUS đổi snapshot đang dùng → GUI tải lại các bảng. Nếu validate hoặc ghi thất bại, dữ liệu đang dùng vẫn giữ nguyên. Các phương thức BUS đồng bộ hóa; các hàng CRUD trả về bản sao và danh sách chi tiết InvoiceReport bất biến, tránh việc GUI sửa dữ liệu đang dùng mà bỏ qua validate. Dữ liệu đầu vào sai cấu trúc, thiếu bảng hoặc chứa null bị từ chối trước khi ghi.

Giỏ hàng nhanh thêm header và mọi dòng vào cùng một bản sao trước khi validate; không để hóa đơn dở nếu dòng thứ hai sai. Các form CRUD riêng vẫn cho tạo header trước rồi điền chi tiết từng bước; báo cáo chỉ đếm hóa đơn có chi tiết.

BUS kiểm tra cả các thao tác sửa dữ liệu cũ: không đổi thuốc của lô đã có giao dịch, không nhập/bán hàng hết hạn và không ghi phiếu nhập/hóa đơn ở ngày tương lai. Kiểm tra tồn chạy theo ngày, kể cả sửa lùi ngày hoặc xóa một dòng nhập mà hàng đã bán. Nhập Excel thay thế toàn bộ snapshot sau cùng một quy trình validate/commit; nó không phải cơ chế lưu lịch sử mọi chỉnh sửa.

## Thiết kế form

- Header xanh đậm, tên nhà thuốc và đồng hồ real-time; thanh trạng thái số lô/cảnh báo.
- Chín tab danh mục/chứng từ và hai tab Thống kê, Hướng dẫn.
- Mỗi tab có tìm kiếm tức thời, bảng sắp xếp theo cột, thêm/sửa/xóa/in, hộp thoại báo lỗi giữ lại nội dung vừa nhập.
- Form sửa khóa ô mã; form thêm có mã gợi ý và cho chỉnh trước khi lưu. Trường tham chiếu dùng combobox mã kèm tên; giới tính/loại thuốc dùng lựa chọn thay cho chuỗi tự nhập. Ngày vẫn nhập theo `yyyy-MM-dd` và được kiểm tra trước khi lưu.
- Bán hàng nhanh có combobox khách hàng, nhân viên và lô theo FEFO; bảng giỏ hàng, số lượng spinner, tổng tiền, mã đơn bác sĩ.
- Báo cáo có 3 bảng riêng kèm nút in, bộ lọc khoảng ngày, tổng quan kỳ và biểu đồ doanh thu. Bảng tồn là tồn hiện tại, không bị lọc theo khoảng ngày bán. Hóa đơn có khách hàng, nhân viên, ngày, đơn vị/lô/hạn dùng, chi tiết và tổng tiền VND; mẫu in hỗ trợ nhiều trang.

## Tính toán

- Thành tiền = số lượng × đơn giá dòng bán (BigDecimal).
- Doanh thu tháng = tổng thành tiền của các dòng hóa đơn trong tháng nằm trong khoảng lọc (bao gồm cả hai đầu); không tính phiếu nhập. Giá sử dụng là đơn giá đã lưu trên chi tiết bán, không phải giá danh mục hiện tại.
- Số hóa đơn kỳ = số mã hóa đơn có dòng bán trong kỳ; tổng lượng bán = tổng số lượng của các dòng đó. Header chưa có dòng là bản nháp, không làm tăng các KPI.
- Bán chạy = tổng lượng bán theo mã thuốc, gộp mọi lô và sắp giảm dần.
- Tồn từng lô = tồn đầu + chi tiết nhập − chi tiết bán. Số cảnh báo chỉ tính lô, không suy ra lãi/lỗ.

Giá nhập không được dùng để công bố lợi nhuận vì chưa có phương pháp tính giá vốn (FIFO/bình quân) cho số dư đầu. FEFO chỉ là thứ tự gợi ý khi chọn lô, người dùng vẫn được chọn lô còn hạn khác.
