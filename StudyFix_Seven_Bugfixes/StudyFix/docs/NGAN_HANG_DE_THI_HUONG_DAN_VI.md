# Ngân hàng đề thi — giao diện chính thức

## Phạm vi và cách mở

Phân hệ dành cho Teacher, sử dụng dữ liệu thật trong `server/data/studyfix-learning.db` (SQLite). Không có dữ liệu demo được tự thêm vào database.

1. Đóng Server Manager cũ bằng nút dừng dịch vụ/đóng cửa sổ, chờ dừng hoàn toàn.
2. Từ thư mục `StudyFix_Seven_Bugfixes`, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
```

3. Mở địa chỉ web được launcher in ra, thường là `http://localhost:8080`. Nhấn `Ctrl+Shift+R` để nạp frontend mới.
4. Đăng nhập tài khoản giảng viên, mở nhóm **Ngân hàng đề thi** trên sidebar.

Launcher giữ nguyên cấu hình SQLite hiện tại. Migration tự tạo bảng còn thiếu khi AssignmentService khởi động; không chạy `clean`, không xóa database và không nhập lại ngân hàng câu hỏi.

## Chín màn hình

| Màn | Chức năng |
|---|---|
| Tất cả đề thi | Bốn thống kê thật; tìm kiếm lọc dần không cần Enter; lọc nguồn và trạng thái; 10 kết quả/trang; chọn và xuất phiên bản |
| Thông tin | Mã đề, tên, mô tả, thời lượng đề xuất, tổng điểm, phiên bản; lưu nháp |
| Chọn nguồn bài | Chọn từ Week hoặc tất cả bài đã phát hành; tìm kiếm; gỡ câu; không thêm trùng câu |
| Cấu trúc & điểm | Thứ tự câu bằng nút lên/xuống, điểm từng câu, chia đều điểm, tổng điểm và phân bố độ khó |
| Cấu hình chấm | Kế thừa cấu hình câu hỏi hoặc cấu hình chung; chính sách phản hồi, template và nộp lại |
| Xem lại & lưu | Kiểm tra nội dung, xem trước, lưu nháp hoặc duyệt đề |
| Chi tiết | Tổng quan, danh sách câu, cấu hình, nhật ký; tạo phiên bản mới, xuất, lưu trữ và dùng để tổ chức thi |
| Xem trước sinh viên | Hộp thoại chuyển từng câu, nội dung và ví dụ công khai; không tạo lượt thi hoặc bản nộp |
| Import/Export | Nhập JSON/ZIP, kiểm tra phụ thuộc và xem trước rồi xác nhận; xuất JSON hoặc ZIP |

Sidebar còn có **Phiên bản đề thi**, hiển thị tất cả các phiên bản thay vì chỉ phiên bản mới nhất của mỗi mã đề.

## Quy trình sử dụng

### Tạo đề

- Nhấn **Tạo đề thi mới** hoặc chọn các câu hỏi trong Ngân hàng câu hỏi rồi nhấn **Thêm vào đề thi**. Không cần vào lớp để soạn đề.
- Mã đề gồm chữ, số, `_`, `-`, tối đa 64 ký tự; tự chuyển chữ hoa. Sau lần lưu đầu tiên mã không đổi.
- Mỗi bộ đề tối đa 200 câu; chỉ dùng bài `BUILD_FROM_SCRATCH` đã phát hành trong bộ chọn. `Dạng bài` trong bảng là chủ đề như Mảng một chiều, Chuỗi, Lớp và đối tượng; không dùng Program/Method/Class thay cho chủ đề.
- Nhập tổng điểm rồi nhấn **Chia đều điểm** nếu cần. Điểm từng câu có tối đa 2 chữ số thập phân, phải dương. Duyệt đề yêu cầu ít nhất một câu và tổng khớp chính xác.
- Lưu nháp vào SQLite. Trước khi xem trước ở wizard, các thay đổi được lưu nháp; tên và mã là bắt buộc.
- Ở bước cuối, chọn **Duyệt đề sau khi kiểm tra hợp lệ** rồi Hoàn thành. Bỏ chọn để chỉ lưu nháp.

### Phiên bản và lưu trữ

- `DRAFT` cho phép sửa với `expectedRevision`. Nếu hai cửa sổ cùng sửa, bản lưu sau bị từ chối khi revision cũ; không ghi đè nội dung cửa sổ khác.
- `APPROVED` giữ nguyên nội dung. **Tạo phiên bản mới** sao chép thành nháp với số phiên bản tăng dần.
- `ARCHIVED` không dùng để tạo kỳ thi mới. Kỳ thi đã tạo tiếp tục dùng dữ liệu riêng đã chụp.
- Danh sách chính hiển thị phiên bản có số lớn nhất của mỗi mã đề; mở **Phiên bản đề thi** để xem cả bản đã duyệt trước đó.
- Câu hỏi trong đề gắn với ID snapshot bất biến. Sửa/xóa câu hỏi trong ngân hàng không đổi đề đã duyệt. Phiên bản câu hỏi đã phát hành và chưa bị xóa vẫn có thể được giữ trong đề/backup dù ngân hàng đã có phiên bản câu hỏi mới. Bộ chọn ưu tiên các phiên bản hiện hành.
- Chuyển trang hoặc tải lại khi có thay đổi chưa lưu sẽ cảnh báo. Thay đổi chưa lưu không được giả vờ coi là đã ghi database.

### Dùng đề để tổ chức thi

1. Giảng viên vào lớp do mình quản lý.
2. Mở chi tiết một phiên bản **Đã duyệt** → **Dùng để tổ chức thi**.
3. Nhập tên kỳ thi, thời gian bắt đầu sau bao lâu và thời lượng.
4. Tạo kỳ thi. Trang **Thi & xếp hạng** hiện có tiếp tục xử lý người tham gia, làm bài, giám sát, phân phát đề và kết quả.

Nhánh tạo từ bộ đề giữ thứ tự và điểm mỗi câu, không giới hạn cũ 20 câu của biểu mẫu tạo kỳ thi trực tiếp. Luồng tạo trực tiếp cũ giữ nguyên. Kỳ thi từ đề mới chụp cấu hình riêng; sửa phiên bản đề sau đó không đổi kỳ thi.

### Cấu hình chấm thực sự áp dụng

- Mặc định kế thừa cấu hình gốc của từng câu.
- Khi tắt kế thừa: Java 17, thời gian 100–10.000 ms/test (UI chọn 1/3/5/10 giây), bộ nhớ 128/256/512 MB, output tối đa 4 MiB, checker `EXACT`/`TOKEN`/`FLOAT`, tính điểm từng phần hoặc toàn phần của từng bài.
- Cấu hình chung được ghép vào runner OJ khi chấm bài thi; giữ support files/test delivery của câu gốc. Không sửa dữ liệu câu gốc.
- Điểm câu được truyền xuyên suốt từ đề → bài trong kỳ thi → receipt → worker → kết quả, kể cả đường xử lý lỗi. Ví dụ câu 37 điểm khi AC được 37, không tự đổi thành 100.
- Tắt hiện điểm: API sinh viên giữ score `null` đến khi kỳ thi kết thúc; Teacher vẫn xem kết quả phục vụ quản lý.
- Tắt chi tiết test: API sinh viên không trả testcase result, số test đạt hoặc phản hồi chi tiết của kết quả.
- Tắt template: dữ liệu bài thi gửi tới sinh viên không kèm nội dung template.
- Tắt nộp lại: server chỉ nhận một bản nộp cho mỗi sinh viên/câu trong kỳ thi; retry cùng idempotency key trả receipt cũ.
- Không thêm JUnit, custom checker hay Maven dependency mới. Các chức năng ngoài runner hiện tại không có nút giả lập.

## Import/Export và chuyển sang máy khác

Định dạng **gói đề** khác với **gói câu hỏi OJ**.

- JSON: `format=StudyFixExamBank`, `schemaVersion=1`, `questionDelivery=REFERENCES_WITH_SHA256`, mảng `exams`.
- ZIP: chỉ có `manifest.json` ở thư mục gốc, chứa JSON trên. Không giải nén file vào hệ thống.
- Gói lưu cấu trúc, điểm và cấu hình đề cùng ID/mã/checksum của snapshot câu hỏi; **không kèm lời giải, test ẩn hay mã chấm**.
- Máy nhận cần đúng câu hỏi đã phát hành. Nếu thiếu hoặc SHA-256 khác, hệ thống từ chối và báo câu hỏi cần bổ sung; nhập bộ câu hỏi OJ bằng luồng hiện có trước.
- Đây không phải ZIP tự chứa mọi dữ liệu chấm. Chuyển máy đầy đủ cần chuyển đúng ngân hàng câu hỏi hoặc backup SQLite theo quy trình riêng.
- Tối đa 2 MiB/gói (cả manifest sau giải nén), 50 định nghĩa mỗi lần nhập/xuất. Đây là giới hạn tài nguyên của gói đề; không thay đổi chức năng nhập OJ số lượng linh hoạt.
- Sau khi chọn file, server kiểm tra định dạng, checksum, quyền và mã trùng. Xem trước có hiệu lực 5 phút, thuộc đúng Teacher; chỉ dùng commit thành công một lần.
- Trùng mã của mình: có thể chọn tạo phiên bản nháp mới. Mã của Teacher khác bị từ chối.
- Xác nhận nhập toàn bộ trong một transaction. Có lỗi hoặc dữ liệu thay đổi sau xem trước thì rollback; không nhập một phần và không ghi đè bản đã duyệt.

## Thành phần source

| File | Trách nhiệm |
|---|---|
| `server/src/studyfix/assignment/persistence/ExamBankRepository.java` | Migration, ownership, nháp, duyệt, phiên bản, lưu trữ, lịch sử, snapshot câu hỏi, checksum và import transaction |
| `server/src/studyfix/assignment/service/ExamBankService.java` | API, đóng gói JSON/ZIP, đọc ZIP có giới hạn, preview token và commit |
| `assignment/service/AssignmentService.java` | Dispatch EXAMBANK trước kiểm tra phòng; nối đề với kỳ thi; thực thi chính sách và điểm chấm |
| `assignment/persistence/AssignmentRepository.java` | Tạo kỳ thi từ các item có điểm và lưu liên kết phiên bản/cấu hình trong transaction |
| `assignment/transport/AssignmentTcpServer.java` | Cung cấp service đã xác thực cho gateway |
| `server/src/studyfix/WebGatewayServer.java` | Allowlist API và upload nhị phân đã xác thực |
| `web-client/static/features/exam-bank/state.js` | State UI và các nhãn/quy tắc hiển thị |
| `web-client/static/features/exam-bank/view.js` | Chín màn, bảng dữ liệu và preview nội dung công khai |
| `web-client/static/features/exam-bank/index.js` | Gọi API, sự kiện, lưu nháp, navigation guard, import/export và tổ chức thi |
| `web-client/static/exam-bank.css` | Kiểu dáng tím/trắng được giới hạn trong `.exam-bank` bằng CSS scope |
| `core/polling.js`, `core/events.js`, `core/roles.js`, `core/state.js`, `app.js`, `ui/render.js` | Đăng ký route, load dữ liệu, sidebar, khôi phục trang và chặn Student |
| `features/exercises/practice.js` | Chuyển lựa chọn câu hỏi sang bản nháp đề, không bắt buộc vào phòng |
| `features/exercises/exercises.js` | Hiển thị trạng thái ẩn điểm/test đúng theo chính sách trả về |

API nghiệp vụ: `POST /api/assignments` với các action `EXAMBANK_LIST`, `SOURCES`, `GET`, `PREVIEW`, `SAVE`, `APPROVE`, `ARCHIVE`, `CLONE`, `EXPORT`, `IMPORT_COMMIT` (đều có tiền tố `EXAMBANK_`). Upload: `POST /api/exam-bank/import?format=JSON|ZIP&update=true|false` với body nhị phân và bearer hiện tại. Danh tính lấy từ server; không nhận owner từ client.

## Kiểm thử

Từ `StudyFix/server`, dùng Java/Ant của dự án:

```powershell
ant -Dbuild.dir=build/exam-bank-check -Ddist.dir=build/exam-bank-dist exam-bank-test
ant -Dbuild.dir=build/exam-bank-check -Ddist.dir=build/exam-bank-dist exam-bank-docker-test
```

Lệnh thứ hai cần Docker Desktop hoạt động và image `eclipse-temurin:21-jdk`. Test dùng database tạm dưới `build/`, không tác động database thật.

- `ExamBankIntegrationTest`: HTTP/SQLite thật, Teacher/Student và hai Teacher, lưu/duyệt/revision, phiên bản bất biến, tìm kiếm, checksum, ZIP/JSON, preview ownership, template, điểm và nộp lại, lưu trữ và restart.
- `ExamBankDockerIntegrationTest`: Docker thật, TOKEN checker, AC đúng 37 điểm và lỗi hidden test nhận 0 khi chấm toàn phần.
- `web-client/tests/exam_bank_browser.cjs`: Chrome thật với fixture `ExamBankIntegrationTest --browser`, chụp 9 màn, lọc dần, wizard, hard reload, preview, tạo kỳ thi, xuất–nhập ZIP, mobile và sidebar Student.
- Các `web-client/tests/check_*.mjs` kiểm tra hồi quy module phía client.

Kiểm thử thủ công: tạo đề 2 câu 40/60 điểm, duyệt; mở thêm cửa sổ để thử revision cũ; sửa câu hỏi gốc rồi kiểm tra đề cũ không đổi; tạo kỳ thi từ đề; cho Student nộp và kiểm tra điểm từng câu; xuất ZIP, nhập lại với tùy chọn phiên bản mới; kiểm tra Student không mở được API EXAMBANK dù tự gửi request.

Ảnh kiểm thử chính thức và báo cáo browser: `server/build/exam-bank-qa/`. Nhật ký tích hợp: `server/build/exam-bank-regression.log`. Bản demo HTML trong docs chỉ là tài liệu tham khảo; giao diện dùng thật mở trong ứng dụng web đã đăng nhập.
