# Phân hệ kỳ thi trực tuyến

Cập nhật: 10/11/2026. Tài liệu mô tả phiên bản hiện tại, thay thế hướng dẫn tổ chức thi cũ.

## Các màn hình

| Màn hình | Chức năng |
| --- | --- |
| Tất cả kỳ thi | Tìm theo mã/tên/lớp/học phần; lọc trạng thái, học phần; quản lý nháp; hoãn, đổi lịch, hủy, xuất danh sách. |
| Tạo kỳ thi | Thông tin, chọn đề đã duyệt, chọn học sinh, quy chế và giám sát, xem lại rồi lên lịch. Không cần chọn hình thức hay cấu hình địa điểm. |
| Chi tiết kỳ thi | Lịch, đề, danh sách học sinh, tìm kiếm, thêm học sinh hợp lệ trước giờ thi, nhập CSV, in/xuất danh sách; chuyển sang giám sát/kết quả. |
| Giám sát kỳ thi | Bắt đầu, heartbeat, kết thúc, số câu đã nộp, số lượt nộp; tìm MSSV/họ tên, lọc trạng thái/cảnh báo; biểu đồ theo lớp; gia hạn, khóa, thông báo, kết thúc và nhật ký. |
| Chấm thi & kết quả | Bài nộp, trạng thái chấm, lọc câu/kết quả/điểm, source và test được phép, điều chỉnh/chấm lại, công bố, xuất CSV và in biên bản. |

Học sinh chỉ thấy kỳ thi được chọn. Đề được bảo vệ trước giờ bắt đầu và sau thời hạn cá nhân. Code chưa nộp không tự trở thành bài nộp. Kết quả đã công bố giữ nguyên khi chấm lại hoặc điều chỉnh cho đến lần công bố tiếp theo.

## Quyền và API

Các API đang dùng: nguồn lớp/học sinh/đề, danh sách/chi tiết, nháp, lên lịch, bổ sung danh sách học sinh, điều hành, xác nhận/vào/hoàn tất lượt, heartbeat, hỗ trợ, source nháp, lịch sử, bài nộp, điều chỉnh/chấm lại/công bố, CSV.

Giáo viên khác không có quyền truy cập chỉ vì từng được phân công ở phiên bản trước. Các API tạo địa điểm/xếp chỗ/phân công cũ không được nhận diện là thao tác hợp lệ.

## Chuyển dữ liệu

Migration SQLite phiên bản 2 chạy khi backend mới khởi động, trong cùng giao dịch với khởi tạo schema. Bản nháp cũ được bỏ các thuộc tính đã nghỉ dùng; các cột, bảng và chỉ mục chỉ phục vụ địa điểm/chỗ ngồi được gỡ.

Migration không đổi mã kỳ thi, học sinh, thời gian tham gia, gia hạn, code đã lưu hay điểm. Với kết quả đã công bố, chỉ loại metadata địa điểm; mã phiên bản, thời điểm công bố và giá trị điểm được giữ. Nhật ký hành động lịch sử được giữ để đối chiếu.

Phòng học trực tuyến của lớp học không bị loại bỏ. Liên kết lớp của contest cũ vẫn được dùng nội bộ để bảo đảm tương thích.

## Kiểm tra và áp dụng

- `node tests/check_online_exams.mjs`: các màn hình và cả năm bước tạo; không có điều khiển đã bỏ.
- `node tests/check_imports.mjs`: kiểm tra module và vòng phụ thuộc.
- `ant exam-organization-test`: migration SQLite, quyền truy cập, lịch, danh sách, vào thi, nộp bài, hạn nộp, công bố và khởi động lại; dùng database tạm.
- `node tests/exam_organization_browser.cjs <fixture-base>`: Chrome thật; cần server `ExamOrganizationIntegrationTest --browser` riêng. Không dùng server hoặc database của người dùng để chạy kiểm thử.
- Ảnh và báo cáo trình duyệt được ghi trong `server/build/online-exams-browser/`.

Kết quả đã chạy: 11 kiểm tra chuyển SQLite/khởi động lại, 50 kiểm tra tích hợp kỳ thi, 53 kiểm tra ngân hàng đề và chính sách chấm, 76 kiểm tra Exam Mode, 11 màn hình/bước giao diện và 30 kiểm tra Chrome. Kiểm tra import không phát hiện module thiếu hoặc vòng phụ thuộc. Các bài nộp trong kiểm thử trình duyệt dùng worker fixture; đây không phải xác nhận chạy bộ chấm Docker thực tế.

Sau khi cập nhật source, dừng rồi khởi động lại Server Manager để nạp backend/schema mới; sau đó tải lại trình duyệt bằng Ctrl+F5. Không xóa database để áp dụng thay đổi.
