# Kiến trúc GUI–BUS–DAO và thiết kế form

```text
Main (composition root: khởi tạo và nối các lớp)
  ├─ TextDatabase (DAO)
  ├─ PharmacyService (BUS, nhận DAO qua constructor)
  └─ MainFrame (GUI, chỉ nhận BUS)

GUI → PharmacyService → TextDatabase / ExcelFile
           ↕                    ↕
                     Schema (DTO dùng chung)
```

## Trách nhiệm

| Package | Lớp | Trách nhiệm |
|---|---|---|
| dto | Schema, Table record | Tên bảng, nhãn cột, cấu trúc snapshot; mỗi hàng là List<String> |
| gui | MainFrame | Hiển thị, nhận dữ liệu, xác nhận thao tác, lựa chọn file, gọi BUS, in bảng |
| bus | PharmacyService | CRUD, PK/FK, ngày/tiền, tồn theo ngày, hết hạn, đơn bác sĩ, FEFO, tổng tiền, thống kê, import/export |
| dao | TextDatabase | Đọc/ghi TXT, lock, đổi snapshot nguyên tử |
| dao | ExcelFile | Đọc/ghi OOXML bằng ZIP/XML, không truy cập giao diện |
| gốc | Main | Ghép các lớp, khởi động Swing trên EDT, đóng tài nguyên khi thoát |

GUI không import DAO và không ghi file dữ liệu trực tiếp. `Main` là nơi lắp ghép phụ thuộc, không chứa nghiệp vụ. DTO ở đây dùng cấu trúc chung để tái sử dụng CRUD 9 bảng, thay vì 9 lớp model và 9 bộ GUI gần giống nhau. Điều này vẫn giữ ba lớp trách nhiệm; điểm đánh giá cụ thể cần giảng viên xác nhận theo quy ước của môn.

## Luồng thêm/sửa/xóa

GUI nhận nội dung → BUS sao chép snapshot → sửa bản sao → validate toàn bộ → DAO ghi file tạm và atomic replace → BUS đổi snapshot đang dùng → GUI tải lại các bảng. Nếu validate hoặc ghi thất bại, dữ liệu đang dùng vẫn giữ nguyên. Các phương thức BUS đồng bộ hóa, chỉ trả về bản sao để GUI không sửa lén dữ liệu.

Giỏ hàng nhanh thêm header và mọi dòng vào cùng một bản sao trước khi validate; không để hóa đơn dở nếu dòng thứ hai sai. Các form CRUD riêng vẫn cho tạo header trước rồi điền chi tiết từng bước.

## Thiết kế form

- Header xanh đậm, tên nhà thuốc và đồng hồ real-time; thanh trạng thái số lô/cảnh báo.
- Chín tab danh mục/chứng từ và hai tab Thống kê, Hướng dẫn.
- Mỗi tab có tìm kiếm tức thời, bảng sắp xếp theo cột, thêm/sửa/xóa/in, hộp thoại báo lỗi giữ lại nội dung vừa nhập.
- Form sửa khóa ô mã; form thêm sử dụng mã nhập tay. Các form CRUD tổng quát dùng nhãn cột và gợi ý định dạng ngày.
- Bán hàng nhanh có combobox khách hàng, nhân viên và lô theo FEFO; bảng giỏ hàng, số lượng spinner, tổng tiền, mã đơn bác sĩ.
- Báo cáo có 3 bảng riêng kèm nút in; hóa đơn có khách hàng, nhân viên, ngày, chi tiết và tổng tiền VND.

## Tính toán

- Thành tiền = số lượng × đơn giá dòng bán (BigDecimal).
- Doanh thu tháng = tổng thành tiền của các dòng hóa đơn trong tháng; không tính phiếu nhập.
- Bán chạy = tổng lượng bán theo mã thuốc, gộp mọi lô và sắp giảm dần.
- Tồn từng lô = tồn đầu + chi tiết nhập − chi tiết bán. Số cảnh báo chỉ tính lô, không suy ra lãi/lỗ.

Giá nhập không được dùng để công bố lợi nhuận vì chưa có phương pháp tính giá vốn (FIFO/bình quân) cho số dư đầu. FEFO chỉ là thứ tự gợi ý khi chọn lô, người dùng vẫn được chọn lô còn hạn khác.
