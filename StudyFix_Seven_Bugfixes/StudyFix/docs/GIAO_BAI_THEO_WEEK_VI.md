# Giao bài trong Week, không cần vào phòng

## Giảng viên

1. Mở Ngân hàng bài tập → Theo Week, chọn tuần học.
2. Tích chọn 1–100 bài Lập trình từ đầu đã phát hành thuộc Luyện tập trong tuần đó. Bài Java OOP có thể giao như bài Program/Method.
3. Bấm **Giao bài trong Week**. Trong hộp thoại có thể bỏ bớt bài và đổi thứ tự bằng nút ↑/↓.
4. Chọn một hoặc nhiều lớp đang mở do mình quản lý. Chọn toàn bộ sinh viên trong các lớp, hoặc chọn từng sinh viên đã ghi danh trong các lớp này.
5. Đặt hạn nộp chung hoặc để trống để không giới hạn, rồi xác nhận. Sinh viên không cần đang online, không cần vào phòng.

Một sinh viên thuộc nhiều lớp đã chọn chỉ có một lượt giao cho mỗi bài. Các lớp liên quan được lưu cùng người nhận; cùng bài sẽ hiện trong các lớp đó nhưng dùng chung một lịch sử nộp. Mỗi lần giao tối đa 2500 sinh viên và 10000 cặp bài/người nhận. Giao bổ sung bằng cách chọn các bài hoặc người chưa được giao.

Không giao lặp cùng câu hỏi gốc cho cùng sinh viên trong cùng Week và cùng giảng viên, kể cả câu hỏi đã có revision mới. Server báo tên bài và người đã nhận để giáo viên điều chỉnh; không giao một phần nếu xác nhận bị lỗi. Retry cùng requestKey và cùng nội dung trả lại đợt giao đã tạo, không tạo trùng.

Tab **Đã giao & Tiến độ** cho phép lọc lớp, xem kết quả từng bài/từng sinh viên, mở lịch sử và code đã nộp, gia hạn hoặc đóng/mở nhận bài của lần giao. Gia hạn áp dụng cho mọi bài/người nhận trong lần giao đó; đóng rồi gia hạn vẫn cần mở lại nếu muốn nhận bài tiếp. Hạn mới phải trong tương lai; có thể bỏ hạn bằng cách để trống.

## Sinh viên

Mở Luyện code → Theo Week, chọn lớp đang học. Timeline các tuần chỉ liệt kê bài đã giao cho chính sinh viên đó trong lớp đã chọn. Week chưa được giao hiện trạng thái Chưa giao, không hiện các bài nguồn trong ngân hàng.

Mở một bài để xem đề, ví dụ công khai, code mẫu và lịch sử riêng của lượt giao. Code tự lưu trên máy, có thể nạp file Java/TXT tối đa 32 KiB. **Chạy thử** chỉ chấm test công khai và không tính tiến độ; **Nộp bài** chấm toàn bộ test bằng bộ chấm hiện có, bao gồm rubric cấu trúc OOP. Nội dung test ẩn không trả về sinh viên.

- Đã nộp: có receipt nộp bài thật, kể cả đang chờ chấm; không tính chạy thử.
- Đạt: có một lượt nộp thật được chấm ACCEPTED.
- Điểm cao nhất: cao nhất trong các lượt nộp thật đã chấm xong.
- Hoàn thành Week: đạt tất cả bài được giao cho sinh viên trong lớp/tuần đang xem.

Hết hạn hoặc bị đóng thì vẫn xem đề, code và lịch sử nhưng không được tạo lượt chạy/nộp mới. Server luôn kiểm tra hạn, không dựa vào trạng thái nút ở trình duyệt. Retry receipt đã được tiếp nhận trước đó vẫn trả lại đúng receipt sau khi đóng/hết hạn.

## Dữ liệu và quyền

Week nguồn dùng chung như trước. Mỗi lần giao lưu riêng giảng viên, ảnh chụp tên/số tuần, bài theo thứ tự, người nhận, lớp liên quan và hạn nộp. Sửa nội dung hoặc gỡ câu hỏi khỏi ngân hàng không viết lại nội dung bài đã giao. Xóa Week nguồn vẫn giữ nhóm đã giao, lịch sử và kết quả; Week được giữ lại trong timeline đã giao. Sinh viên bị rút khỏi tất cả lớp liên quan sẽ mất quyền truy cập lượt giao đó.

Không chuyển hoặc xóa bài giao theo phòng trước đây. Bài tự luyện và bài được giao theo Week có lịch sử/tiến độ tách riêng. Giao theo Week chỉ nhận bài thuộc PRACTICE đã phát hành; EXAM_PRIVATE và bài sửa lỗi cộng tác cần phòng không thuộc luồng này. Các tuần trong timeline lấy từ danh mục Week chung, nhưng bài trong tuần luôn lọc theo người nhận/lớp và giảng viên của lớp.

SQLite bổ sung p3_week_dispatches, p3_week_work, p3_week_receivers và p3_week_submissions. Work tham chiếu ID snapshot bài tập bất biến; không tham chiếu roomId hoặc phiên online. Báo cáo OOP dùng p3_structure_results theo receipt. Danh sách lớp/người nhận lấy từ users/classes/class_members trong database quản trị, kiểm tra active và ENROLLED; không lấy người đang có mặt trong phòng.

WA_SOURCES, WA_TIMELINE, WA_CREATE, WA_PROGRESS, WA_CONTROL, WA_OPEN, WA_RUN, WA_SUBMIT, WA_HISTORY và WA_RESULT được xác thực trước kiểm tra phòng. Worker WEEK dùng chung hàng đợi, Docker Java/OOP judge và khôi phục receipt khi khởi động lại. Giao bài, chống trùng và tiếp nhận receipt dùng giao dịch SQLite IMMEDIATE; điểm và báo cáo OOP được lưu cùng giao dịch.

## Kiểm thử

`ant week-assignment-test` dùng database tạm và Docker thật: lớp chồng lấn, học sinh offline, không vào phòng, giao riêng, quyền giáo viên/sinh viên, retry, đóng/quá hạn/gia hạn, ẩn test, snapshot nội dung, OOP và restart. `WeekAssignmentIntegrationTest --browser` chạy thêm Chrome: chọn nhiều bài ngay trong Week, giao cho lớp, timeline sinh viên, giữ code khi reload, nạp file Java, nộp/chấm/cập nhật tiến độ và đóng nhận bài. Playwright cần biến STUDYFIX_BROWSER_TEST_MODULES trỏ đến node_modules phù hợp.
