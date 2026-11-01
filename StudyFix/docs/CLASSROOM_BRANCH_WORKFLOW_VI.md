# Phòng học sửa lỗi với Master và nhánh cá nhân

**Giao diện hiện hành:** Phòng học đã có bố cục tối kiểu cuộc họp và mic/camera WebRTC theo yêu cầu mới. Xem [hướng dẫn giao diện giảng viên và sinh viên](MEETING_UI_VI.md) để biết nút điều khiển, phạm vi LAN, giới hạn media và phần chưa nghiệm thu. Các mô tả bố cục thẻ trước đây bên dưới được thay bằng workspace này; nghiệp vụ Master/nhánh vẫn giữ nguyên.

Bản cập nhật ngày 29/10/2026 chuyển phòng học Web của StudyFix sang quy trình: giáo viên giảng trên Master, tách code thành nhánh riêng cho từng học sinh, theo dõi bài làm và chọn một nhánh để công bố thành Master mới. Java, TCP, SQLite, Web Gateway và giao diện JavaScript hiện có được giữ lại theo lựa chọn của người dùng.

## Chạy và dùng

Từ thư mục `StudyFix`, chạy trong PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1
```

Script tìm JDK từ `JAVA_HOME` hoặc `java.exe` trong PATH; tìm Ant từ PATH, `ANT_HOME`, bản đi kèm NetBeans hoặc Downloads. Máy hiện tại đã xác minh JDK 26.0.2 và Ant 1.10.17 của NetBeans. Không cần cài Node để chạy giao diện. Nếu cấu hình môi trường riêng, `JAVA_HOME` phải trỏ tới JDK có cả `java.exe` và `javac.exe`.

Trước lần khởi động bản mới đầu tiên, dừng server và sao lưu toàn bộ `server/data/`, gồm WAL/SHM nếu có. Bản nâng cấp thêm bảng vào core database khi server khởi động, không yêu cầu xóa database. Các kiểm thử trong quá trình sửa chỉ dùng database tạm dưới `server/build/`.

Mở `http://localhost:8080`. Máy học sinh dùng `http://IP_MAY_CHU:8080` trong LAN. Dùng tài khoản demo hiện có: `teacher` / `Teacher123!`, `student1` / `Student123!`, `student2` / `Student234!`. Đổi sang các tài khoản thực nếu hệ thống đã có dữ liệu riêng. Hard refresh trình duyệt bằng Ctrl+F5 sau cập nhật mã giao diện.

1. Giáo viên tạo hoặc vào phòng; học sinh nhập cùng mã phòng.
2. Giáo viên nhấn **Bắt đầu giảng bài**, sau đó **Chọn code lỗi từ ngân hàng** và **Dùng starter**. Chỉ các bài `DEBUG_COLLAB` được dùng cho quy trình này.
3. Giáo viên có thể sửa và **Lưu Master** khi đang giảng bài. Học sinh chỉ xem Master.
4. Khi có ít nhất một học sinh trong phòng, giáo viên chọn **Tách nhánh cho cả lớp**. Các tài khoản học sinh nhận bản sao riêng từ đúng phiên bản Master.
5. Học sinh sửa **Nhánh riêng của bạn**. Code được lưu sau khoảng dừng 1,2 giây; có nút **Lưu nhánh** và **Chạy kiểm tra**. Có thể tắt tự kiểm tra sau khi lưu. Yêu cầu chấm được hạn chế tần suất và xếp hàng.
6. Giáo viên xem bảng **Theo dõi lớp**: trạng thái online, phiên bản nhánh, cập nhật gần nhất và kết quả chấm. Kết quả của bản code trước được đánh dấu rõ.
7. Chọn **Dừng sửa và chọn lời giải**, mở **Xem bài làm**, rồi **Chọn nhánh này làm Master**. Xác nhận dùng đúng snapshot đang xem. Giáo viên cũng có thể cho học sinh sửa tiếp.
8. Sau Merge, cả lớp nhận Master mới và quay về giảng bài. Giáo viên có thể mở vòng tách nhánh tiếp theo hoặc kết thúc phiên.
9. **Xem lại vòng** mở các nhánh và kết quả cũ. Học sinh chỉ xem bài của mình; giáo viên chủ phòng xem được cả lớp. **Lịch sử Master** giữ các bản trước và sau khi Merge.

Các lượt sửa của client cũ phải được kết thúc trước khi bắt đầu quy trình mới. Người đang giữ lượt có thể dùng **Hủy nháp và trả lượt cũ**. Luồng Master/nhánh mới được cung cấp trên Web FE; Swing vẫn phục vụ chức năng cũ, không có màn hình nhánh mới. Sau khi phòng chuyển sang phiên học mới, server từ chối yêu cầu học sinh sửa Master, kể cả gửi trực tiếp bằng TCP.

## Trạng thái và dữ liệu

```text
WAITING -> LECTURE -> BRANCHING -> MERGING -> LECTURE
                         ^           |
                         +-----------+  cho sửa tiếp
LECTURE / BRANCHING / MERGING -> ENDED -> WAITING
```

Mỗi lần tách nhánh tăng số vòng. Nhánh được định danh bằng phòng, vòng và `userId`, không phụ thuộc socket. Đăng nhập lại cùng tài khoản và vào lại phòng sẽ lấy bài đã lưu. Học sinh vào muộn trong `BRANCHING` nhận nhánh mới từ Master của vòng đó; vào trong `MERGING` phải chờ vòng tiếp theo.

Mỗi lần lưu có kiểm tra phiên bản. Hai phiên đăng nhập cùng tài khoản không được ghi đè lặng lẽ. Nháp chưa lưu được giữ trong `sessionStorage` của tab, có nút tải code; nháp của vòng cũ có mục khôi phục. Dữ liệu này không thay thế bản lưu server và có thể mất nếu đóng tab hoặc xóa dữ liệu trình duyệt. Khi server ngừng, đăng nhập và vào lại phòng để khôi phục dữ liệu đã lưu; chưa có cơ chế tự đăng nhập lại sau restart.

Mỗi lượt chấm lưu snapshot code, vòng và phiên bản nhánh bất biến. Kết quả cũ không được coi là kết quả của bản mới. Giáo viên quyết định lựa chọn lời giải; hệ thống không bắt buộc nhánh phải AC mới cho Merge. Merge là chọn toàn bộ snapshot làm Master, không phải ghép tự động nhiều nhánh như Git.

Trong cùng một SQLite transaction, Merge cập nhật Master, tạo revision, đổi trạng thái phiên và ghi sự kiện. Chỉ sau commit mới cập nhật RAM và phát sự kiện. Lỗi ở bất kỳ bước lưu nào đều rollback; điều này có test bằng trigger gây lỗi sau bước ghi Master.

Tin chat được ghi vào database trước khi broadcast. Giao diện tải 80 tin gần nhất; sự kiện phiên học hiển thị riêng trong nhật ký của giáo viên. Chat của những bản cũ vốn chỉ lưu trong RAM không thể khôi phục.

Ngân hàng bài, bài tập cá nhân, giao bài, lịch sử nộp bài, contest và chia sẻ màn hình hiện có được giữ lại. Không chuyển sang React, Node.js, MySQL, Socket.IO hoặc Judge0. Quản lý lớp/khóa học riêng, thi đội và chống gian lận chưa được bổ sung; audio/video đã có triển khai mesh LAN theo hướng dẫn cuộc họp ở đầu tài liệu.

## Chấm code và giới hạn

Backend dùng DockerJavaSandbox hiện có, ngôn ngữ Java, image mặc định `eclipse-temurin:21-jdk`. Worker phòng học riêng có một luồng và hàng đợi tối đa 32 lượt; mỗi học sinh có tối đa một lượt đang chờ/chấm, giãn cách ít nhất 3 giây. Giao diện tự kiểm tra với khoảng cách tối thiểu 5 giây. Các worker assignment/contest hiện có tiếp tục dùng cấu hình riêng.

Source tối đa 32.000 byte UTF-8. Tối đa 128 nhánh mỗi vòng. Sandbox giữ testcase ẩn ở server, hạn chế CPU, RAM, thời gian, process và output, tắt mạng container. Khi không có Docker hoặc chấm gặp lỗi, kết quả là `SANDBOX_UNAVAILABLE`/`JUDGE_ERROR`, không phải `ACCEPTED`. Lượt chưa hoàn tất khi server dừng được đánh dấu lỗi và có thể chạy lại.

Web FE đồng bộ bằng HTTP polling, không phải Socket.IO. Chưa có kết quả đo tải cho cả lớp hoặc thử trên nhiều máy LAN trong lần sửa này. Browser screen share vẫn cần HTTPS tin cậy khi truy cập bằng IP LAN; Swing relay và browser media vẫn là hai transport riêng.

## Mã nguồn chính

| File | Trách nhiệm |
|---|---|
| `server/src/studyfix/ClassroomRepository.java` | Migration và lưu phiên, nhánh, snapshot chấm, sự kiện, chat |
| `server/src/studyfix/ClassroomService.java` | Trạng thái, phân quyền, tách nhánh, chấm bất đồng bộ, Merge và xem vòng cũ |
| `server/src/studyfix/Store.java` | Transaction dùng chung giữa Master/revision và dữ liệu lớp học |
| `server/src/studyfix/Room.java` | Bảo vệ Master cả HTTP/TCP, công bố revision sau commit |
| `server/src/studyfix/WebGatewayServer.java` | API theo danh tính và phòng của phiên đăng nhập |
| `web-client/static/features/classroom/classroom.js` | Giao diện giáo viên/học sinh, autosave, nháp và kiểm tra xung đột |

`room.js` dùng workspace mới làm màn hình chính. `polling.js` đồng bộ phòng và lớp học, giữ workspace khi lỗi mạng tạm thời. Các hàm giao diện xin lượt cũ còn giữ để tương thích source cũ, không phải luồng chính trên web.

## Kiểm thử

Từ thư mục `server`:

```powershell
ant classroom-test
ant integration-test
```

Từ thư mục `web-client`:

```powershell
node tests/check_classroom.mjs
node tests/check_imports.mjs
node tests/check_seven_bugfixes.mjs
```

Kết quả trên Windows với JDK 26.0.2:

- Bộ hồi quy `ant integration-test`: **BUILD SUCCESSFUL**, gồm các suite assignment, TCP, gateway HTTP/HTTPS, edit session, recovery/history, relay và console/Swing đã có. Log: `server/build/classroom-regression-final.log`.
- Sau sửa nhỏ cho thời điểm học sinh vào muộn, `ant classroom-test`: **110 assertions PASS**, gồm học sinh vào muộn, nhánh độc lập, phân quyền, kiểm tra version/vòng, snapshot, rollback Merge, khôi phục sau restart, chat và adapter Docker thiếu executable. Log: `server/build/classroom-final.log`. Số assertions bao gồm các lần đọc kiểm tra trạng thái, không tương đương 110 kịch bản độc lập.
- Frontend: **22 behavioral checks PASS** bằng DOM/HTTP giả lập; kiểm tra role, read-only, giữ nháp khi xung đột, nhập trong lúc lưu, polling trả muộn, modal và nháp vòng trước. Kiểm tra 20 module không thiếu export, không có vòng import; 25 kiểm tra tĩnh của bảy nhóm lỗi cũ PASS.
- Kiểm thử ngữ nghĩa chấm trong suite mới có test double được inject rõ ràng; kiểm thử lỗi Docker dùng adapter thật với executable cố ý không tồn tại. Không tuyên bố đã chạy code tới AC trên Docker Engine thật.
- Không kiểm thử trình duyệt thật trong lần sửa này vì browser access host không kết nối được. Cần nghiệm thu thủ công luồng nêu ở đầu bằng giáo viên và ít nhất hai học sinh; chấm Docker thật và media LAN cũng chưa nghiệm thu.

Hai database gốc vẫn khớp SHA-256 trước/sau khi sửa và kiểm thử:

```text
studyfix.db          40cf07c52bfaa52b334ef341456f970787f6dc701ffe18ad3c572cb5056dbd70
studyfix-learning.db cb72892e51092ed07b27e85e3223e90684f6113243bed9a46efe022899a2aa48
```
