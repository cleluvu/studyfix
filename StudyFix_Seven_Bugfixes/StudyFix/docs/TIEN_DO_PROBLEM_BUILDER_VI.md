# Problem Builder — trạng thái triển khai và kiểm thử

Cập nhật 09/11/2026 sau khi hoàn thiện luồng bước 4–7 trên SQLite.

Điểm mở hiện tại: **Ngân hàng câu hỏi → Tất cả bài tập → Tạo bài tập**. Wizard mở trong hộp thoại trên danh sách; đã bỏ trang và mục sidebar tạo bài riêng. Kiểm thử thay đổi này: `server/build/builder-dialog-verified.log`.

**Bản hiện tại đã mở đủ bảy bước:** quản lý testcase/nhóm/import/generator; cấu hình chấm Exact/Token/Float và điểm; kiểm định Docker thật; review theo hash, phát hành snapshot, liên kết Week, lưu trữ và tạo phiên bản mới. Program/Method/Class dùng cùng OJ runner với ngân hàng đã nhập. Bản đã giao/kỳ thi cũ được giữ nguyên.

Hướng dẫn sử dụng, source, API, migration, ví dụ ba cấu trúc và giới hạn: [PROBLEM_BUILDER_4_7_HUONG_DAN_VI.md](PROBLEM_BUILDER_4_7_HUONG_DAN_VI.md).

Test mới: `BuilderCompleteIntegrationTest` và `builder_complete_browser.cjs`, dùng HTTP/SQLite/Chrome/Docker thật trên database tạm. Bằng chứng trong `server/build/builder-complete-release.log`, `server/build/builder-complete-regression.log`, `server/build/builder-complete-qa/` và `server/reports/BUILDER_COMPLETE_VERIFICATION.json`.

Phạm vi runner: một file Java được sửa/nộp và helper Java công khai chỉ đọc, default package, Java17. Method/Class dùng trusted snippet, có thể kiểm tra constructor/method/reflection/exception. Chưa tích hợp JUnit riêng, Maven dependencies, Java package project hay custom checker tải lên. Không coi compile thành công là AC hoặc coi một lời giải sai mẫu là bằng chứng đủ độ bao phủ.

<details>
<summary>Báo cáo giai đoạn 1–3 trước đây — lưu để đối chiếu, không phải trạng thái hiện tại</summary>

## 1. Phần đã triển khai

| Phần | Hoạt động hiện tại |
| --- | --- |
| Bản nháp | Lưu database trên server, tách riêng đề đã xuất bản; tự lưu, khôi phục sau reload, lịch sử và khôi phục thành revision mới |
| Chống ghi đè | `expectedRevision`, hash nội dung và retry không nhân đôi; cửa sổ cũ nhận lỗi xung đột và phải mở lại |
| Bước 1 — Thông tin | Tên, mã, chuẩn đầu ra, mô tả, độ khó, thời lượng dự kiến, chọn các Week do chính giáo viên quản lý |
| Bước 2 — Đề bài | Soạn Markdown, toolbar, xem trước, đầu vào/ra, ràng buộc, ví dụ, giải thích và trường hợp biên |
| File đính kèm | Upload PNG/JPEG/PDF bằng HTTP có Bearer auth; ảnh xem trước và PDF tải xuống; kiểm tra chữ ký định dạng, dung lượng và quyền tác giả |
| Bước 3 — Template | Monaco đóng gói nội bộ, cây thư mục/file, thêm/xóa/sửa file, phân loại PUBLIC/TEACHER/JUDGE và quyền sửa; giữ instance, focus và Undo khi ứng dụng render lại |
| Import ZIP | Đọc file UTF-8 vào bản nháp; không giải nén ra đường dẫn người dùng, không chạy Maven/pom/script; chặn traversal, symlink, ZIP bomb, tên trùng và file/thư mục xung đột |
| Biên dịch template | `javac` thật trong Docker, giới hạn tài nguyên dùng lại từ sandbox; không chạy source trên máy chủ; lưu kết quả kèm hash |
| Kết quả cũ | Sửa nội dung làm kết quả compile cũ hiện “cần biên dịch lại”, kể cả sau reload |
| Phân quyền | Chỉ Teacher dùng Builder; giáo viên khác và Student không đọc/sửa draft, source, lịch sử hoặc asset riêng |

Chuyển bước được kiểm tra tại server. Gọi thẳng `SAVE` để bỏ qua bước bị từ chối. Bản nháp có thể chưa đầy đủ, nhưng chỉ được sang bước kế tiếp sau khi các trường bắt buộc của bước hiện tại hợp lệ.

## 2. Phần chưa hoàn tất

- Bước 4: quản lý testcase/group, import `.in/.out` và generator.
- Bước 5: cấu hình checker, phân bổ điểm và chính sách phản hồi.
- Bước 6: chạy lời giải chuẩn/lời giải sai, kiểm định toàn bộ nội dung và lưu báo cáo kiểm định.
- Bước 7: review, xuất bản snapshot, tài nguyên dành cho sinh viên và tích hợp snapshot mới với giao bài/kỳ thi.
- Runner Method/Class, JUnit/harness và kiểm tra cấu trúc lớp.
- Preview công thức toán, bộ kiểm tra nội dung chuyên biệt Method/Class và luồng reviewer.

Các bước 4–7 và Method/Class chưa được bật. Compile thành công chỉ xác nhận các file Java công khai biên dịch được; **không chứng nhận lời giải AC, testcase đúng hay đủ điều kiện xuất bản**. Các chức năng ngân hàng đề cũ vẫn hoạt động theo luồng hiện có; chưa áp dụng chính sách xuất bản mới cho chúng.

## 3. Các file chính

### Backend

| File, dưới `server/src/studyfix/` | Nhiệm vụ |
| --- | --- |
| `assignment/persistence/ProblemBuilderRepository.java` | Migration cộng thêm, draft/history, mã bài, quyền tác giả, revision, hash, nhật ký compile |
| `assignment/persistence/ProblemBuilderAssets.java` | BLOB, quota, SHA-256 và tải file có kiểm tra quyền |
| `assignment/persistence/ProblemTemplate.java` | Kiểm tra file/path/visibility và đọc ZIP có giới hạn |
| `assignment/service/ProblemBuilderService.java` | Dispatch API Builder qua AssignmentService hiện có; compile tối đa một lượt đồng thời |
| `assignment/service/ProblemBuilderChecks.java` | Kiểm tra nội dung trước khi chuyển bước |
| `assignment/grading/DockerJavaSandbox.java` | Bổ sung compile-only nhiều file; phương thức chấm bài cũ giữ nguyên |
| `assignment/service/AssignmentService.java` | Khởi tạo và chuyển request cho Builder |
| `assignment/transport/AssignmentTcpServer.java` | Cung cấp service cho gateway binary |
| `WebGatewayServer.java` | Whitelist action, upload/download xác thực và CSP cho blob ảnh/Monaco worker |

### Frontend

Các file trong `web-client/static/features/problem-builder/`:

- `state.js`, `api.js`, `controller.js`: trạng thái, API, tự lưu, xung đột, điều hướng và upload.
- `general-step.js`, `statement-step.js`, `template-step.js`, `view.js`: ba bước và bố cục wizard.
- `markdown.js`: tạo DOM mới với danh sách thẻ/thuộc tính cho phép; không chèn trực tiếp HTML từ Markdown.
- `assets.js`: tải ảnh qua Bearer auth, blob URL và thu hồi cache khi đổi tài khoản.
- `editor.js`: lifecycle Monaco, fallback textarea khi tài nguyên editor không tải được.
- `static/problem-builder.css`: layout tím, ba cột, cây file và responsive.
- `static/vendor/`: Marked và Monaco nội bộ, kèm license và thông tin phiên bản.

Router, sidebar, điều hướng, logout và hook sau render dùng các module hiện có: `app.js`, `core/state.js`, `core/roles.js`, `core/polling.js`, `core/events.js`, `core/navigation-guards.js`, `ui/render.js`, `index.html`.

## 4. Database và API

Không đổi database quản lý tài khoản/lớp. Builder dùng **cùng AssignmentRepository với ngân hàng đề hiện có**, mặc định là SQLite `server/data/studyfix-learning.db`.

Các bảng mới được tạo cộng thêm khi service khởi động:

- `p3_builder_drafts`: payload nháp, tác giả, revision, bước và hash.
- `p3_builder_history`: snapshot mỗi lần lưu có thay đổi.
- `p3_builder_sequences`: gợi ý mã JAVA, kiểm tra trùng lại khi lưu.
- `p3_builder_assets`: nội dung binary, loại file, size và SHA-256.
- `p3_builder_checks`: kết quả compile theo hash và thời gian.

Không xóa/reseed database thật, không chuyển bài đang giao sang version nháp. Có xử lý bổ sung cột `creation_hash` khi gặp bảng draft từ phiên bản triển khai trung gian.

Actions qua `POST /api/assignments`: `BUILDER_CAPABILITIES`, `BUILDER_LIST`, `BUILDER_GET`, `BUILDER_CREATE`, `BUILDER_SAVE`, `BUILDER_ADVANCE`, `BUILDER_HISTORY`, `BUILDER_RESTORE`, `BUILDER_DELETE_DRAFT`, `BUILDER_SUGGEST_CODE`, `BUILDER_ASSETS`, `BUILDER_TEMPLATE_IMPORT`, `BUILDER_COMPILE`.

Binary routes:

```text
POST /api/problem-builder/uploads/{draftId}?name={filename}
GET  /api/problem-builder/assets/{assetId}
Authorization: Bearer <token>
```

Không đặt token vào URL. Không phục vụ file upload trong thư mục static công khai.

Giới hạn hiện tại: JSON nháp 96 KiB; 100 draft/tác giả; file upload 2 MiB; tổng asset 8 MiB/100 file/draft; ZIP template 100 entry, 32 file, tổng giải nén 64 KiB, đường dẫn 160 ký tự. Template chỉ nhận `.java/.md/.txt/.json/.xml/.properties` dạng UTF-8. Không tự tải thư viện ngoài hoặc thực thi cấu hình build từ ZIP.

## 5. Chạy và thử trên giao diện

1. Nếu Server Manager đang chạy, dừng dịch vụ và đóng tiến trình server cũ.
2. Mở Docker Desktop, đợi Engine hoạt động. Kiểm tra:

   ```powershell
   docker version
   docker image inspect eclipse-temurin:21-jdk
   ```

   Nếu chưa có image, chạy `docker pull eclipse-temurin:21-jdk`. Nếu dự án đang cấu hình image khác, dùng đúng image đó.

3. Từ thư mục gốc workspace, chạy launcher hiện có:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
   ```

4. Mở địa chỉ web hiện dùng, nhấn Ctrl+Shift+R, đăng nhập Teacher, chọn **Ngân hàng câu hỏi → Tạo bài tập**.
5. Tạo nháp, điền các trường bắt buộc bước 1; chọn Week của bạn nếu cần. Chờ “Đã lưu”, tải lại trang và kiểm tra dữ liệu vẫn còn.
6. Nhấn **Tiếp theo**. Nhập Markdown, đầu vào/ra, ràng buộc, ví dụ; thử **Thêm ảnh** và **Đính kèm PDF**.
7. Sang **Template**, sửa `Main.java`, thêm `Helper.java` hoặc nhập ZIP. Chọn file test để kiểm tra quyền JUDGE và checkbox sửa bị tắt.
8. Nhấn **Kiểm tra biên dịch**. Java hợp lệ trả `COMPILED`, sai cú pháp trả `COMPILE_ERROR`. Docker/image không sẵn sàng phải báo lỗi, không báo đạt.
9. Sau khi compile thành công, sửa một dòng: kết quả phải chuyển sang cần biên dịch lại. Reload vẫn giữ nhận định đó.
10. Mở cùng nháp ở hai cửa sổ Teacher đã đăng nhập: cửa sổ lưu sau với revision cũ phải nhận xung đột; không được âm thầm ghi đè.

Hiện nút Tiếp theo ở bước Template bị tắt vì các bước sau chưa hoàn tất.

## 6. Kiểm thử tự động và phạm vi bằng chứng

Tất cả native test dùng database tạm trong `server/build`, cổng ngẫu nhiên, tài khoản fixture riêng. Không dùng dữ liệu thật của người dùng.

Tại `StudyFix/server`, sau khi cấu hình Java/Ant:

```powershell
$env:STUDYFIX_BUILDER_DOCKER_TEST='1'
$env:STUDYFIX_BUILDER_BROWSER_MODULES='C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules'
ant problem-builder-test
```

Biến `STUDYFIX_BUILDER_BROWSER_MODULES` trỏ tới thư mục `node_modules` có Playwright trên máy chạy test. Khi không đặt biến, test backend vẫn chạy; không được coi đó là đã chạy Chrome. Khi không đặt biến Docker bằng `1`, không được coi đó là đã chạy biên dịch Docker thật.

Test source: `server/test/studyfix/ProblemBuilderIntegrationTest.java`, `web-client/tests/problem_builder_browser.cjs`.

Bằng chứng lưu trong `server/build`:

- `problem-builder-regression.log`: Builder + ngân hàng đề/Week/luyện code/Exam Mode.
- `problem-builder-phase4-final.log`: lượt kiểm tra Java/Chrome/Monaco/Docker.
- `problem-builder-final.log`: lượt cuối, bổ sung ZIP bomb/symlink và giữ focus/Undo qua render.
- `problem-builder-web-regression.json`: 20 kiểm tra frontend hiện hành; các bài có mock được ghi rõ trong output.
- `problem-builder-qa/`: ảnh desktop/mobile cho ba bước.

Không dùng các kết quả giai đoạn 1–3 này để chứng nhận bước 4–7; các bước mới có bộ kiểm thử riêng nêu phía trên.

</details>
