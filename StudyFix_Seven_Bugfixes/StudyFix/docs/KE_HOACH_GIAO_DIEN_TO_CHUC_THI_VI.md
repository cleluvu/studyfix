# Kế hoạch đơn giản hóa kỳ thi trực tuyến

Cập nhật: 10/11/2026. Kế hoạch này thay thế thiết kế tổ chức thi cũ.

Luồng chính: giáo viên tạo kỳ thi, chọn học sinh, lên lịch; học sinh được chọn vào thi trên web. Hai màn hình Giám sát kỳ thi và Chấm thi & kết quả được giữ lại.

## Phạm vi thực hiện

1. Bỏ màn hình phân công riêng khỏi sidebar, điều hướng, trạng thái lưu trong trình duyệt và các nút tác vụ.
2. Bỏ lựa chọn hình thức, địa điểm, máy/chỗ ngồi và nhân sự coi thi ở toàn bộ bước tạo kỳ thi, danh sách, chi tiết, bộ lọc, biểu đồ, bản in và CSV.
3. Giữ wizard năm bước: thông tin → đề thi → chọn học sinh → quy chế & giám sát → xem lại.
4. Quản lý học sinh trong chi tiết kỳ thi: tìm theo MSSV/họ tên, thêm học sinh thuộc lớp đã chọn, nhập CSV, in/xuất danh sách. Chỉ bổ sung trước khi kỳ thi bắt đầu.
5. Backend chỉ cho giáo viên sở hữu quản lý và giám sát; học sinh chỉ truy cập kỳ thi được chọn. Bỏ API và quyền dựa trên nhân sự coi thi. Giữ kiểm tra trùng lịch của học sinh.
6. Chuyển SQLite trong một giao dịch có đánh dấu phiên bản; giữ mã kỳ thi, bản nháp, danh sách học sinh, code, bài nộp, điểm và các phiên bản kết quả.
7. Cập nhật kiểm thử tích hợp, kiểm thử trình duyệt và tài liệu; rà lại toàn bộ mã nguồn đang chạy.

## Ranh giới dữ liệu

Phòng học/lớp học là tính năng khác và vẫn được giữ. Cột lớp trong dữ liệu contest cũ tiếp tục làm ngữ cảnh lớp học cho các API tương thích; không phải địa điểm thi.

Các bản lưu source trong deliverables và dữ liệu build cũ là hiện vật lịch sử, không dùng làm mã nguồn của phiên bản này.

Xem [báo cáo triển khai](KET_QUA_TRIEN_KHAI_TO_CHUC_THI_VI.md).
