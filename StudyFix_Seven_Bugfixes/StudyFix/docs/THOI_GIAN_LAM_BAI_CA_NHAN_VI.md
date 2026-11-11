# Khung mở kỳ thi và thời gian làm bài cá nhân

Khi tạo kỳ thi có hai thiết lập độc lập:

- **Ngày/giờ bắt đầu – ngày/giờ kết thúc:** khung mà kỳ thi cho phép thí sinh bắt đầu/làm bài.
- **Thời gian làm bài tối đa (phút):** số phút của từng học sinh, tính từ lần đầu bấm **Bắt đầu thi**.

Không còn tính lại thời lượng từ chênh lệch ngày giờ. Thay đổi thời lượng không thay đổi giờ đóng kỳ thi; thay đổi ngày giờ không ghi đè thời lượng cá nhân.

## Quy tắc hạn nộp

Không có gia hạn riêng:

`hạn của học sinh = min(giờ đóng kỳ thi, giờ bắt đầu lượt + thời lượng tối đa)`

Ví dụ mở từ 08:00 ngày 11/11 đến 20:00 ngày 12/11, thời lượng tối đa 90 phút:

| Bắt đầu lượt | Hết thời gian |
| --- | --- |
| 10:00 ngày 11/11 | 11:30 ngày 11/11 |
| 15:00 ngày 12/11 | 16:30 ngày 12/11 |
| 19:00 ngày 12/11 | 20:00 ngày 12/11 vì kỳ thi đóng trước khi đủ 90 phút |

- Hai học sinh vào khác giờ có hạn riêng. Một người hết giờ không kết thúc kỳ thi của người khác.
- Tải lại trang, đăng nhập lại, bấm tiếp tục hoặc heartbeat không đặt lại đồng hồ.
- Hết giờ cá nhân, lượt được đánh dấu kết thúc. Server chặn tiếp tục, lưu nháp và nhận bài nộp mới kể cả gửi API trực tiếp; giao dịch nhận bài cũng kiểm tra hạn.
- Giao diện có đồng hồ cá nhân và tự khóa trình soạn, nộp file, nút nộp khi hết giờ. Code đang hiển thị vẫn được giữ để tránh mất nháp; bài nộp đã nhận tiếp tục được chấm theo cơ chế hiện có. Không tự tạo một bài nộp mới từ code chưa nộp.
- Thử lại cùng idempotency key chỉ trả receipt cũ đã nhận hợp lệ, không tạo bài nộp muộn.
- Trước khi Bắt đầu thi, bài mới có giới hạn cá nhân không mở đề qua API để tránh đọc trước khi đồng hồ chạy.
- Giờ cuối kỳ thi vẫn là giới hạn chung. Nếu giáo viên chủ động gia hạn cho một học sinh, số phút gia hạn được cộng vào hạn riêng theo cơ chế gia hạn hiện có. Gia hạn khung kỳ thi mở rộng khung tiếp nhận, không đặt lại thời điểm bắt đầu lượt.

## Theo dõi

Thông tin học sinh hiển thị khung kỳ thi, số phút tối đa, hạn cá nhân và đếm ngược. Giám sát giáo viên có giờ bắt đầu và hạn nộp của từng thí sinh. Học sinh hết thời lượng có trạng thái đã kết thúc, trong khi kỳ thi chung vẫn có thể đang mở.

## Dữ liệu và tương thích

Migration bổ sung `p3_eo_exams.duration_minutes`, mặc định 0 cho các kỳ thi đã lên lịch trước cập nhật; không tự đổi quy chế của kỳ thi đang tồn tại. Kỳ thi tạo mới lưu riêng thời lượng từ bản nháp. Các kỳ thi cũ tiếp tục theo khung thời gian cũ; muốn có giới hạn mới hãy tạo kỳ thi mới hoặc nhân bản rồi chọn thời lượng.

`started_at` và `finished_at` của từng thí sinh được lưu trong SQLite, giữ nguyên qua restart. Hạn cá nhân được server tính lại từ thời điểm bắt đầu đã lưu và khung hiện tại; GET trạng thái/giám sát cũng ghi nhận lượt đã quá hạn.

Khởi động lại Server Manager sau cập nhật backend, sau đó Ctrl+F5 trên trình duyệt.

## Kiểm thử

`ExamOrganizationIntegrationTest` kiểm tra HTTP/SQLite thật: khung nhiều ngày với thời lượng riêng, hai người vào khác giờ, chống reset khi đăng nhập lại, đọc đề trước BEGIN, nộp trong hạn, lưu/nộp/API trực tiếp sau hạn, receipt retry, giới hạn giờ đóng chung, trạng thái người khác và restart. Các kiểm tra cũ về phân quyền, lịch thi, kết quả, gia hạn và công bố vẫn chạy.

- `web-client/tests/organization_calendar_browser.cjs`: Chrome trên backend thật, ngày kết thúc qua ngày khác, thời lượng 60 phút không đổi giờ đóng, lưu/tải lại/lên lịch vẫn giữ độc lập.
- `web-client/tests/exam_personal_timer_browser.cjs`: Chrome với HTTP fixture, đếm ngược và tự khóa cá nhân trong khi kỳ thi vẫn mở, giữ nguyên code nháp.
- Log: `server/build-debug-lessons/personal-duration-test.log`.
- Ảnh QA: `server/build/organization-calendar/qa/` và `server/build-debug-lessons/exam-timer/`.