# Phân hệ học sinh

Phân hệ sử dụng sidebar, topbar, biểu tượng và bảng màu tím của template StudyFix hiện tại. Không thêm framework hay thư viện giao diện.

## Các trang

- **Tổng quan học sinh** (`overview`): lớp được phân công, bài luyện tập AC duy nhất, kỳ thi sắp diễn ra, bài được giao và lịch sử gần đây.
- **Lớp học của tôi** (`student-classes`): lớp có thành viên `ENROLLED`, giảng viên, sĩ số, mô tả, trạng thái phiên và yêu cầu vào phòng.
- **Luyện code** (`exercises`): giữ workspace Java, bản nháp, kết quả, lịch sử; có Tất cả bài tập / Theo Week và bộ lọc Đã làm, Chưa làm, AC.
- **Kỳ thi của tôi** (`exam-organization`): nhóm Sắp diễn ra, Đang diễn ra, Đã kết thúc; sử dụng luồng xác nhận, vào thi, tiếp tục và kết quả có kiểm soát công bố hiện có.
- **Bảng xếp hạng** (`student-ranking`): toàn hệ thống hoặc lớp học của học sinh, lọc 7/30/90 ngày hoặc toàn thời gian, vị trí cá nhân và 100 học sinh đứng đầu.
- **Tiến độ học tập** (`student-progress`): tỷ lệ lượt nộp AC, bài được giao, tiến độ từng Week, lịch sử có phân trang, xem code chỉ đọc và mở kết quả kỳ thi.

## Quyền vào phòng

Với chế độ quản lý SQLite, server kiểm tra lớp còn mở, học sinh thuộc lớp, giảng viên trực tuyến và phiên khác `WAITING` / `ENDED`. Học sinh vẫn phải được duyệt cho mỗi phiên đăng nhập. Kiểm tra được thực hiện ở HTTP gateway và đường vào phòng TCP; nút giao diện cũng kiểm tra lại dữ liệu trước khi gửi yêu cầu.

Giảng viên cần vào phòng và bấm bắt đầu phiên học trước khi học sinh xin vào. Phòng họp dùng lại giao diện và quyền hiện có: học sinh không được điều khiển phiên, Merge, ưu tiên sửa code hay xem dữ liệu kết nối riêng của giảng viên.

## Dữ liệu và cách tính

`GET /api/student/portal` lấy danh tính từ phiên đăng nhập, không nhận ID học sinh từ client. `classId` phải thuộc danh sách lớp của tài khoản; `days` hỗ trợ 0, 7, 30, 90; `page` phân trang 20 lượt nộp.

`GET /api/student/submission?id=...` chỉ đọc code luyện tập hoặc bài được giao của chính tài khoản. Endpoint này không trả bài thi, test ẩn hay code học sinh khác. Kết quả thi tiếp tục đi qua API kỳ thi và chính sách công bố hiện có.

Xếp hạng ưu tiên số bài luyện tập AC duy nhất, sau đó tổng điểm tốt nhất từng bài. Các phiên bản bài được gộp theo `root_id`; không cộng trùng lượt nộp, lượt chạy thử hay điểm thi. Cùng số bài AC và điểm thì đồng hạng. Bộ lọc lớp giới hạn học sinh trong bảng; điểm là hoạt động luyện tập của các học sinh đó. Lịch sử khi chọn một lớp chỉ gồm bài được giao trong lớp đó, vì lượt luyện tập cá nhân không có lớp sở hữu.

## Kiểm thử

Tại thư mục `server`, với JDK 26 và Ant đã cấu hình:

```powershell
ant -Dbuild.dir=build-student-portal student-portal-test
```

Kiểm thử sử dụng SQLite tạm và cổng tự cấp; không sửa tài khoản hoặc database hiện có.

Kiểm thử Chrome: chạy `studyfix.StudentPortalIntegrationTest --browser` với classpath gồm `build-student-portal/classes`, `build-student-portal/student-test-classes` và `lib/*`, sau đó dùng Node 22+ chạy `test/student-portal-browser.cjs`. Đặt `CHROME_PATH` nếu Chrome nằm ngoài đường dẫn mặc định. Tạo file `build-student-portal/stop` để đóng fixture.

Ảnh kiểm tra và báo cáo browser nằm trong `server/build-student-portal/`. Sau khi cập nhật, khởi động lại server để nạp JAR mới và tải lại trang bằng Ctrl+Shift+R.
