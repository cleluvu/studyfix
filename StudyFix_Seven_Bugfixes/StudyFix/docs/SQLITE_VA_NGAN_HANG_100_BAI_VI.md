# Chuyển SQLite và nhập ngân hàng 100 bài Java OJ

Ngày thực hiện: 09/11/2026. Gói gốc: `server/private/catalogs/CodeBank_Java_100_OJ_Complete.zip`.

## 1. Kết quả thực tế

- Toàn bộ runtime quản lý tài khoản/lớp/LAN đã chuyển sang SQLite. Không cần MySQL Server để chạy StudyFix.
- Giữ nguyên 8 tài khoản và password hash; sao chép 25 lớp, 5 quan hệ thành viên, 53 phiên và 37 bản ghi điểm danh, 18 yêu cầu vào lớp từ MySQL tại thời điểm chuyển. Phiên phát sinh sau khi chạy lại được ghi vào SQLite.
- Ngân hàng hoạt động có đúng **100 bài JAVA001–JAVA100**, **12 Week**, **316 test**, gồm **50 Program, 30 Method, 20 Class**.
- 25 đề cũ đã gỡ khỏi ngân hàng hoạt động. Các snapshot cũ còn trong database để bài giao, kỳ thi và bài nộp trước đây không mất tham chiếu.
- Đã kiểm chứng **100/100 reference AC** trong Docker và **100/100 starter chưa hoàn chỉnh không AC**. Không dùng kết quả ghi sẵn trong ZIP để gán AC.
- Đề mới nhập ở trạng thái **DRAFT**; việc phát hành cần giáo viên duyệt. Không tự coi bộ test ban đầu là đã đủ chất lượng giáo dục.

Các báo cáo có thể đọc trực tiếp:

- [`server/reports/OJ_100_SANDBOX_VERIFICATION.json`](../server/reports/OJ_100_SANDBOX_VERIFICATION.json): kết quả từng bài.
- [`server/reports/SQLITE_MIGRATION_AUDIT.json`](../server/reports/SQLITE_MIGRATION_AUDIT.json): kiểm tra integrity, khóa ngoại, số lượng bản ghi và ngân hàng.
- [`server/reports/SQLITE_OJ_FRONTEND_TESTS.json`](../server/reports/SQLITE_OJ_FRONTEND_TESTS.json): 20 kiểm tra frontend; các test dùng mock được ghi rõ trong output.

## 2. Kiến trúc và database sau chuyển đổi

Giữ Java, HTTP/TCP/WebSocket, giao diện HTML/CSS/JS và queue chấm đang có. Không thay bằng framework khác.

| File SQLite | Nhiệm vụ |
| --- | --- |
| `server/data/studyfix-management.db` | Tài khoản, phân quyền, lớp/thành viên, phiên LAN, điểm danh và yêu cầu vào lớp |
| `server/data/studyfix.db` | Lõi phòng học, code cộng tác, revisions, dữ liệu lớp học hiện có |
| `server/data/studyfix-learning.db` | Đề, Week, assignment/contest/submission, nháp Builder, metadata và kết quả kiểm định OJ |

Các bảng quản lý giữ cùng hợp đồng dữ liệu: `users`, `classes`, `class_members`, `sessions`, `attendance`, `class_admissions`, `runtime_binding`. Migration SQLite ở `server/src/studyfix/db/sqlite-management-schema.sql`.

`ManagementDatabase` và `TeacherRepository` hiện dùng trực tiếp cú pháp SQLite. Đã gỡ `ManagementSql`, nhánh MySQL, driver và công cụ kết nối MySQL. Giao dịch ghi dùng IMMEDIATE, WAL, kiểm tra khóa ngoại và busy timeout. Xem [báo cáo SQLite-only](SQLITE_ONLY_VI.md).

Cấu hình runtime trong `.env`:

```ini
STUDYFIX_MANAGEMENT_DB=sqlite
STUDYFIX_SQLITE_MANAGEMENT_PATH=data/studyfix-management.db
```

Thông tin MySQL đã gỡ khỏi `.env` hoạt động. Bản sao `.env` cũ nằm trong thư mục backup riêng, không đưa mật khẩu vào báo cáo hay source.

## 3. Ngân hàng đề và snapshot

Tái sử dụng `p3_exercises`, `p3_test_cases`, `p3_question_bank`, `p3_question_weeks`, `p3_question_week_items` và các bảng submission hiện có.

Migration cộng thêm:

- `p3_exercises.java_structure`, `entry_file`: xác định Program/Method/Class và tên file cần nộp.
- `p3_oj_exercises`: config và metadata riêng theo snapshot, kết quả kiểm định, hash gói và mã chuẩn.
- `p3_oj_imports`: lịch sử import theo SHA-256.
- `p3_oj_settings`: gói đang hoạt động; khi có gói OJ, server không seed lại ngân hàng cũ.

Nhập lại đúng gói sử dụng lại 100 snapshot, không nhân đôi đề hoặc Week. Week đã có cùng số được giữ ID, cập nhật nội dung và liên kết theo gói. Nhập gói mới không ghi đè snapshot đã sử dụng; trường hợp trùng ID tạo version riêng và giữ mã hiển thị.

Không tạo Category. Week là cách tổ chức đề; Program/Method/Class là cấu trúc nộp/chấm. Thuộc tính topic hiện có được dùng làm nhãn Week/dạng bài, không thêm phân hệ Category.

Nguồn private ZIP/reference/hidden test nằm ngoài `web-client/static`. API sinh viên chỉ trả đề, starter, tên file và test public. Quyền đọc reference chỉ cấp cho Teacher có quyền quản lý đề.

## 4. Sửa lỗi dữ liệu JAVA092

Gói ghi 314 test trong phần mô tả prompt, nhưng kiểm kê manifest và file thật có **316 test**. Importer lấy số lượng từ dữ liệu thực.

JAVA092 dùng `Files.readAllLines(...).size()` để đếm dòng. Hai fixture gốc có một LF được thêm cuối khi đóng gói:

| Fixture | Nội dung gốc | Đáp án trong gói | Nội dung sau hiệu chỉnh |
| --- | --- | --- | --- |
| Public TC001 | `a\nb\nc\n\n` | 3 dòng | `a\nb\nc\n` |
| Hidden TC002 | `\n` | 0 dòng | file rỗng |

Importer loại đúng một LF cuối của hai fixture thuộc riêng JAVA092, giữ nguyên đáp án và ý nghĩa hai trường hợp “ba dòng” / “file rỗng”. Việc này được ghi vào `metadata.import_corrections` và preview. ZIP gốc không bị sửa. Grader vẫn so sánh chính xác và không bỏ qua dòng trống của các bài nộp.

Các bản nháp JAVA092 đã nhập trước khi phát hiện lỗi chỉ được sửa nếu còn DRAFT và chưa được sử dụng. Snapshot đã phát hành/giao/nộp không được ghi đè. Sau sửa đã chấm lại reference bằng Docker và đạt 2/2 test.

## 5. Cách chấm ba dạng

`OjJavaSandbox` được gọi từ worker hiện có cho mọi submission có metadata OJ. Những bài cũ vẫn dùng `DockerJavaSandbox.grade` cũ.

- **Program:** biên dịch `Main.java`, chạy stdin/stdout. JAVA091/JAVA092/JAVA094 ghi fixture hiện tại vào file trong vùng sandbox và truyền đường dẫn qua `args[0]`.
- **Method:** biên dịch `Solution.java`, giữ helper Shape/Product của JAVA077/JAVA086 trong source; dựng harness riêng cho mỗi test, thực thi snippet, so sánh stdout với expected trên server.
- **Class:** dùng đúng tên file như `Student.java`; harness gọi constructor/method và các kiểm tra reflection/hành vi do gói định nghĩa. Không đổi thành bài stdin/stdout.

Harness mang tên ngẫu nhiên, không dùng class `Harness` cố định để sinh viên thay thế. Các định nghĩa lớp trùng lớp hạ tầng/harness bị từ chối. Chỉ test hiện tại được đưa vào workspace; không đưa cả bộ hidden test hoặc expected vào thư mục chương trình truy cập.

Biên dịch với `--release 17`, `-proc:none` trong image Java đã cấu hình, mặc định `eclipse-temurin:21-jdk`. Không chạy javac/java của bài nộp trên host và không chạy Maven/script trong gói.

Container tắt network, giới hạn CPU/RAM/PID, root filesystem chỉ đọc, bỏ capabilities, không cấp Docker socket/credentials. Giới hạn time/memory/output lấy từ config đã được importer kiểm tra. Code và fixture runtime mount chỉ đọc. Expected giữ trên server.

Trọng số thập phân được lưu theo đơn vị một phần triệu để không làm tròn 33.333333 thành 33. Điểm tổng tối đa 100; AC chỉ khi tất cả test đạt. Run chỉ dùng public test; Submit dùng public + hidden. Các verdict CE/WA/RE/TLE/MLE đã được gây lỗi có chủ đích và xác minh trong Docker thật.

## 6. Chạy hệ thống và duyệt đề

1. Mở Docker Desktop, đợi Engine sẵn sàng. Kiểm tra:

   ```powershell
   docker version
   docker image inspect eclipse-temurin:21-jdk
   ```

2. Chạy từ thư mục workspace:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
   ```

3. Mở `http://localhost:8080`, hoặc `http://IP-máy-server:8080` từ máy LAN. Đăng nhập lại bằng tài khoản/mật khẩu cũ sau khi server khởi động lại.
4. Nhấn Ctrl+Shift+R để tải giao diện mới. Teacher mở **Ngân hàng câu hỏi → Tất cả bài tập**: tổng 100. **Theo Week**: 12 Week, đúng liên kết.
5. Chọn các đề đã rà soát, nhấn **Duyệt phát hành OJ** và xác nhận. Có thể chọn nhiều đề qua các trang. Server chỉ cho phát hành đề của mình đã qua reference và kiểm tra starter sai.
6. Student chỉ thấy đề đã phát hành. Mở bài sẽ thấy tên file đúng: Main.java / Solution.java / tên class của đề. Thử Run sample, nộp code hoặc nạp file Java, xem verdict và lịch sử.

Chưa phát hành thì Student không thấy bài là hành vi có chủ đích theo yêu cầu của gói, không phải lỗi kết nối database.

Launcher đã đổi sang cache class theo hash toàn bộ source/build inputs, tránh tái dùng class phụ thuộc API MySQL cũ sau khi đổi kiểu repository.

## 7. Import và kiểm định lại

### Trên giao diện

Xem [hướng dẫn nhập ZIP số lượng linh hoạt](NHAP_GOI_OJ_ZIP_VI.md). Từ bản cập nhật mới, import giao diện là bổ sung bài và giữ liên kết Week cũ, không thay toàn bộ ngân hàng; không bắt buộc 100 bài/12 Week. Teacher mở **Nhập gói OJ ZIP**, chọn file, xem số bài/Week/test và danh sách trước khi xác nhận. Commit mới thêm dữ liệu hoặc tạo phiên bản cho mã bài thuộc quyền quản lý của người nhập; không cho Teacher thay gói thuộc giáo viên khác. Preview có thời hạn và ràng buộc tài khoản. Đề mới vẫn là DRAFT, kết quả kiểm định cũ chỉ được dùng lại cho cùng snapshot.

### CLI cho người vận hành

Từ `StudyFix/server`, với classes/JAR vừa biên dịch:

```powershell
java --enable-native-access=ALL-UNNAMED -cp "dist/studyfix.jar;lib/*" studyfix.assignment.catalog.OjCatalogCli preview private/catalogs/CodeBank_Java_100_OJ_Complete.zip

# Chỉ dùng replace khi chủ động thay toàn bộ ngân hàng hoạt động và đã sao lưu.
java --enable-native-access=ALL-UNNAMED -cp "dist/studyfix.jar;lib/*" studyfix.assignment.catalog.OjCatalogCli replace data/studyfix-learning.db private/catalogs/CodeBank_Java_100_OJ_Complete.zip USER_ID_GIAO_VIEN

java --enable-native-access=ALL-UNNAMED -cp "dist/studyfix.jar;lib/*" studyfix.assignment.catalog.OjCatalogCli verify data/studyfix-learning.db eclipse-temurin:21-jdk reports/OJ_100_SANDBOX_VERIFICATION.json
```

`verify` lưu kết quả theo từng snapshot. Nếu bị gián đoạn, chạy lại sẽ dùng bằng chứng đã hoàn thành cho snapshot không thay đổi và chạy phần còn thiếu. Không tự phát hành đề sau verify. Không dùng `verify_reference.py` trong gói làm production judge hoặc chạy code sinh viên trên host.

Kiểm định cả bộ ZIP vẫn có CLI. Từ bản hoàn thiện Builder 4–7, nút Sửa của đề OJ mở bản nháp phiên bản mới trong Problem Builder, giữ cấu trúc Program/Method/Class và file nộp. Phiên bản mới cần kiểm định, duyệt và phát hành lại; bản giao/kỳ thi cũ giữ nguyên snapshot. Xem `PROBLEM_BUILDER_4_7_HUONG_DAN_VI.md` để biết quy trình và giới hạn runner.

## 8. File source chính và kiểm thử

| File | Vai trò |
| --- | --- |
| `ManagementConfig.java`, `ManagementDatabase.java`, `ManagementSql.java`, `TeacherRepository.java` | SQLite management và tương thích dialect để migration |
| `Store.java`, `ClassAdmission.java`, `AdminManagement.java`, các điểm gọi management | Giữ identity, auth và nghiệp vụ lớp/LAN |
| `db/sqlite-management-schema.sql` | Schema quản lý SQLite |
| `assignment/catalog/OjPackage.java` | ZIP validation, manifest/Week/file/test mapping và correction JAVA092 |
| `assignment/catalog/OjCatalogCli.java` | Preview/replace/verify/resume |
| `assignment/persistence/OjRepository.java` | Snapshot riêng, import transaction, validation và duyệt phát hành |
| `assignment/grading/OjJavaSandbox.java` | Adapters Program/Method/Class, fixture file, grading trong Docker |
| `assignment/service/OjImportService.java` | Preview có thời hạn và commit ràng buộc Teacher |
| `AssignmentService.java`, `AssignmentTcpServer.java`, `WebGatewayServer.java` | Nối API/queue/publish/import và health vào kiến trúc cũ |
| `web-client/static/features/exercises/oj-import.js` | Upload, preview và xác nhận import thật |
| `practice.js`, `teacher-bank.js`, `question-bank-view.js`, `week-bank-view.js` | Hiển thị đề/Week, filter cấu trúc, duyệt phát hành và tên file đúng |

Test thực tế:

- `SqliteManagementIntegrationTest`: SQLite đầy đủ, không dùng chế độ legacy; auth, quyền, admission, IP/attendance, khóa/đổi mật khẩu và persistence.
- `OjIntegrationTest`: import 100/12, nhập lại, snapshot cũ, chưa duyệt không public, Run sample/Submit qua HTTP/queue/Docker cho JAVA001/028/064, kiểm tra DTO không lộ solution/hidden.
- `OjVerdictIntegrationTest`: Docker thật CE/WA/RE/TLE/MLE.
- `OjImportIntegrationTest`: upload ZIP nhị phân qua HTTP thật, preview không sửa dữ liệu, commit đủ 100 bài/12 Week, nhập lại không trùng, ZIP lỗi trả 400, Student bị từ chối, ZIP riêng không truy cập được từ web và giảng viên khác không được chiếm quyền sở hữu bài/Week; lỗi được rollback toàn bộ.
- `oj_browser.cjs`: Chrome thật hiển thị ba dạng, starter/tên file, Teacher 100 đề và 12 Week; ảnh tại `server/build/oj-qa`.
- Suite ngân hàng, Week, practice, exam và integration hiện có; 20 kiểm tra frontend. Test hồi quy legacy được đánh dấu bằng `studyfix.legacy.sqlite`; test SQLite management mới chủ động tắt chế độ này.

Ví dụ lệnh Ant:

```powershell
ant sqlite-management-test oj-test oj-import-test oj-verdict-test integration-test
```

Muốn bật Chrome trong OJ test, đặt `STUDYFIX_BUILDER_BROWSER_MODULES` tới thư mục `node_modules` có Playwright. Các test dùng database tạm và cổng ngẫu nhiên; không tác động database thật.

Bằng chứng được lưu tại `server/reports/OJ_100_SANDBOX_VERIFICATION.json`, `server/reports/SQLITE_MIGRATION_AUDIT.json` và `server/reports/SQLITE_OJ_FRONTEND_TESTS.json`. Log upload test: `server/build/oj-import-test.log`; log hồi quy tổng: `server/build/sqlite-oj-full-regression.log`. Ảnh kiểm tra giao diện trong `docs/previews/OJ_SQLITE/` dùng database kiểm thử, trong đó ba bài được phát hành để kiểm tra Student; database sử dụng thực tế vẫn giữ 100 bài DRAFT chờ giảng viên duyệt.

## 9. Sao lưu và phạm vi chất lượng

Sao lưu trước chuyển đổi nằm tại `server/backups/before-sqlite-oj-20261109-041213/`, gồm hai SQLite cũ, `.env` cũ và bản sao dữ liệu management MySQL sang SQLite. Thư mục backup bị loại khỏi Git và không được phục vụ qua web. MySQL nguồn không bị xóa/sửa bởi công cụ copy.

Khôi phục phải dừng server và đối chiếu thay đổi sau thời điểm backup; không chép đè một phần database đang chạy hoặc phục hồi snapshot cũ làm mất hoạt động mới. Không đưa file `.env` backup lên kho công khai.

100 starter bị từ chối chỉ chứng minh bộ test phát hiện được các lời giải khởi đầu chưa hoàn chỉnh; **không chứng minh bộ test chống được mọi lời giải sai hoặc đủ độ bao phủ giáo dục**. Giáo viên vẫn cần đọc đề, bổ sung phản ví dụ/biên và duyệt nội dung trước khi dùng thi chính thức. Các kết quả được báo đúng phạm vi đã chạy.
