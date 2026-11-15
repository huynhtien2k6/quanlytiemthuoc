# Kết quả kiểm thử — 15/11/2026

## Kiểm tra tự động

Biên dịch toàn bộ source bằng JDK 17.0.21 với `--release 17`, đóng gói JAR. Lệnh `bash build.sh test` chạy **121 kiểm tra**:

| Suite | Số kiểm tra | Nội dung chính |
|---|---:|---|
| ServiceTest | 63 | PK/FK, CRUD, stock theo ngày, thuốc kê đơn/hết hạn, nhập lô hết hạn, chứng từ tương lai, rollback file/bộ nhớ, Excel roundtrip, giỏ hàng nguyên tử, DTO bất biến, kỳ thống kê, loại hóa đơn trống khỏi KPI |
| DaoTest | 24 | TXT UTF-8/khóa/đóng tài nguyên, Excel Date 1900/1904, Boolean, inline/shared strings, XML namespace, ô trùng/gộp/công thức/DTD, export lỗi không ghi đè file cũ |
| RecordEditorTest | 12 | Combobox hiện nhãn và lưu mã, khóa PK khi sửa, ngày nhuận hợp lệ/ngày lỗi, lượng nguyên, cờ kê đơn, gợi ý giá từ BUS, thiếu danh mục |
| TableSupportTest | 5 | Sắp xếp số lượng/tiền theo số, trình bày tiền Việt Nam, bảng không cho sửa trực tiếp vượt BUS |
| InvoicePrintTest | 17 | Trang A4 đơn, hóa đơn dài 10 trang, tên thuốc 2.000 ký tự, không vẽ ra ngoài lề, dừng đúng trang cuối, tham số render lỗi |

Kiểm thử không dùng mật khẩu/tài khoản ngoài và tạo dữ liệu trong thư mục tạm.

## Luồng giao diện đã chạy trên Linux/Xvfb

`GuiSmokeTest` mở 11 tab, tìm NV01, thêm/sửa/xóa khách hàng qua form thật; mở giỏ bán hàng; nhập `2.5` và xác nhận bị chặn thay vì cắt thành 2; nhập `2` bằng bàn phím rồi lưu; kiểm tra hóa đơn/chi tiết trong BUS và persistence. Lọc thống kê từ 01/11/2026 đến 03/11/2026, xác nhận doanh thu mẫu **580.000 VND**, rồi trả về tất cả. Kết xuất hóa đơn thực qua `InvoicePrinter`, chụp màn hình.

Dữ liệu GUI test được sao chép vào thư mục tạm, không sửa `data/store.txt` gốc. `docs/giao-dien.png` và `docs/ban-in-mau.png` được tạo từ bản sao sau khi bán hàng nên số liệu có thêm hóa đơn GUI_TEST.

## Tương thích và mô hình

- Xuất workbook mới, openpyxl đọc đủ 9 sheet, tiêu đề/style/filter/freeze, tiếng Việt và số 0 đầu điện thoại. Chuyển ngày thành ô Date và cờ kê đơn thành Boolean, lưu lại và Java đọc đúng snapshot.
- Chạy `schema.sql` bằng SQLite trong bộ nhớ, chèn dữ liệu mẫu và kiểm tra toàn bộ khóa ngoại bằng `PRAGMA foreign_key_check`.
- Mã nguồn không thêm thư viện chạy ngoài JDK. File TXT vẫn giữ version cũ và thứ tự cột, không yêu cầu chuyển đổi lại dữ liệu bản trước.

## Chạy lại

```bash
bash build.sh test
# Cần display hoặc xvfb-run trên Linux:
xvfb-run -a java -ea -cp build/classes:build/test-classes vn.pharmacy.GuiSmokeTest
# Xuất Excel mẫu từ dữ liệu hiện tại:
java -cp build/classes:build/test-classes vn.pharmacy.ExcelCompatibilityTest export docs/du-lieu-mau.xlsx
```

Windows thay dấu `:` trong classpath bằng `;`. Build.bat đã được sửa để trích dẫn đường dẫn chứa khoảng trắng; phần này được kiểm tra mã lệnh, chưa chạy trên Windows.

GitHub Actions được cấu hình chạy cả 121 kiểm tra headless và luồng Swing trên màn hình ảo, xuất JAR/ảnh thành artifact. Trạng thái chạy thực tế có trong tab Actions của repository.

## Phạm vi chưa xác nhận

Chưa chạy trực tiếp trên Windows/macOS, Microsoft Excel/LibreOffice GUI hoặc máy in vật lý. Đã kiểm tra OOXML bằng openpyxl và render trang in; khả năng lưu PDF còn phụ thuộc máy in PDF của hệ điều hành.

Ứng dụng phục vụ đồ án một cửa hàng/một máy, chưa có đăng nhập/phân quyền, lịch sử chỉnh sửa, thuế/giảm giá/trả hàng, quy đổi đơn vị, nhiều cửa hàng hoặc kết nối đơn thuốc điện tử. Import thay thế toàn bộ dữ liệu nên cần xuất sao lưu trước. Chưa mô phỏng mất điện/hỏng thiết bị; atomic replace và file lock chỉ bảo vệ trong phạm vi đã mô tả.
