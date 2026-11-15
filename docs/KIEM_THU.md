# Kết quả kiểm thử — 15/11/2026

## Đã chạy thành công

- Biên dịch toàn bộ source với JDK 17.0.21, target `--release 17`; đóng gói JAR thực thi.
- `bash build.sh test`: **34 kiểm tra** BUS/DAO/Excel đạt.
- Kiểm tra 11 tab Swing dưới màn hình Xvfb; tìm kiếm NV01; mở form Bán hàng nhanh, thêm lô vào giỏ và xác nhận; kiểm tra hóa đơn/chi tiết đã được BUS lưu xuống TXT. Dùng thư mục tạm, không sửa data/store.txt gốc.
- Kết xuất trang in hóa đơn qua `JTable.getPrintable()` lên ảnh; kiểm tra có trang, lưu `ban-in-mau.png`.
- Xuất Excel bằng ứng dụng; openpyxl độc lập đọc đủ 9 sheet, giữ chữ tiếng Việt và số 0 đầu điện thoại; ghi lại rồi Java đọc lại bằng DAO, so sánh đúng toàn bộ snapshot.
- Thực thi `schema.sql` bằng SQLite trong bộ nhớ, chèn toàn bộ dữ liệu mẫu, `PRAGMA foreign_key_check` không có lỗi.

34 kiểm tra gồm tính tồn/doanh thu/top thuốc/tổng hóa đơn, tìm tiếng Việt, mã trùng, FK không tồn tại/xóa cha, bán vượt tồn, số lượng bằng 0, giá âm/sai số lẻ, hết hạn, lô trùng trên phiếu, rollback bộ nhớ và file, Excel roundtrip/import, batch import lỗi, đơn bác sĩ, sửa lùi ngày làm âm tồn, xóa phiếu nhập đã dùng, CRUD đảo tồn, giỏ hàng lỗi không sinh hóa đơn dở, giỏ nhiều dòng, giỏ trống, FEFO, TXT chứa TAB/xuống dòng, khóa file và mở lại.

## Chạy lại

```bash
bash build.sh test
# Cần display Linux hoặc xvfb-run đã cài; Windows/macOS chạy java tương ứng:
xvfb-run -a java -cp build/classes:build/test-classes vn.pharmacy.GuiSmokeTest
# Trên Windows thay dấu : phân cách classpath bằng ;
java -cp build/classes:build/test-classes vn.pharmacy.ExcelCompatibilityTest export docs/du-lieu-mau.xlsx
```

CI GitHub Actions chạy kiểm tra BUS/DAO/Excel trên JDK17, lưu JAR thành artifact. Trạng thái CI phụ thuộc việc push và quyền Actions của repository; kiểm thử ở trên đã chạy cục bộ trong môi trường Linux.

## Phạm vi chưa xác nhận

Chưa thử trực tiếp trên Windows/macOS, Microsoft Excel/LibreOffice GUI hoặc máy in vật lý. Khả năng ghi PDF phụ thuộc máy in PDF của hệ điều hành. Đã kiểm tra kết xuất trang in và tương thích workbook bằng openpyxl, không coi đó là kiểm tra máy in thực.

Đây là ứng dụng desktop học tập với dữ liệu một máy. Chưa có auth/phân quyền, lịch sử chỉnh sửa, chữ ký số, kiểm định chuẩn phần mềm nhà thuốc hay kết nối đơn thuốc điện tử. Import snapshot thay thế toàn bộ, nên xuất sao lưu trước. Không có bảo đảm khôi phục dữ liệu nếu người dùng sửa/xóa file ngoài ứng dụng hoặc thiết bị lưu trữ hỏng.

DAO dùng atomic move cùng thư mục, chống ghi dở khi tiến trình lỗi ở mức thông thường; chưa mô phỏng mất điện hay hỏng filesystem. Khi filesystem không hỗ trợ atomic move, thao tác báo lỗi và BUS giữ snapshot cũ.
