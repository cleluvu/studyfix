# Tạo bài tập Java — bước 4 đến 7

Ngày cập nhật: 09/11/2026. Backend Java, frontend JavaScript và SQLite hiện có được giữ nguyên. Bài tạo từ Builder dùng cùng OJ sandbox và hàng đợi nộp bài với ngân hàng 100 bài. Các bài đã nhập không bị thay thế bởi việc hoàn thiện Builder.

## 1. Cách mở và sử dụng

1. Mở Docker Desktop, đợi Engine hoạt động. Image hiện dùng: `eclipse-temurin:21-jdk`; Java source được biên dịch với `--release 17`.
2. Nếu chưa chạy server, từ workspace thực hiện:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\StudyFix\server\run-server-manager.ps1
   ```

3. Mở `http://localhost:8080`, nhấn Ctrl+Shift+R, đăng nhập Teacher, chọn **Ngân hàng câu hỏi → Tất cả bài tập → Tạo bài tập**. Bộ tạo bài mở trong hộp thoại ngay trên danh sách, không có trang hoặc mục sidebar riêng.
4. Tiếp tục nháp cũ hoặc tạo nháp mới. Bước 1 chọn Program/Method/Class, đặt mã, tên, dạng bài/chủ đề và Week. Bước 2 nhập đề. Bước 3 tạo template.
5. Sau khi hoàn thành template, nút **Tiếp theo** mở bước 4. Các trường bắt buộc được kiểm tra tại server khi chuyển bước; lưu nháp vẫn cho phép nội dung đang soạn chưa đầy đủ.

Bộ tạo bài là **hộp thoại bảy bước trên trang Tất cả bài tập**. Sidebar không còn mục tạo bài riêng. Nhấn Đóng hoặc Escape khi không còn thao tác tải/lưu đang chờ sẽ lưu thay đổi rồi đóng; nếu lưu lỗi, hộp thoại giữ nguyên để xử lý. Đóng hộp thoại trả quyền thao tác và cập nhật danh sách ngân hàng. Mở lại bằng cùng nút để tiếp tục nháp; dùng **Bản nháp của tôi** trong hộp thoại để chọn nháp khác hoặc xem các đề chờ kiểm duyệt. Reload trở về danh sách, không mở trang Builder riêng; nháp đã lưu vẫn mở lại được.

## 2. Hợp đồng template và nộp bài

Ứng dụng hiện sử dụng **một file Java sinh viên được sửa/nộp**. Các file Java công khai khác là file hỗ trợ chỉ đọc, được compile cùng file nộp. Java source chạy OJ ở thư mục gốc, default package.

- Program: `Main.java`, class `Main`, `public static void main(String[] args)`.
- Method: thường là `Solution.java`, định nghĩa phương thức đúng API trong đề.
- Class: tên file trùng public class, ví dụ `Student.java`, constructor và method theo đề.
- Chọn đúng một file PUBLIC được sửa; tên file đó phải trùng **Tên file sinh viên nộp**.
- File TEACHER/JUDGE giữ riêng, không tự đưa vào source hay response sinh viên. Test chạy được cấu hình ở bước 4; không tự chạy file test/script/pom.xml trong ZIP template.
- Có thể upload template ZIP từ bước 3. File nhập là văn bản có giới hạn và được kiểm tra đường dẫn; không thực thi cấu hình build tải lên.

## 3. Bước 4 — testcase

### Nhập, sửa và phân nhóm

Nhấn **Thêm test case**, chọn test ở bảng rồi sửa ở panel bên phải:

- ID duy nhất, 1–40 ký tự chữ/số/`_`/`-`.
- Input hoặc Java snippet; expected output (được phép rỗng).
- Nhóm Basic/Edge/Performance hoặc nhóm tự tạo.
- Public hoặc Hidden.
- Điểm dương, tối đa sáu chữ số thập phân.

**Public** dùng cho ví dụ/Run sample; **Hidden** chỉ Judge nhận khi nộp toàn bộ bài. Cần ít nhất một test Public. Không có Hidden là cảnh báo cần giảng viên cân nhắc, không tự bịa test ẩn.

Có thể chọn nhiều test để xóa/nhân bản. Nhân bản tạo ID mới nhưng cần sửa dữ liệu trùng trước khi chuyển bước. Nhóm đang có test không được xóa; chuyển test sang nhóm khác trước. Lịch sử lưu nháp giữ snapshot testcase, nhóm và điểm trước/sau mỗi lần sửa.

### Import file

- Nút **Import .in/.out**: chọn đồng thời các cặp `one.in`, `one.out`, `two.in`, `two.out`…
- Method/Class có thể dùng cặp `.snippet` và `.expected`.
- Nút **Import ZIP**: cùng quy ước cặp tên, ví dụ:

  ```text
  public/sample.in
  public/sample.out
  hidden/edge.in
  hidden/edge.out
  ```

Mỗi cặp phải đầy đủ. ZIP không được chứa đường dẫn vượt thư mục, symlink, file trùng hoặc dữ liệu không phải UTF-8. Giới hạn 100 test; 32 KiB/file, 256 KiB tổng giải nén; nội dung input/expected tối đa 16000 ký tự. Nháp JSON tối đa 96 KiB nên bộ test lớn hơn cần chia nhỏ, không âm thầm cắt dữ liệu.

Import chỉ tạo **bản xem trước**. Kiểm tra danh sách rồi chọn thêm hoặc thay toàn bộ, nhấn **Áp dụng vào bản nháp**. File lỗi không làm hỏng bộ test đang có.

### Sinh test và tạo expected

Generator có quy tắc hỗ trợ Program nhận một số nguyên hoặc `n` và mảng số nguyên. Nhập phạm vi, số test, kích thước mảng và seed; cùng seed/cấu hình tạo cùng dữ liệu. Method/Class dùng snippet do giáo viên soạn, không tự đoán API.

Sau khi áp dụng input được sinh:

1. Nhập **lời giải chuẩn**.
2. Nhấn **Chạy lời giải chuẩn → expected**.
3. Theo dõi lượt chạy trong bước Kiểm định hoặc cập nhật kết quả.
4. Mở kết quả **Xem expected và áp dụng**; xác nhận thay testcase sau khi kiểm tra.

Expected là stdout do Docker thực tế trả về, không dùng AI/phép suy đoán. Lượt chạy không tự sửa draft. Tổng preview expected giới hạn 24 KiB; nếu vượt, giảm kích thước bộ test. Nháp thay đổi sau lượt chạy phải tạo expected lại.

### Ví dụ Program

Template:

```java
public class Main {
    public static void main(String[] args) {
        // TODO
    }
}
```

Test Public: input `1 2\n`, expected `3\n`, 50 điểm. Test Hidden: input `-2 8\n`, expected `6\n`, 50 điểm. `\n` trong ví dụ này có nghĩa ký tự xuống dòng thật khi nhập form/file.

Lời giải chuẩn:

```java
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);
        System.out.println(s.nextInt() + s.nextInt());
    }
}
```

### Ví dụ Method

File nộp `Solution.java`; template có phương thức `public static int sum(int a, int b)` chưa hoàn thiện. Test input là Java snippet:

```java
System.out.println(Solution.sum(1, 2));
```

Expected: `3` và xuống dòng. Snippet được ghép vào trusted harness bên trong Docker. Lời giải chuẩn là toàn bộ file `Solution.java`, không chỉ thân phương thức.

### Ví dụ Class

File nộp `Student.java`, API `Student(String name)` và `String getName()`. Test:

```java
Student student = new Student("Lan");
System.out.println(student.getName());
```

Expected: `Lan` và xuống dòng. Có thể kiểm tra constructor/method qua reflection, hoặc dùng `try/catch`/`AssertionError` trong snippet để kiểm tra exception và hành vi. Các đoạn kiểm tra này vẫn chạy trong Docker, không chạy trong JVM server.

## 4. Bước 5 — cấu hình chấm

- Time limit: 100–10000 ms mỗi test; memory: 64–512 MiB; output: 1–2048 KiB.
- Target Java 17, chạy trong image JDK 21 hiện có; không chỉ đổi nhãn giao diện.
- Program nhận stdin hoặc file fixture qua `args[0]`.
- **Exact:** chuẩn hóa CRLF rồi so sánh chính xác, giữ khoảng trắng và xuống dòng cuối.
- **Token:** so sánh token, bỏ khác biệt khoảng trắng.
- **Float:** token số so sánh sai số tuyệt đối/tương đối; không chấp nhận NaN/Infinity như kết quả số hợp lệ.
- **Từng phần:** tổng điểm test đạt / tổng điểm. **Toàn phần:** chỉ đạt đủ test mới có điểm.
- Bảng và biểu đồ tính tổng điểm theo nhóm. Tổng testcase phải bằng 100 (chấp nhận sai số làm tròn vi mô).
- Có cấu hình hiện thông báo compile và chi tiết test Public. Hidden input/expected, reference và stderr của hidden harness không được trả cho sinh viên.

Giới hạn tổng thời gian một lượt OJ hiện là 90 giây. Bộ test lớn hoặc nặng phải được kiểm định thực tế trong giới hạn này; không có cam kết chạy thành công chỉ dựa trên số lượng test tối đa của form.

## 5. Bước 6 — kiểm định thật

Nhấn **Chạy kiểm định** sau khi lưu. Server xếp hàng riêng có giới hạn, trả job ID ngay; giao diện cập nhật tiến độ. Lượt chạy gồm:

1. Kiểm tra metadata, cấu trúc, đường dẫn, testcase và điểm.
2. Compile template bằng Docker.
3. Chấm lời giải chuẩn qua cùng OJ adapter với sinh viên.
4. Chấm lời giải sai mẫu; để rỗng thì dùng template làm ứng viên sai.
5. Tổng hợp PASS/WARNING/FAIL và lưu báo cáo theo hash nội dung.

Lời giải chuẩn phải AC toàn bộ test. Ứng viên sai phải bị phát hiện bằng verdict thực; SANDBOX_UNAVAILABLE/INTERNAL_ERROR không được tính là “phát hiện lời giải sai”. Không có Docker thì báo lỗi/không đạt. Job còn chạy khi server dừng được đánh dấu INTERRUPTED khi mở lại và cần chạy lại.

Sửa đề, source, test hoặc cấu hình làm báo cáo cũ hết hiệu lực. Nháp thay đổi trong lúc chạy không thể dùng kết quả đó để phát hành.

Phát hiện một lời giải sai mẫu không chứng minh test có độ bao phủ đầy đủ. Cần giảng viên kiểm tra ví dụ biên, constraints và các lời giải sai hợp lý trước khi dùng thi.

## 6. Bước 7 — duyệt và xuất bản

Đọc sáu thẻ tóm tắt, dùng **Xem trước sinh viên** để kiểm tra phần công khai. Chọn một trong hai cách duyệt:

- **Tôi đã rà soát — duyệt:** Teacher tác giả xác nhận tự duyệt theo chính sách hiện tại.
- **Gửi kiểm duyệt:** chọn một tài khoản Teacher đang hoạt động. Người đó mở **Tất cả bài tập → Tạo bài tập → Bản nháp của tôi → Đề đang chờ bạn kiểm duyệt**, đọc snapshot rồi Phê duyệt/Yêu cầu chỉnh sửa. Tác giả không tự ghi đè khi đang chờ người khác duyệt.

Phê duyệt gắn với hash, không chỉ ID nháp. Nội dung sửa sau khi gửi duyệt làm yêu cầu cũ hết hiệu lực. Reviewer chỉ có quyền đọc/ra quyết định cho snapshot được giao, không có quyền sửa draft của tác giả.

Để phát hành, quyền phải là **Phát hành vào ngân hàng cho sinh viên**. Riêng tư giữ trong nháp. Đổi quyền sau kiểm định cần kiểm định lại. Nút **Phát hành bài tập** chỉ bật khi kiểm định và phê duyệt còn hiệu lực; backend kiểm tra lại toàn bộ trong transaction.

Sau phát hành:

- Ngân hàng và Week nhận đúng snapshot, Program/Method/Class và tên file được giữ.
- Sinh viên được truy cập template, ví dụ Public và tài nguyên được phát hành; không truy cập draft/hidden/reference.
- Run sample dùng test Public; Submit chạy cả bộ test qua queue/Docker hiện có.
- Nhấn phát hành lại cùng nội dung không tạo bản sao.
- Nháp đã phát hành khóa sửa. Dùng **Tạo phiên bản mới**, hoặc nút Sửa của đề OJ trong ngân hàng để mở Builder tạo nháp kế tiếp.
- Phiên bản mới phải kiểm định và duyệt lại. Bài giao/kỳ thi/bài nộp cũ tiếp tục trỏ snapshot cũ.
- **Lưu trữ phiên bản** gỡ bản khỏi ngân hàng hoạt động, giữ dữ liệu lịch sử.

**Xuất JSON** là bản dành cho Teacher, gồm test ẩn và lời giải; không gửi file đó cho sinh viên. Chức năng này không phải luồng import JSON tổng quát.

## 7. Database, API và source

Builder dùng `server/data/studyfix-learning.db`. Migration cộng thêm được áp dụng idempotent khi khởi động; bản SQL đối chiếu: `server/db/sqlite-builder-workflow.sql`.

- `p3_builder_workflow`: reviewer, trạng thái, hash được duyệt, người quyết định.
- `p3_builder_jobs`: job, tiến độ, hash và kết quả.
- `p3_builder_publications`: snapshot riêng tư bất biến liên kết exercise.
- `p3_builder_public_assets`: chỉ những ảnh/PDF thuộc snapshot đã phát hành được phép đọc.
- Tiếp tục dùng draft/history/checks/assets, `p3_exercises`, `p3_test_cases`, `p3_question_bank`, Week và `p3_oj_exercises`; không tạo ngân hàng song song.

Actions mới qua `POST /api/assignments` có Bearer auth:

```text
BUILDER_STATUS, BUILDER_TEST_IMPORT, BUILDER_TEST_GENERATE, BUILDER_EXPECTED,
BUILDER_VALIDATE, BUILDER_REVIEWERS, BUILDER_REVIEW_LIST, BUILDER_REVIEW_GET,
BUILDER_REVIEW, BUILDER_REVIEW_DECIDE, BUILDER_PUBLISH, BUILDER_ARCHIVE, BUILDER_EXPORT
```

Các actions CREATE/SAVE/ADVANCE/GET hiện có được mở rộng, không đổi kiểu transport. Tất cả Builder APIs chỉ Teacher được dùng; REVIEW_GET/DECIDE còn kiểm tra đúng reviewer và hash; SAVE dùng expectedRevision. Binary routes cũ được dùng lại cho upload.

Source chính:

| File | Vai trò |
|---|---|
| `assignment/service/BuilderProblem.java` | Hợp đồng nội dung, template, testcase, config → Exercise/OJ |
| `assignment/service/ProblemBuilderWorkflow.java` | Import/generator, expected, queue kiểm định, review/publish API |
| `assignment/persistence/BuilderWorkflowRepository.java` | Job, review theo hash, transaction snapshot/Week/asset |
| `ProblemBuilderService.java`, `ProblemBuilderRepository.java`, `ProblemBuilderChecks.java` | Nối bảy bước, clone đúng cấu trúc, khóa bản phát hành |
| `ProblemBuilderAssets.java`, `StudyFixGateway.java`, `WebGatewayServer.java` | Quyền tài nguyên, danh sách reviewer tối thiểu và action whitelist |
| `OjJavaSandbox.java`, `DockerJavaSandbox.java` | Compile Java17, helper files, checker, scoring và capture expected riêng cho Teacher |
| `AssignmentService.java` | Dùng chung runner và dừng worker Builder khi đóng dịch vụ |
| `test-step.js`, `grading-step.js`, `validation-step.js`, `publish-step.js` | Giao diện bốn bước |
| `workflow-controller.js`, `controller.js`, `state.js`, `view.js` | API, tiến độ, preview/apply, autosave, điều hướng |
| `editor.js`, `assets.js`, `markdown.js`, `template-step.js`, `problem-builder.css` | Template readonly, file xác thực và bố cục |
| `practice.js`, `teacher-bank.js` | Sửa đề OJ bằng cách tạo phiên bản mới trong Builder |

## 8. Kiểm thử và phạm vi hỗ trợ

Test mới: `server/test/studyfix/BuilderCompleteIntegrationTest.java`, `web-client/tests/builder_complete_browser.cjs`. Dùng database tạm, cổng ngẫu nhiên; không thêm bài kiểm thử vào database đang sử dụng.

```powershell
# Từ StudyFix/server, sau khi cấu hình Java/Ant và mở Docker:
$env:STUDYFIX_BUILDER_BROWSER_MODULES='DUONG_DAN_NODE_MODULES_CO_PLAYWRIGHT'
ant builder-complete-test
```

Test bao gồm Program/Method/Class, helper Java chỉ đọc, reflection, kiểm định và sinh viên nộp thật, reviewer, clone phiên bản, Week nhiều-nhiều, ZIP preview, generator seed, expected Docker, chấm từng phần/toàn phần, phân quyền và không lộ hidden/reference. Chrome đi qua bảy bước, reload, upload cặp input/output, phê duyệt/phát hành, preview và laptop 1366px. Log và ảnh nằm trong `server/build/builder-complete-*`.

Phạm vi hiện hỗ trợ là file nộp đơn + helper Java công khai, trusted snippet và ba checker nói trên. **Chưa có JUnit riêng, custom checker tải lên, tải Maven dependencies, Java package project hoặc sinh test tùy ý cho mọi API.** UI không bật các tùy chọn chưa được runner hỗ trợ. Chọn giới hạn này để giữ hợp đồng nộp bài hiện có; không coi chạy snippet là đã tích hợp JUnit.

## 9. Bằng chứng của lần phát hành này

- `server/build/builder-complete-release.log`: BUILD SUCCESSFUL, native HTTP/SQLite/Docker và Chrome đi đủ bảy bước.
- `server/build/builder-dialog-verified.log`: BUILD SUCCESSFUL cho hộp thoại từ ngân hàng, không có route/sidebar riêng, đóng/Escape/mở lại nháp, đủ bảy bước và hồi quy Monaco/ảnh/PDF/ZIP/responsive. `server/reports/BUILDER_DIALOG_FRONTEND_TESTS.json`: 20 kiểm tra frontend đạt sau đổi giao diện.
- `server/build/builder-complete-regression.log`: BUILD SUCCESSFUL cho integration, SQLite management, Builder 1–3/Chrome/Monaco, OJ ba cấu trúc và verdict Docker CE/WA/RE/TLE/MLE.
- `server/reports/BUILDER_COMPLETE_FRONTEND_TESTS.json`: 20 kiểm tra frontend đạt. Bộ kiểm tra import được sửa để hiểu cú pháp ES module `import {name as alias}` đúng, vẫn kiểm tra export và vòng phụ thuộc.
- `server/reports/BUILDER_COMPLETE_VERIFICATION.json`: kiểm tra database sau khởi động, integrity/foreign key, số bài/Week và bảo toàn hash tài khoản.
- Ảnh đã kiểm tra: [bước 4](previews/PROBLEM_BUILDER_4_7/step4-tests.png), [bước 5](previews/PROBLEM_BUILDER_4_7/step5-grading.png), [bước 6](previews/PROBLEM_BUILDER_4_7/step6-validation.png), [bước 7](previews/PROBLEM_BUILDER_4_7/step7-publish.png).

Đã sao lưu ba database trước triển khai tại `server/backups/before-builder-4-7-20261109-051341/`. Server được khởi động lại qua launcher hiện có; log `server/build/builder-release-live.log`. Database sử dụng thực tế vẫn có 100 bài/12 Week, không chứa các bài Program/Method/Class fixture của bộ kiểm thử. Khôi phục backup cần dừng server và đối chiếu dữ liệu phát sinh sau thời điểm sao lưu, không chép đè khi đang vận hành.
