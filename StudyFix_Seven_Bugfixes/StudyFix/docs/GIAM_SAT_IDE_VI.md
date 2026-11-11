# Cho phép VS Code và NetBeans trong lúc thi

Đã thêm hỗ trợ IDE vào giám sát kỳ thi và phòng sửa lỗi. Ngoại lệ chỉ áp dụng khi công cụ trên máy học sinh xác nhận ứng dụng foreground là VS Code hoặc NetBeans. Mở trình duyệt khác hoặc chuyển tab trong cùng trình duyệt vẫn tạo tín hiệu rời trang. Tắt hoặc mất kết nối công cụ khôi phục cảnh báo thông thường.

## Thiết lập

1. Khởi động lại server để áp dụng CSP mới và tải lại trang bằng Ctrl+F5.
2. Nếu học sinh truy cập bằng IP LAN, dùng địa chỉ HTTPS có chứng chỉ được máy học sinh tin cậy. Xem [hướng dẫn HTTPS LAN](HTTPS_LAN_MIC_CAMERA_VI.md). Chrome mới yêu cầu secure context và quyền truy cập mạng cục bộ cho kết nối từ trang thi tới công cụ trên máy học sinh: [tài liệu Chrome](https://developer.chrome.com/blog/local-network-access).
3. Trên trang làm bài của học sinh, mở **Hướng dẫn IDE**, tải công cụ Windows và giải nén. Gói cũng nằm ở `web-client/static/downloads/studyfix-ide-support.zip`.
4. Mở PowerShell trong thư mục đã giải nén và chạy (thay địa chỉ bằng đúng địa chỉ StudyFix đang dùng):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-ide-support.ps1 -StudyFixOrigin https://192.168.1.251:8080
```

5. Giữ công cụ chạy, quay lại trang thi và bấm **Kết nối hỗ trợ IDE**. Cho phép truy cập mạng cục bộ nếu trình duyệt yêu cầu. Trạng thái phải hiện **Hỗ trợ VS Code / NetBeans đã kết nối** trước khi chuyển sang IDE.
6. Sau khi tải lại trang, bấm kết nối lại. Nếu đổi địa chỉ server, đóng công cụ và chạy lại với địa chỉ mới. Ctrl+C trong cửa sổ công cụ để dừng.

Công cụ phải chạy trên **từng máy học sinh**, không chỉ máy server. Hỗ trợ Windows PowerShell 5.1, không cần thêm thư viện hoặc quyền quản trị để mở cổng loopback. Cổng 18765 phải còn trống. Dùng HTTP localhost khi thử trên chính máy server được; HTTP qua IP LAN sẽ được hướng dẫn chuyển sang HTTPS.

## Hành vi

- VS Code (`Code.exe`), NetBeans (`netbeans.exe`/`netbeans64.exe` hoặc Java có tham số khởi chạy NetBeans): không tăng số lần rời trang khi đã kết nối hỗ trợ IDE.
- Chrome, Edge, Firefox và trình duyệt khác: vẫn được ghi nhận. Từ IDE mở trình duyệt cũng được ghi nhận; không cần quay lại trang thi trước.
- Công cụ giữ các lần đổi nhóm ứng dụng trong bộ nhớ để trang thi đọc bù khi tab nền bị giảm tần suất chạy. Nó chỉ trả nhóm ứng dụng và thời điểm; không gửi tiêu đề cửa sổ, địa chỉ web, nội dung clipboard hoặc code.
- Không xóa cảnh báo đã ghi nhận trước đó. Quy định dán code và thời hạn nộp bài vẫn áp dụng.
- Nhận diện ứng dụng dùng [GetForegroundWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getforegroundwindow) và tiến trình sở hữu cửa sổ, không dựa vào tên trang hoặc tiêu đề cửa sổ.

## Kiểm tra

- `student-tools/test-ide-support.ps1`: biên dịch C#, phân loại ứng dụng, endpoint foreground thật, giới hạn Origin và preflight.
- `web-client/tests/check_ide_monitoring.mjs`: IDE được phép, IDE sang trình duyệt, lịch sử bị trì hoãn, mất và phục hồi kết nối, tab cùng trình duyệt và rời trang thi.
- `web-client/tests/exam_ide_browser.cjs`: Chrome với UI hiện tại, hướng dẫn/tải công cụ, CSP, thông báo và số lần cảnh báo; mẫu foreground được mô phỏng.
- `ExamModeIntegrationTest`: 76 kiểm tra backend HTTP/WebSocket/SQLite với dữ liệu tạm; `check_exam_monitoring.mjs` kiểm tra hồi quy giám sát và giới hạn dán.

Chưa kiểm thử thao tác đổi cửa sổ IDE trên nhiều máy học sinh qua LAN vật lý. Khi triển khai cần xác nhận kết nối trên từng máy và đúng bản cài NetBeans/VS Code của lớp.