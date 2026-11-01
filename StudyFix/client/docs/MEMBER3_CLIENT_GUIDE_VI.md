# Hướng dẫn kiểm thử tab Bài tập / Nộp bài (Thành viên 3)

## Chạy client

Build một lần khi toàn bộ cửa sổ client đã đóng, tránh Windows khóa JAR:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\build.ps1
.\self-test.ps1
```

Mở một cửa sổ: `java -jar .\target\studyfix-client-1.0.0.jar`. Muốn mở thêm client, mở PowerShell mới cùng thư mục rồi chạy `.\run-client-only.ps1` (không build lại). Mỗi cửa sổ đăng nhập một tài khoản khác nhau.

## Cổng cần thiết

Client nối điều khiển tới server host / port `5000` như thường lệ. Tab Bài tập mở kênh riêng `5002`; vì vậy server phải được khởi động bằng `run-with-assignments.ps1`. Chia sẻ màn hình vẫn dùng relay `5001`. Trên cùng máy dùng `127.0.0.1`; máy LAN khác dùng IP LAN của server.

## Test gợi ý

1. Giáo viên và học sinh đăng nhập, vào cùng phòng.
2. Tab `Ngân hàng bài`: tìm `ARR001`, xem đề; hidden tests và lời giải tham khảo không được trả về client.
3. Tab `Giao bài`: chọn mã bài, scope `ROOM` hoặc `INDIVIDUAL`, deadline; chỉ giáo viên chủ phòng được giao.
4. Tab `Làm bài cá nhân`: chọn bài đã giao, nạp template, viết code và chọn nguồn nộp `PERSONAL` hoặc `SHARED_SNAPSHOT`.
5. `Nộp bài để chấm`; trạng thái đi từ `QUEUED`/`RUNNING` đến verdict cuối. Khi Docker sẵn sàng có thể nhận `ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED` hoặc giới hạn khác; không có Docker thì `SANDBOX_UNAVAILABLE`.
6. Tab `Lịch sử & kết quả`: học sinh chỉ xem bài của mình; giáo viên chủ phòng được xem kết quả trong phạm vi phòng.
7. Tab `Thi & xếp hạng`: tạo kỳ thi từ mã các bài đã giao, cài thời gian bắt đầu và thời lượng, chọn kỳ thi để nộp và xem bảng xếp hạng.

## Bảo vệ dữ liệu cá nhân

Bài cá nhân là snapshot bất biến cho mỗi lần nộp. Nộp lại tạo submission mới. Hidden test / expected output / reference solution không được gửi đến học sinh. Điểm và quyền truy cập do server xác minh; client chỉ render.
