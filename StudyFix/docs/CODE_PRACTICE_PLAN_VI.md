# Đề xuất chuyển Bài tập thành Luyện code

Trạng thái: bản phác thảo đã được duyệt và triển khai. Xem [hướng dẫn hiện hành và kiểm thử](CODE_PRACTICE_VI.md). Các mục dưới đây lưu lại thiết kế đã thống nhất.

## Phạm vi đề xuất

- Đổi mục Bài tập thành Luyện code. Giáo viên/sinh viên dùng cùng bố cục và hành động, chỉ khác tên tài khoản và dữ liệu luyện tập cá nhân.
- Chỉ cần đăng nhập; không cần tham gia phòng hoặc được giao bài. Liệt kê toàn bộ bài hiện có trong catalog, gồm lập trình từ đầu và sửa lỗi; không tạo bản sao đề/test.
- Nhấn bài mở màn hình làm bài riêng: đề phía trên, editor ở giữa, nút Chạy thử/Nộp bài/Nạp code từ file dưới editor, lịch sử nộp ở cuối.
- Từng lần nộp lưu snapshot code và kết quả riêng; sửa editor hoặc nộp tiếp không làm thay đổi phiên bản đã nộp.
- Luyện tập không phụ thuộc vòng Master/Branch/Merge hay hạn giao bài. Chức năng giao bài của cuộc họp được giữ trong phạm vi cuộc họp, không trộn vào trang luyện tập chung.

## Kết quả rà soát mã hiện có

| Thành phần | Hiện trạng | Cách sử dụng lại |
|---|---|---|
| `SeedExerciseCatalog`, `Exercise`, bảng `p3_exercises` | Có đề, chủ đề, độ khó, template/starter, ví dụ, test ẩn và lời giải tham chiếu | Dùng chung catalog; API chỉ trả public view và ví dụ công khai |
| `features/exercises/exercises.js` | Có danh sách, chi tiết đề, workspace, kết quả và lịch sử; hiện chia theo role và chặn khi chưa vào phòng | Tách phần trình bày đề/editor/kết quả thành thành phần dùng chung, dựng trang luyện tập thống nhất |
| `core/events.js` | Đã nạp `.java`/`.txt`, kiểm tra kích thước, đọc nội dung và xác nhận thay editor | Dùng lại, chỉ thay nơi lưu buffer theo bài đang luyện |
| `renderSubmissionResult`, `renderSubmissions`, `statusClass`, `humanVerdict` | Có verdict, test, điểm, lịch sử; lịch sử hiện chỉ lấy tối đa 10 mục hiển thị | Dùng lại formatter/kết quả; bổ sung số phiên bản, xem snapshot và phân trang lịch sử |
| `AssignmentService`, queue/worker, `DockerJavaSandbox`, `GradingSandbox.grade` | Đã có chấm bất đồng bộ, giới hạn queue, chống nộp trùng và khôi phục sau restart | Thêm loại tác vụ luyện tập trong queue chung, không tạo bộ chấm hay luồng Docker thứ hai |
| `SUBMISSION_*`, bảng `p3_submissions` | Yêu cầu assignment và room; schema có FK assignment bắt buộc | Không tạo phòng/bài giao giả. Bổ sung bảng/API luyện tập độc lập và dùng chung hạ tầng chấm |
| `TEACHER_TRIAL_*` | Lượt thử riêng của chủ phòng; giáo viên/sinh viên hiện khác quyền | Không dùng quyền chấm thử giáo viên để lách ràng buộc; mở luồng practice hợp lệ cho cả hai vai trò |
| `WebGatewayServer.assignmentRequest`, `AssignmentService.handle`, `core/polling.js` | Gateway/service và tải trang hiện dựa trên phòng/role | Cho nhóm thao tác practice chỉ cần identity hợp lệ; giữ kiểm tra phòng cho các thao tác cuộc họp cũ |

## Giao diện thống nhất

Màn hình danh sách:

1. Tiêu đề Luyện code, mô tả có thể luyện ngay không cần giao bài.
2. Các ô thống kê cá nhân: Tổng bài, Đã AC, Đã thử chưa AC, Chưa làm.
3. Tìm mã/tên, lọc chủ đề/độ khó/loại bài/trạng thái. Phân trang đề xuất 10 bài/trang, tìm trên toàn catalog.
4. Bảng mã, tên, chủ đề, độ khó, trạng thái cá nhân và nút Làm bài. Click dòng/nút đều mở bài.

Màn hình làm bài theo chiều dọc:

1. Quay lại danh sách, mã/tên bài và trạng thái cá nhân.
2. Đề đầy đủ: mô tả, input/output, ràng buộc, ví dụ công khai; gợi ý có thể thu gọn.
3. Editor Java `Main.java`, template với bài lập trình, starter có lỗi với bài sửa lỗi. Buffer/nháp riêng từng tài khoản và bài; đổi bài không mang nhầm code.
4. Dưới editor: Chạy thử, Nộp bài, Nạp code từ file, Khôi phục code ban đầu (có xác nhận nếu đã sửa).
5. Kết quả gần nhất: trạng thái queue/running hoặc verdict cuối, điểm, test đạt, lỗi biên dịch/chạy nếu có. Kết quả ghi rõ thuộc phiên bản nào.
6. Cuối trang: lịch sử của chính tài khoản/bài, mới nhất trước, có phân trang; cột phiên bản, thời điểm nộp, trạng thái, test, điểm, thời gian chạy, Xem code/chi tiết.

## Quy tắc màu và phiên bản

- Bài đã từng có AC: xanh lá, nhãn Đã AC; nộp sai sau đó không mất thành tích AC.
- Đã có kết quả lỗi bài làm và chưa từng AC: đỏ, nhãn Chưa đạt. Gồm WA/CE/RE/TLE/MLE theo verdict thật.
- Chưa nộp: trung tính. Đang chấm: vàng. Lỗi dịch vụ chấm/Docker hiển thị Lỗi chấm, không quy thành bài sai của người học.
- Mỗi dòng lịch sử luôn giữ verdict riêng: ví dụ v1 WA đỏ, v2 CE đỏ, v3 AC xanh, v4 WA đỏ; thẻ bài vẫn xanh vì đã có v3 AC.
- Số phiên bản tăng theo tài khoản + bài, gắn một submission ID bất biến; gửi lại cùng request không tạo thêm phiên bản. Bản nháp không phải một lần nộp.
- Chạy thử chỉ dùng ví dụ công khai, không đánh dấu AC hoặc tạo phiên bản nộp chính thức. Dùng lại sandbox và hàng đợi có giới hạn; không mở thêm chạy Java trực tiếp trên máy chủ.

## Kế hoạch triển khai sau khi thống nhất

1. Tách các thành phần đề/editor/kết quả hiện có để dùng chung; chuyển công cụ giao bài về ngữ cảnh cuộc họp nếu hiện chỉ truy cập qua trang Bài tập.
2. Thêm persistence luyện tập: bài nộp, số phiên bản, snapshot source, kết quả/test, user ID, exercise ID, thời điểm và idempotency key. Giữ nguyên bài nộp/phòng/thi cũ.
3. Thêm nhóm thao tác `PRACTICE_LIST/GET/SUBMIT/SUBMISSIONS/RESULT/RUN` hoặc tên tương đương trong gateway/service hiện có. Xác thực từ phiên server; quyền luyện tập giống nhau cho giáo viên và sinh viên. Chỉ đọc source/lịch sử cá nhân; không trả lời giải tham chiếu hoặc test ẩn.
4. Nối tác vụ practice vào queue/worker/sandbox chung. Snapshot code lúc bấm Nộp bài; polling theo đúng submission ID, tránh lấy kết quả bài cũ ghi lên bài vừa mở.
5. Thay route Bài tập bằng Luyện code cho cả hai role, bỏ điều kiện phải có phòng, ghép tìm/lọc/phân trang, màu trạng thái và trang làm bài dọc. Nối lại file import đã có.
6. Kiểm thử đăng nhập nhưng không vào phòng, hai role cùng UI, riêng tư giữa tài khoản, mọi bài trong catalog, debug starter, snapshot v1/v2 không đổi, AC/WA và trạng thái chờ, file import, đổi bài/refresh, nộp trùng, queue đầy, restart và hồi quy cuộc họp/thi/giao bài.

## Bản xem trước

Mở `previews/CODE_PRACTICE_PREVIEW.html`: có thể tìm/lọc bài, chuyển vai trò, mở bài và xem code của phiên bản minh họa. Toàn bộ số liệu/verdict trong bản xem trước là dữ liệu mẫu; nút chạy/nộp không chấm thật. Bản này không gọi server và không thay đổi dữ liệu StudyFix.
