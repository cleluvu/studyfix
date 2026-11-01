# Luyện code cá nhân

Mục **Luyện code** dùng cùng giao diện/chức năng cho giáo viên và sinh viên. Chỉ cần đăng nhập, không cần phòng hoặc bài được giao. Đây là bản triển khai từ phác thảo đã duyệt ngày 30/10/2026.

## Cách dùng

1. Đóng Server Manager cũ, chạy lại `server/run-server-manager.ps1`, tải lại trình duyệt bằng Ctrl+Shift+R. Launcher build ở `build-launcher/`, không xóa thư mục test.
2. Mở Luyện code ở sidebar. Danh sách dùng toàn bộ catalog hiện có, gồm lập trình từ đầu và sửa lỗi; có tìm mã/tên không dấu, lọc chủ đề/độ khó/loại/trạng thái và phân trang 10 bài.
3. Nhấn dòng bài hoặc Làm bài. Trang làm bài xếp dọc: đề và ví dụ; editor Java; nút Chạy thử/Nộp bài/Nạp code từ file; kết quả; cuối cùng lịch sử các phiên bản.
4. Chạy thử chỉ chạy test công khai trong Docker, không tính thành tích hay tạo phiên bản chính thức. Nộp bài lưu snapshot rồi chấm toàn bộ test. Chọn file `.java`/`.txt` tối đa 32.000 byte chỉ nạp vào editor, không tự nộp.
5. Lịch sử riêng từng tài khoản/bài, mới nhất trước, 10 phiên bản/trang. Xem code/kết quả mở snapshot chỉ đọc. Sửa editor và nộp tiếp không ghi đè phiên bản cũ.

Bài đã từng AC có màu xanh và nhãn Đã AC. Chưa AC mà có lỗi bài làm thì đỏ. Lịch sử từng phiên bản giữ verdict riêng: AC xanh, WA/CE/RE/TLE/MLE/OLE đỏ. Đang chấm có trạng thái chờ; lỗi Docker/dịch vụ được ghi riêng, không quy thành bài sai. Giáo viên không được xem source luyện tập riêng của tài khoản khác chỉ nhờ role giáo viên.

Nháp lưu theo tài khoản + bài trong localStorage, có bộ nhớ tạm dự phòng khi storage không ghi được. Bài đang mở giữ theo tab trong sessionStorage. Refresh khôi phục trang luyện tập/bài đang mở kể cả không tham gia phòng. Polling theo đúng submission ID, không ghi đè code đang nhập khi kết quả cũ về muộn.

## Tái sử dụng và dữ liệu

- Dùng chung catalog `p3_exercises`, template/starter và test. Không tạo phòng hoặc assignment giả.
- Dùng lại editor, file import, formatter verdict và phần hiển thị kết quả của module exercises.
- PRACTICE dùng chung queue/worker, GradingSandbox và DockerJavaSandbox của AssignmentService; không chạy Java trực tiếp trên host.
- Thêm bảng `p3_practice_submissions` trong learning database, chứa user/exercise ID, version, snapshot source, is_run, timestamp, status/verdict, điểm/test/feedback và request key. Không đổi FK/dữ liệu assignment/contest/teacher trial cũ.
- Công cụ giao bài/báo cáo lớp vẫn truy cập từ Bài tập bên trong cuộc họp. Có nút về cuộc họp và chuyển sang luyện tập cá nhân. Master/Branch/Merge và bài thi giữ ngữ cảnh phòng.

## API

`POST /api/assignments`, bearer session, trường `type`:

| Type | Tham số | Kết quả |
|---|---|---|
| PRACTICE_LIST | query, topic, difficulty, mode, filterStatus, page | exercises, stats, topics, total/page/totalPages; 10 bài/trang |
| PRACTICE_GET | exerciseId | đề, template/starter, publicTests và tiến độ cá nhân |
| PRACTICE_SUBMIT | exerciseId, sourceCode, idempotencyKey | submission; version tăng theo tài khoản/bài |
| PRACTICE_RUN | như SUBMIT | isRun=true, version=0; chỉ test công khai |
| PRACTICE_SUBMISSIONS | exerciseId, page | 10 phiên bản chính thức/trang, không trả source trong danh sách |
| PRACTICE_RESULT | submissionId | snapshot source/kết quả của chính tài khoản |

Cả HTTP và service TCP xác thực phiên trước khi xử lý PRACTICE. Gateway ghi lại session ID theo bearer, bỏ session/room/request ID tự khai. Các thao tác assignment/contest cũ vẫn kiểm tra phòng. Idempotency key ràng buộc tài khoản, bài, chạy/nộp và source; retry không tạo phiên bản mới. Chỉ public view/ví dụ công khai được trả; test ẩn không lộ input/expected output và diagnostics ẩn được làm sạch. Queue chung có giới hạn, tác vụ QUEUED/RUNNING được khôi phục sau restart.

## Môi trường và kiểm thử

Docker Desktop phải đang chạy; image mặc định `eclipse-temurin:21-jdk`. Trong phiên triển khai đã tải image này và nghiệm thu chấm Java thật. Khi chuyển máy server, chuẩn bị cùng image hoặc cấu hình image JDK tương thích qua `studyfix.assignment.docker.image`.

- `ant practice-test`: 63 kiểm tra HTTP/auth/SQLite/worker, gồm không cần phòng, quyền hai role, riêng tư, retry, version/source bất biến, phân trang, AC bền vững, lỗi Docker và recovery. AC fixtures ở suite này chỉ kiểm tra persistence.
- `ant practice-docker-test`: 25 kiểm tra HTTP + Docker Java thật: public run giáo viên, AC/WA/CE sinh viên, test ẩn, lịch sử và snapshot.
- `node web-client/tests/check_practice.mjs` từ StudyFix: 21 kiểm tra HTTP/DOM mô phỏng cho UI chung, bố cục, file import dùng lại, nháp, phản hồi muộn, retry, polling, refresh và đổi tài khoản.
- `ant integration-test` chạy thêm practice cùng hồi quy cuộc họp/giao bài/thi. Dùng database tạm và JAR riêng.

Nghiệm thu UI: đăng nhập từng role chưa vào phòng, mở Luyện code; chọn Giai thừa, chạy ví dụ, nộp lời giải đúng rồi sai, kiểm tra bài vẫn xanh và lịch sử giữ AC/WA riêng. Xem code AC phải đúng snapshot cũ. Thử bài sửa lỗi/starter, nhập file, đổi bài/refresh giữ nháp và thử lại Bài tập trong cuộc họp.
