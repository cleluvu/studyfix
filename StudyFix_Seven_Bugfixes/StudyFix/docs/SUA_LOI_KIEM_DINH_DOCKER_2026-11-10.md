# Sửa lỗi kiểm định Docker báo TLE giả

Ngày xử lý: 10/11/2026.

**Đã sửa và áp dụng vào server http://localhost:8080.** Bản nháp **JAVA004 — Hoán đổi hai biến** trong ảnh đã được chạy kiểm định lại trên dữ liệu chính và đạt **5/5 PASS**. Phiên sửa và content hash của bản nháp giữ nguyên; không phát hành bài trong quá trình sửa lỗi.

## Nguyên nhân

Bộ chấm cũ bắt đầu đồng hồ ngay khi gọi `docker run`. Thời gian Docker Desktop chuẩn bị container, mount thư mục và kết nối I/O bị tính vào giới hạn chạy bài. Do đó một chương trình đúng có thể bị kết thúc trước khi thực thi xong dù bản thân chương trình chạy nhanh. Template cũng dùng chung cách đo từ phía host và cấu hình JVM chưa được giới hạn rõ cho công đoạn biên dịch.

Trong phép đo sau sửa: lệnh thực thi **51 ms**, toàn bộ lượt Docker **3.076 ms**, vẫn đạt với giới hạn thực thi **250 ms**. Chương trình lặp vô hạn bỏ qua TERM vẫn bị dừng trong khoảng **380 ms** với giới hạn 250 ms và khoảng xử lý cưỡng chế dừng.

## Thay đổi

- Tách thời gian vận hành Docker khỏi thời gian biên dịch/chạy lệnh trong container. Giữ giới hạn thực thi theo cấu hình đề; thêm ngân sách vận hành riêng, có chặn trên.
- Đo và giới hạn lệnh bằng supervisor trong container. Worker chạy bằng tài khoản không phải root, không có effective capabilities và không nhận token trạng thái. Các quyền SETUID/SETGID/KILL chỉ dùng cho supervisor để hạ quyền và dừng worker.
- Supervisor được mount chỉ đọc từ file riêng, tránh lỗi truyền chuỗi lệnh có dấu nháy trên Windows. Token ngẫu nhiên xác thực kết quả supervisor, không tin các dòng giả mạo do chương trình nộp in ra.
- Tiếp tục cách ly mạng, filesystem chỉ đọc, giới hạn bộ nhớ/process/output; giới hạn container một CPU và JVM một processor. Thiết lập rõ heap cho javac và biên dịch Java 17 với `-proc:none`.
- Phân loại lỗi khởi động/kết nối Docker thành lỗi hạ tầng thay vì TLE của bài làm.
- Nếu template/lời giải chuẩn chưa đạt, bước kiểm tra lời giải sai được ghi **CHƯA CHẠY**, không tạo tiêu chí PASS gây hiểu nhầm.

Không cần tăng giới hạn chạy của bài hoặc thay cấu hình `.env` để xử lý lỗi này. Database tiếp tục chỉ sử dụng SQLite.

## Kiểm chứng

| Kiểm tra | Kết quả |
| --- | --- |
| Chạy lại đúng bản nháp lỗi trên bản sao SQLite | 5/5 PASS |
| Chạy lại bản nháp trong dữ liệu chính bằng JAR mới | 5/5 PASS; revision/content hash không đổi; số bản phát hành không đổi |
| Sandbox timing/isolation | 7 kiểm tra Docker thật đạt: loại trừ startup, worker không đặc quyền, không đọc/giết supervisor, chống giả trạng thái, dừng vòng lặp và chặn output |
| Toàn bộ Problem Builder | PROGRAM/METHOD/CLASS, kiểm định, review, phát hành fixture, nộp bài, phiên bản bất biến và tạo expected: đạt |
| Luyện tập qua Docker thật | 25 kiểm tra AC/WA/CE đạt |
| Bài thi qua Docker thật, giới hạn gốc 1 giây | AC/WA/CE/RE/TLE đạt, giữ thang điểm và chính sách chấm |
| Hồi quy phòng học | 112 kiểm tra đạt |
| Hồi quy tổ chức thi | 52 kiểm tra đạt |
| Hồi quy ngân hàng đề | 53 kiểm tra đạt |
| Frontend | Kiểm tra cú pháp/import đạt; tiêu chí chưa chạy được hiển thị tách khỏi PASS |
| Bản chạy chính | Health đạt, SQLite; bytecode khớp bản đã kiểm thử |

Một lần chạy hồi quy đồng thời gặp timeout đăng nhập của fixture khi máy đang tải nặng; chạy lại độc lập đã đạt. Không thay đổi timeout đăng nhập của ứng dụng.

## Tệp và bằng chứng

- `server/src/studyfix/assignment/grading/DockerJavaSandbox.java`
- `server/src/studyfix/assignment/grading/OjJavaSandbox.java`
- `server/src/studyfix/assignment/service/ProblemBuilderWorkflow.java`
- `web-client/static/features/problem-builder/validation-step.js`
- [Báo cáo kiểm định mới của bản nháp](../server/logs/docker-fix-validation-result.json)
- [Log kiểm định dữ liệu chính](../server/logs/docker-fix-validation-replay.log)
- [Kiểm tra timing/isolation](../server/docker-fix-isolation.log)
- [Kiểm tra toàn bộ Builder](../server/docker-fix-builder-complete.log)
- [Kiểm tra bộ kết quả chấm](../server/docker-fix-verdicts.log)

Source gốc và ba database được sao lưu tại `server/backups/before-docker-timeout-fix-20261110-001043`. Log server mới: `server/logs/docker-fix-server.out.log` và `.err.log`; PID launcher được ghi tại `server/logs/docker-fix-server.pid`.

Để xem kết quả: tải lại trang, đăng nhập lại nếu phiên cũ đã hết hạn sau khi khởi động server, mở bản nháp rồi chọn **Cập nhật kết quả** ở bước Kiểm định.
