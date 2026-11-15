# Kịch bản demo đồ án (10–15 phút)

1. Trình sơ đồ ERD: 9 bảng, khóa chính/ngoại; giải thích một thuốc nhiều lô và một hóa đơn nhiều dòng. Chỉ rõ vì sao không lưu tổng tiền/tồn hiện tại dư thừa.
2. Mở mã `Main → MainFrame → PharmacyService → TextDatabase`, nêu đúng vai trò GUI/BUS/DAO. Giải thích DTO dùng chung và việc GUI không đọc TXT.
3. Chạy ứng dụng, giới thiệu menu, tab, đồng hồ, tìm kiếm và sắp xếp.
4. Thêm KH03, tìm KH03, sửa tên; chưa dùng trong chứng từ thì xóa được. Thử xóa KH01 đang có hóa đơn: bị chặn.
5. Thêm thuốc T03, lô LO_T03 hạn 2099-12-31 tồn đầu 0; nhập PN02 (NCC01/NV01), thêm CTN02 lượng 10 giá 10000. Kiểm tra tồn = 10.
6. Hóa đơn → Bán hàng nhanh → chọn KH02/NV01 → chọn LO_T03 → lượng 2, giá 15000 → Thêm lô → OK. Kiểm tra tồn = 8, tổng 30000.
7. Thử bán 100 đơn vị LO_T03: báo vượt tồn và không tạo hóa đơn dở. Thử bán thuốc kê đơn khi bỏ trống mã đơn: bị chặn. Điền mã đơn để lưu được.
8. Xem / In hóa đơn và xem hộp thoại máy in; có máy in PDF thì lưu bản PDF để trình bày.
9. Mở 3 bảng thống kê. Lọc 2026-11-01 đến 2026-11-03: dữ liệu mẫu gốc có 580.000 VND doanh thu. Chỉ rõ KPI không đếm hóa đơn chưa có chi tiết, biểu đồ doanh thu và tồn kho hiện tại không thay đổi theo kỳ lọc.
10. Xuất Excel, chỉ ra tiêu đề/filter/freeze; mở bằng Excel/LibreOffice, sửa tên KH02 (ô Text), lưu và nhập lại; xác nhận dữ liệu cập nhật. Có thể nhập ngày bằng ô Date không chứa giờ.
11. Sửa mã thuốc tham chiếu sang mã không tồn tại trong Excel, thử nhập: từ chối toàn bộ, dữ liệu cũ vẫn nguyên. Xuất lại làm bản sao an toàn.
12. Đóng/mở lại ứng dụng để chứng minh dữ liệu TXT tồn tại qua phiên.

## Trước khi nộp

- Xin duyệt mô hình CSDL đúng quy trình giảng viên yêu cầu. Bản này dùng mô hình quan hệ nhưng storage là TXT theo thông tin được cho phép; cần thống nhất cách giảng viên chấm tiêu chí CSDL.
- Chạy kiểm thử, mở giao diện trên máy sẽ bảo vệ, kiểm tra font tiếng Việt, máy in/PDF và Excel.
- Dùng bản sao dữ liệu mẫu khi demo. Không đưa dữ liệu cá nhân thực tế vào kho công khai.
- Đọc và tự giải thích validate(), commit(), stock(), checkout(), write() và importExcel().
- Các tiêu chí điểm được đáp ứng bằng chức năng cụ thể trong README; không coi đó là cam kết số điểm, nhất là tổng điểm liệt kê trong đề lớn hơn 10.


## Các file nên mở sẵn khi giảng viên hỏi code

- `Main.java`: nơi ghép DAO, BUS, GUI và khởi động ứng dụng.
- `dto/Schema.java`: lược đồ 9 bảng; `InvoiceReport`, `InvoiceLine`, `LookupOption`, `SalesSummary`: dữ liệu có kiểu để chuyển giữa BUS và GUI.
- `gui/RecordEditor.java`: control nhập liệu, trả về giá trị; không ghi dữ liệu.
- `bus/PharmacyService.java`: `validate`, `commit`, `checkout`, `stock`, `summary`, `invoiceReport`.
- `dao/TextDatabase.java`: `read`, `write`, file lock và atomic replace.
- `dao/ExcelFile.java`: ZIP/XML OOXML, chuyển ô ngày/boolean, kiểm tra file đầu vào.
- `gui/StatsPanel.java`, `gui/InvoicePrinter.java`: trình bày thống kê và in từ kết quả BUS.

Khi sửa giao diện đổi `MainFrame`/`RecordEditor`. Khi sửa quy tắc tồn/giá/thống kê sửa BUS, không ghi TXT từ GUI. Sau sửa chạy `bash build.sh test`, chạy thử giao diện rồi mới cập nhật JAR. Sửa mã nguồn không tự sửa file JAR đã đóng gói trong dist.
