# Soạn và lưu bài lỗi để dùng lại khi dạy học

## Chuẩn bị bài trước buổi học

1. Đăng nhập giáo viên. Mở **Ngân hàng bài tập → Danh sách bài lỗi**. Không cần tạo hay tham gia phòng.
2. Chọn **Soạn bài lỗi**, rồi chọn bài nguồn. Code chuẩn tự điền vào vùng code để bạn chỉnh sửa. Đề và bản code chuẩn chỉ đọc có trong mục mở rộng để đối chiếu.
3. Đặt tên bài lỗi. **Tự sửa code chuẩn thành code có lỗi** theo mục tiêu buổi học. Không còn nhóm lỗi, số lỗi, tạo biến thể tự động hay danh sách bản xem trước gần đây trong giao diện này.
4. Bấm **Lưu vào danh sách bài lỗi**. Hệ thống lưu code trước, rồi kiểm chứng bằng sandbox: code chuẩn phải đạt toàn bộ testcase, code giáo viên soạn phải thể hiện lỗi.
5. Khi hiển thị **Sẵn sàng giao**, bài có thể được chọn trực tiếp trong phòng học. Bản không đạt kiểm chứng vẫn giữ code trong danh sách với trạng thái **Cần chỉnh sửa**, để giáo viên mở lại sửa/lưu tiếp.

Bài nguồn cần được xuất bản trong ngân hàng luyện tập, thuộc quyền quản lý của giáo viên, có code chuẩn và testcase, cấu trúc chương trình một file `Main.java`. Không lấy bài kỳ thi riêng tư hay lời giải thuộc giáo viên khác. Code tối đa 32000 byte UTF-8; tên bài lỗi từ 1–160 ký tự.

Code có lỗi có thể là sai kết quả (WA), lỗi biên dịch (CE), lỗi khi chạy (RE), quá thời gian/bộ nhớ/đầu ra. Lỗi hạ tầng Docker không được coi là một bài lỗi hợp lệ. Code còn đạt toàn bộ testcase không được đánh dấu sẵn sàng giao.

## Chọn nhanh khi dạy học

1. Trong phòng mình làm chủ, chọn **Bắt đầu giảng bài**.
2. Chọn **Chọn bài lỗi đã lưu** → tìm bài → **Chọn bài**. Chỉ bài đã kiểm chứng mới bật nút chọn. Không chạy kiểm chứng lại trong thao tác này.
3. Bài lỗi được đưa vào Master. Khi học sinh đã vào phòng, bấm **Bắt đầu sửa lỗi** để tạo nhánh riêng.
4. Học sinh sửa code và **Nộp bài**. Kết quả ở dưới phần code/thanh nút: trạng thái đang chấm, AC/WA/CE/RE..., số testcase đạt và lỗi test công khai. Lưu/tự lưu chỉ giữ code, chưa chạy test.

Có thể **Soạn bài lỗi** ngay trong phòng rồi lưu vào danh sách để dùng lại. Đáp án chỉ giáo viên chủ phòng được xem. Các bản Master/nhánh và testcase của bài đã giao là snapshot độc lập: cập nhật, xóa bài lỗi hay xóa bài nguồn đều không thay đổi bài học sinh đang làm.

## Danh sách và phiên bản

- Mỗi giáo viên có danh sách riêng; học sinh không được truy cập API danh sách, code chuẩn hoặc thao tác lưu/xóa.
- Bài đã lưu tồn tại qua tải lại trang và khởi động lại server. Có tìm theo tên bài/tên hoặc mã bài nguồn, xem/sửa và xóa khỏi danh sách.
- Sửa bài dùng phiên bản kỳ vọng để tránh ghi đè thao tác từ cửa sổ khác. Khi chọn bài trong phòng cũng kiểm tra phiên bản đang hiển thị trong danh sách.
- Xóa là xóa khỏi danh sách; snapshot đã giao tiếp tục tồn tại.
- Bài đang kiểm chứng không được sửa/xóa/giao. Mỗi giáo viên có tối đa một bài đang kiểm chứng; hàng đợi toàn dịch vụ có 4 vị trí.
- Nếu đóng hộp thoại trong lúc kiểm chứng, mở lại bài trong danh sách để xem trạng thái. Nếu server dừng giữa chừng, code vẫn được giữ và chuyển sang Cần chỉnh sửa; mở bài, bấm lưu để kiểm chứng lại.
- Sau khi đã lưu, bài giữ snapshot code chuẩn/testcase của nguồn ban đầu. Bạn vẫn sửa và dùng lại bài đó khi bài nguồn thay đổi hoặc bị xóa. Muốn đổi nguồn, tạo bài lỗi mới.

## Cập nhật chương trình

Khởi động lại Server Manager sau cập nhật backend và tải lại trình duyệt bằng Ctrl+F5:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\run-server-manager.ps1
```

Chạy từ thư mục StudyFix; dừng/đóng Server Manager cũ trước. Cần Docker đang chạy và image Java `eclipse-temurin:21-jdk` đã có để kiểm chứng và chấm code.

## Kỹ thuật và API

Bảng mới `classroom_debug_library` trong database lõi lưu owner, phiên bản, trạng thái, snapshot nội dung và thời gian. `DebugLibraryService` kiểm chứng thủ công, `DebugLibraryRepository` lưu bền vững, `SELECT_LIBRARY` tạo snapshot riêng trong `classroom_debug_lessons`. Không xóa dữ liệu bài/phòng cũ; các API preview cũ được giữ tương thích nhưng không còn là giao diện soạn bài chính.

| API | Chức năng |
| --- | --- |
| GET `/api/debug-library/sources` | Bài nguồn được phép soạn |
| GET `/api/debug-library/source/{id}` | Đề và code chuẩn cho giáo viên |
| GET `/api/debug-library` | Danh sách bài lỗi của giáo viên, metadata không có đáp án |
| POST `/api/debug-library` | Lưu bài lỗi thủ công: sourceId, title, code; HTTP 202 |
| GET `/api/debug-library/{id}` | Đọc bài của mình và trạng thái kiểm chứng, không trả raw testcase |
| POST `/api/debug-library/{id}` | Sửa title/code, expectedVersion; kiểm chứng lại |
| POST `/api/debug-library/{id}` với action=DELETE | Xóa khỏi danh sách, expectedVersion |
| POST `/api/classroom` với action=SELECT_LIBRARY | Chọn libraryId/libraryVersion, expectedPhase/round/expectedVersion của phòng |

## Kiểm thử

Từ `StudyFix/server` với JDK 26/Ant:

```powershell
ant -Dbuild.dir=build-debug-lessons debug-library-test
ant -Dbuild.dir=build-debug-lessons debug-library-docker-test
```

Target đầu dùng HTTP/SQLite thật và javac thực thi fixture cố định do test cung cấp. Target Docker dùng DockerJavaSandbox thật. Tính năng sản phẩm luôn chạy code trong sandbox, không chạy code học sinh trên host.

Các tình huống đã kiểm chứng: soạn trước khi vào phòng; lấy code chuẩn; lưu code do giáo viên sửa; giữ code khi kiểm chứng không đạt; CE thủ công; lưu/sửa/tải lại/restart; phân quyền giáo viên/học sinh; chống ghi đè; chọn bài không chấm lại; dùng lại nhiều phòng; sửa/xóa không thay bản đã giao; xóa nguồn không mất bài lỗi; học sinh sửa đúng đạt AC.

Từ `StudyFix`, đặt `STUDYFIX_BROWSER_TEST_MODULES` trỏ đến node_modules có Playwright:

```powershell
node web-client/tests/debug_lessons_browser.cjs
node web-client/tests/classroom_debug_only_browser.cjs
node web-client/tests/classroom_results_browser.cjs
node web-client/tests/check_classroom_template.mjs
```

Chrome dùng DOM/CSS thật và HTTP fixture; backend được kiểm tra riêng bằng HTTP/SQLite/Docker thật. Ảnh QA: `server/build-debug-lessons/library-browser/`; log backend: `server/build-debug-lessons/debug-library-test.log`. Bộ kiểm thử kết quả học sinh xác nhận thanh Lưu/Nộp/tải code không bị cắt ở 5 kích thước màn hình, kết quả nằm dưới code, không lộ testcase ẩn và tự cập nhật WA/AC/CE.
Các dòng mờ trong bộ chọn ghi lý do cụ thể: thiếu code chuẩn, chưa có testcase, vượt giới hạn code hoặc Method/Class chưa được hỗ trợ. Ví dụ JAVA060 là Method với Solution.java, đã có code chuẩn và testcase nên không phải thiếu dữ liệu; chưa dùng được trong luồng Program/Main.java hiện tại.
