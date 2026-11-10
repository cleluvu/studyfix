# Phân tích Problem Builder theo source StudyFix hiện tại

Tài liệu Phase 0: đối chiếu yêu cầu đính kèm và bảy ảnh với source trong workspace. Đây là kết quả khảo sát và thiết kế triển khai; không phải xác nhận đã xây dựng xong Wizard, Method/Class Judge hoặc kiểm định Docker. Không thay code nghiệp vụ hay database trong lượt khảo sát này.

## 1. Kết luận về phạm vi

Problem Builder là phân hệ soạn và phát hành **một bài lập trình** của giáo viên. Nó không phải trình tạo kỳ thi. Sau khi xuất bản, bài được chọn qua các luồng giao bài, thi hoặc sửa lỗi lớp học đang có.

Giữ Java backend, frontend JavaScript ES modules, gateway hiện tại, database hiện tại, cơ chế đăng nhập và template tím–trắng. Không thêm React/Node server/Spring hoặc một ngân hàng dữ liệu thứ hai. Giữ tách biệt ngân hàng giáo viên và luyện tập sinh viên vừa triển khai.

Program có thể tận dụng đáng kể Judge hiện tại. Method/Class chưa được hỗ trợ chỉ bằng cách chọn một nhãn: cần cấu hình cấu trúc, nhiều file, trusted harness, kiểu testcase riêng và kết quả có cấu trúc. Không thể dùng nguyên form stdin/stdout cho cả ba cấu trúc.

## 2. Các ảnh cần điều chỉnh để đúng nghiệp vụ

| Ảnh | Phần giữ làm chuẩn visual | Điều chỉnh cho StudyFix |
|---|---|---|
| 1 — Thông tin | Form hai cột, lựa chọn dạng thẻ, thông tin tóm tắt | Thay Category bằng chọn nhiều Week; tác giả lấy từ phiên server; cấu trúc Java là trường mới |
| 2 — Xuất bản | Checklist bên trái, workflow và tùy chọn bên phải | Ảnh có “Đề giữa kỳ”, EXM006 và bước cài đặt kỳ thi: loại khỏi Builder một bài; dùng metadata bài lập trình |
| 3 — Template | Cây file / editor / cấu hình ba cột | JDK lấy từ cấu hình cho phép; không ghi JDK 17 cố định; chưa có Maven/JUnit trong pipeline hiện tại |
| 4 — Đề bài | Editor / preview / checklist ba vùng | Nội dung đổi theo cấu trúc Program/Method/Class; preview chỉ nhận DTO công khai |
| 5 — Chấm điểm | Cấu hình và phân bố nhóm/điểm, feedback | Các công tắc phải thực sự được Judge thực thi; thư viện ngoài/JUnit không mặc định bật |
| 6 — Kiểm định | Danh sách tiêu chí, chạy thử, lịch sử | Tính số tiêu chí/test từ dữ liệu thật; ảnh dùng số 20/21 test không thống nhất; chưa có đo bộ nhớ peak để hiển thị MB |
| 7 — Testcase | Toolbar, bảng test, panel cấu hình, tổng hợp | Đổi kiểu dữ liệu test theo cấu trúc; import/generator/kiểm định là thao tác thật |

Bảy ảnh dùng nhiều thứ tự/tên bước khác nhau. Chốt một thứ tự duy nhất: **Thông tin → Đề bài → Template → Test case → Chấm điểm → Kiểm định → Xuất bản**. Khung tạo đề thi, thời lượng kỳ thi và phân phối đề vẫn thuộc module kỳ thi.

Theo prompt mới, Builder chỉ chọn Week, không tạo menu/bảng Category. Trường `topic` đang có trong dữ liệu cũ được bảo toàn cho tương thích; không biến nó thành một hệ thống Category mới. Cũng không gán giá trị Method/Class/Program vào trường chủ đề này. `javaStructure` và `mode` phải là hai khái niệm riêng.

## 3. Audit frontend/backend/database/Judge

| Thành phần | Hiện trạng đã đọc trong source | Tái sử dụng | Cần bổ sung |
|---|---|---|---|
| Frontend | HTML/CSS/JS ES modules, `core/router.js` đăng ký renderer, `core/state.js` và `core/events.js`; không phải React | Router, event delegation, đăng nhập, toast/modal | Route Builder cho Teacher, state riêng bảy bước, guard chuyển trang |
| Template UI | `ui/template.js`, `template.css`, `question-bank.css`, `week-bank.css` | Sidebar/header/SVG/card/button/badge | Stepper, sticky footer, layout từng bước, menu Tạo bài tập |
| API client | `core/api.js`: Bearer token; `assignment(type,data)` gửi POST `/api/assignments` | Transport, lỗi và xử lý hết phiên | Client Builder riêng, revision/idempotency, upload binary riêng |
| Ngân hàng Teacher | `teacher-bank.js`, `BANK_LIST/GET/UPDATE/DELETE`, `catalog-create.js` | Quyền tác giả, xem trước, tạo/sửa đề, xóa mềm | Wizard thay form modal dài; mở lại bản nháp chưa hoàn chỉnh |
| Sinh viên | `practice.js`: editor một nguồn Java, chạy/nộp/lịch sử; giao diện đã tách Teacher | Giữ nguyên luồng Program cũ | Editor/DTO nhiều file và submit cho Method/Class trước khi cho dùng hai cấu trúc đó |
| Backend | Java, `HttpServer/HttpsServer`, `WebGatewayServer`, `AssignmentService`; build Ant, `javac.release=26` | Gateway và service/repository hiện có | Service Builder riêng, validation service, assets và audit; tránh tiếp tục dồn vào AssignmentService |
| Xác thực | Identity TEACHER/STUDENT; gateway xác thực session, mã phiên core do server gắn; Passwords dùng PBKDF2-HMAC-SHA256 | Identity và phân quyền backend | Quyền reviewer, ACL chia sẻ, policy tự duyệt có cấu hình |
| DB quản lý | MySQL được hỗ trợ cho quản lý tài khoản/lớp; có backend SQLite theo cấu hình | Không đổi database quản lý | Không đưa workflow Builder vào bảng account/class |
| DB học tập | SQLite riêng, mặc định `data/studyfix-learning.db`, JDBC | Toàn bộ ngân hàng, Week, assignment/contest/submission | Migration tăng dần cho bản nháp, file, cấu hình, validation, review, audit |
| Câu hỏi | `p3_exercises`, `p3_test_cases`; template/starter/reference là chuỗi | Nguồn dữ liệu xuất bản của Program cũ | Metadata cấu trúc, file bundle, statement sections, test groups/harness |
| Version | `p3_question_bank`: root/display code, author, DRAFT/PUBLISHED, revision, current/deleted | Immutable exercise/test rows và FK cũ | Snapshot đầy đủ mọi thành phần Builder; lịch sử version và chọn bản |
| Week | `p3_question_weeks`, `p3_question_week_items`, khóa ghép week–exercise | Quan hệ nhiều–nhiều có sẵn, API `PRACTICE_WEEK_*` | Chọn nhiều Week trong Wizard, kiểm tra quyền và lưu liên kết nguyên tử |
| Bản nháp | `CATALOG_CREATE` vẫn yêu cầu đủ đề/testcase; cập nhật ngân hàng tạo exercise version mới | DRAFT/PUBLISHED ở thời điểm phát hành | Draft workspace riêng lưu dữ liệu chưa hoàn chỉnh, autosave và current step |
| Upload | Gateway JSON tối đa 128 KiB; nạp code hiện có chủ yếu là `file.text()` phía trình duyệt | Luồng auth và kiểm tra file cơ bản | Upload ảnh/PDF/ZIP thật, streaming/giới hạn riêng, lưu file có checksum/ACL |
| Queue | `AssignmentService`: bounded queue, worker, recover; TaskKind PERSONAL/SHARED/CONTEST/TRIAL/PRACTICE | Cơ chế trạng thái và nhận kết quả | Job compile/validation, snapshot hash, giới hạn tác vụ kiểm định để không chiếm hết lượt sinh viên |
| Kiểm định | Chưa có bảng/job chứng nhận bản đề vượt reference tests | Có interface `GradingSandbox` và worker | Compile-only, reference/wrong sample dry run, lịch sử, stale result và publish gate |
| Markdown/editor | Chưa có Monaco, Markdown renderer/sanitizer hoặc JUnit runner trong static/module chấm đã khảo sát | Textarea/form/escape hiện có | Đóng gói thư viện frontend tại chỗ cho LAN; parser/sanitizer và adapter editor |

Các file gốc cần đối chiếu: `web-client/static/core/{router,state,api,events,polling}.js`, `ui/{render,template}.js`, `features/exercises/{practice,catalog-create,teacher-bank,week-bank}.js`; backend `WebGatewayServer.java`, `assignment/service/AssignmentService.java`, `assignment/persistence/{AssignmentRepository,QuestionBankRepository,QuestionWeekRepository,PracticeRepository}.java`, `assignment/model/{Exercise,ExerciseMode}.java`, `assignment/grading/{GradingSandbox,DockerJavaSandbox}.java`.

## 4. Judge hiện tại: khả năng và giới hạn chính xác

`DockerJavaSandbox.grade()` nhận một `String source`, danh sách `Exercise.TestCase(input,expected,weight,hidden)` và maxScore. Nó ghi `Main.java`, chạy `javac -encoding UTF-8 Main.java`, rồi chạy `java ... Main` với stdin từng test. Mỗi test chạy trong container mới. Đây là Program Judge, không có gọi method, JUnit, kiểm tra constructor hoặc class structure.

Các mặc định/giới hạn trong code:

- Image mặc định `eclipse-temurin:21-jdk` trong `AssignmentModuleConfig`; có thể được cấu hình khác. JDK server 26 và JDK container 21 là hai môi trường khác nhau.
- Source tối đa 32 KiB; stdout tối đa 512 KiB; stderr capture 16 KiB; diagnostic capture 12 KiB. Không được hiển thị một cấu hình mới lớn hơn mà backend vẫn âm thầm dùng giới hạn cũ.
- AssignmentService khởi tạo sandbox 2 giây/test và tổng 45 giây; cấu hình chấm hiện tại không nằm riêng trên từng câu hỏi.
- Container: network none, RAM/swap 128 MiB, CPU 0.5, PID 32, read-only root, drop capabilities, no-new-privileges, tmpfs 16 MiB. Runtime dùng heap 64 MiB. Source/classes mount read-only khi chạy; compile mount writable chỉ có workspace tạm.
- So sánh hiện tại chuẩn hóa CR/LF và bỏ tối đa một newline cuối; không phải token checker hay float tolerance.
- Điểm hiện tại là `floor(maxScore * trọng số đạt / tổng trọng số)`. Trọng số cũ không bị bắt buộc cộng thành 100; không được áp quy tắc tổng 100 lên toàn bộ dữ liệu cũ.
- Verdict gồm CE/RE/TLE/MLE/OLE/WA/AC và sandbox unavailable. Exit 137 chỉ được code coi là dấu hiệu có thể vượt RAM, chưa phải đo bộ nhớ peak.
- Hidden expected không ghi vào thư mục container Program; input từng test phải đưa cho chương trình qua stdin. Kết quả hidden được rút gọn; không hứa bộ đếm/log mới chứa dữ liệu thô sẽ tự an toàn.
- Có cleanup thư mục trong finally và kill container khi timeout. Các ca cleanup bất thường vẫn phải có test thực tế khi mở rộng.

Docker CLI được tìm thấy trên máy tại lúc khảo sát. Chưa chạy Docker Engine/image hoặc chấm thật trong lượt phân tích này; không kết luận Engine đang sẵn sàng. `PracticeDockerIntegrationTest` là test hiện có cho Program và không chứng minh Method/Class đã hoạt động.

## 5. Mô hình thông tin cần phân biệt

| Trục | Ý nghĩa | Quyết định thiết kế |
|---|---|---|
| `mode` hiện có | BUILD_FROM_SCRATCH / DEBUG_COLLAB | Giữ nguyên luồng nghiệp vụ lớp học |
| `javaStructure` mới | PROGRAM / METHOD / CLASS | Chọn runner, schema statement/template/test |
| Week | Sắp xếp bài theo tuần | Một bài thuộc nhiều Week; không tạo bản sao đề |
| Workflow | DRAFT / VALIDATED / IN_REVIEW / APPROVED / PUBLISHED / ARCHIVED | Trạng thái kiểm soát bởi hành động server |
| Visibility | PRIVATE / SHARED / PUBLIC | Quyền đọc/sử dụng, tách khỏi workflow |
| Revision nháp | Số lần lưu workspace | Optimistic concurrency, không phải một bản đề đã phát hành |
| Version phát hành | Snapshot được assignment/exam tham chiếu | Bất biến, có hash và người/thời điểm phát hành |

Thông tin tác giả/kiểm duyệt không tin từ request. Người kiểm duyệt phải là tài khoản có quyền review hợp lệ, không chỉ là tên nhập tay. Tên trùng nên cảnh báo, mã trùng phải bị chặn bằng constraint và transaction. Tự sinh JAVA0001 dùng sequence/reservation trong transaction, không lấy MAX ở frontend; mã được giữ hoặc sinh lại khi có xung đột.

Visibility phải được áp dụng tại tất cả đường truy cập: bank list/detail, practice list/get/run/submit, assets/download, chọn đề vào Week, giao bài, kỳ thi, preview. Người được giao bài riêng được đọc đúng phiên bản được giao; không vì PRIVATE mà làm hỏng bài đã giao, cũng không vì biết ID mà đọc được bản nháp.

## 6. Thiết kế bảy bước phù hợp dự án

1. **Thông tin:** hai cột theo ảnh. Tên/mã, cấu trúc Java, độ khó, thời gian dự kiến, nhiều Week, chuẩn đầu ra, mô tả ngắn. Tác giả do server xác định; reviewer/visibility/workflow/version hiển thị đúng quyền. Không tạo Category.
2. **Đề bài:** editor Markdown, student preview và checklist. Program có input/output; Method có signature/params/return; Class có constructor/fields/methods/behavior. Checklist chỉ kiểm tra trường/định dạng, không tự chứng nhận lời giải đúng. Markdown/ảnh/link được sanitize; raw HTML không được render tùy ý.
3. **Template:** tree/editor/config theo ảnh. Monaco được phục vụ từ static assets cùng dự án để dùng LAN không phụ thuộc CDN. Public/editable/read-only/teacher-only/judge-only là metadata server thực thi. Ban đầu chỉ liệt kê JDK/runner thực có; compile-only độc lập với chấm tính đúng. Chương trình có thể compile đúng nhưng template TODO vẫn không qua testcase.
4. **Test case:** bảng/group/panel. Program dùng stdin/stdout; Method dùng invocation/arguments/expected return/exception; Class dùng constructor, chuỗi thao tác, state và structure checks. Import .in/.out/JSON/ZIP có xem trước, kiểm tra toàn bộ rồi ghi transaction. Generator dùng kiểu/range/seed có whitelist, chưa có dữ liệu expected cho đến khi reference chạy thật.
5. **Chấm điểm:** execution limits, checker, nhóm/trọng số, policy phản hồi. Backend là nguồn tính điểm chính, frontend chỉ hiển thị. Thư viện/JUnit/harness theo allowlist; chưa có runner hỗ trợ thì báo không hỗ trợ, không cho bật công tắc giả.
6. **Kiểm định:** compile template/harness/reference, chạy reference toàn test, chạy sample sai nếu cung cấp, kiểm tra file/visibility/config và lưu run. PASS/WARNING/FAIL được tính từ kết quả thật. Docker hỏng hoặc thiếu reference ⇒ chưa đạt, không được phát hành. Không hiển thị số MB peak nếu chưa đo được.
7. **Xuất bản:** tổng hợp, student preview, export JSON theo quyền, gửi duyệt, duyệt và publish. Kiểm tra lại content hash/revision ở server trong transaction trước khi tạo snapshot. Snapshot hoàn chỉnh liên kết với Week và ngân hàng hiện có.

Stepper chỉ dùng một tập nhãn thống nhất. Thông tin đánh giá/duyệt lấy từ API, không tự tích xanh theo bước đã bấm. Các trạng thái loading/empty/error/conflict/sandbox-unavailable có giao diện riêng.

## 7. Nháp, autosave và chuyển bước

Không dùng `QuestionBankRepository.save()` cho từng phím gõ: nó tạo bản exercise/testcase bất biến mới, yêu cầu nội dung đầy đủ và chuyển liên kết Week. Cần workspace nháp riêng.

Đề xuất `p3_problem_builder_drafts`: id, owner_id, base_exercise_id, revision, current_step, payload_json, content_hash, created_at, updated_at. JSON payload là format có schema/version được server validate; không phải dữ liệu tùy ý để thực thi.

- Lưu nháp cho phép thiếu dữ liệu chưa tới bước; vẫn giới hạn kích thước, kiểu và quyền ngay khi lưu.
- Autosave debounce khoảng 800–1200 ms, serialize request, gửi expectedRevision. Server trả revision/hash; response cũ không ghi đè màn mới. Xung đột hiển thị để reload/so sánh, không tự last-write-wins.
- Lưu thất bại giữ dirty state và báo chưa lưu. Chuyển bước chỉ khi validate bước hiện tại và lưu thành công; quay lại không bắt hoàn tất bước sau.
- Guard phải gắn cả `navigate()`, các nút đổi màn ngân hàng và beforeunload. Session hết hạn/logout đổi người không được gửi lại nội dung dưới tài khoản mới.
- Reload mở lại nháp bằng ID từ server và phục hồi bước. localStorage nếu dùng chỉ là bản phục hồi phụ theo user, không phải nguồn chính.
- Lưu hash của snapshot đang kiểm định. Người dùng sửa nội dung trong khi job đang chạy ⇒ kết quả job cũ stale, không cấp VALIDATED cho bản đang sửa.

## 8. Database: tái sử dụng và phần bổ sung tối thiểu

Giữ `p3_exercises`, `p3_test_cases`, `p3_question_bank`, hai bảng Week, các bảng assignment/contest/submission. Không tạo thêm `problems` và `problem_versions` độc lập trùng với ngân hàng hiện tại.

Các thực thể mới đề xuất theo vai trò:

| Nhóm | Lưu gì | Quan hệ |
|---|---|---|
| Builder draft | Payload chưa hoàn chỉnh, step, revision/hash | Owner Identity; base version nếu đang sửa |
| Version specification | javaStructure, structured statement, entrypoint/signature, visibility, limits/checker/scoring/feedback, schema version | FK đến exercise version bất biến hiện có |
| Template files/assets | Path logic, ACL, editable, size, media type, checksum/storage key | Draft hoặc published exercise version; không public static trực tiếp |
| Test groups/specifications | Group/scoring và typed method/class test data | Exercise version; Program có thể tiếp tục dùng p3_test_cases |
| Reference solutions | Source/files và ACL teacher/judge-only | Draft/version; không nằm trong student DTO/bundle |
| Validation runs/checks | Job status, hash, runner/image/config, tiêu chí, log đã lọc, người/thời gian | Draft revision hoặc exercise version |
| Review/workflow/audit | Reviewer, transition, actor, reason, timestamp, before/after revision | Draft/root/version; không lưu mật khẩu/token |

Không thay CHECK DRAFT/PUBLISHED của `p3_question_bank` bằng cách ghi các giá trị mới trực tiếp: sẽ lỗi schema. Workflow đầy đủ cần migration/companion metadata có mapping rõ với trạng thái ngân hàng. ARCHIVED cũng khác xóa mềm. Private/Shared/Public cần policy riêng, không chỉ ba nút radio.

Publish là transaction gồm quyền/revision/hash/validation/review ⇒ snapshot version ⇒ template/test/config/ref metadata ⇒ Week links ⇒ publication marker. FK lịch sử không cascade sang assignment/exam/submission. Migration có version/checksum, kiểm tra trước/sau, backup theo hướng dẫn; rollback migration không hứa khôi phục dữ liệu phát sinh sau khi nâng cấp.

Đề cũ đang Published được giữ nguyên, có nhãn legacy/chưa có chứng nhận Builder nếu chưa kiểm định. API tạo/xuất bản cũ phải được rà lại khi bật publish gate: hiện `CATALOG_CREATE` có thể xuất bản trực tiếp. Không thể nói “bắt buộc kiểm định” nếu endpoint này vẫn cho phát hành mới vượt qua policy. Cutover phải chặn bypass server-side, đồng thời kiểm thử compatibility dữ liệu cũ.

## 9. API và module theo kiến trúc hiện có

Tiếp tục POST `/api/assignments` với action, thay vì dựng thêm một bộ REST song song không cần thiết. Tên sau là **đề xuất, chưa tồn tại**:

- `BUILDER_CREATE/GET/LIST/SAVE/DELETE_DRAFT`: nháp và revision.
- `BUILDER_CHECK_CODE`: đề xuất mã/kiểm tra trùng; constraint khi lưu vẫn là quyết định cuối.
- `BUILDER_HISTORY/RESTORE`: lịch sử nháp, phục hồi tạo revision mới.
- `BUILDER_COMPILE/VALIDATE/JOB/VALIDATIONS`: nhận job, theo dõi trạng thái, lịch sử; không giữ HTTP chờ container chạy.
- `BUILDER_PREVIEW`: trả student DTO theo allowlist, không toàn bộ draft.
- `BUILDER_SUBMIT_REVIEW/APPROVE/REJECT/PUBLISH/ARCHIVE`: state transitions có quyền, revision/hash/idempotency.
- `BUILDER_EXPORT`: gói phù hợp quyền; export sinh viên không có secrets; export tác giả chứa reference phải được giới hạn riêng.
- Week dùng `PRACTICE_WEEK_LIST` và repository quan hệ hiện có; chỉ thêm operation gán nhiều Week nguyên tử nếu cần.

Upload ảnh/PDF/ZIP cần route binary/multipart được giới hạn riêng và qua Bearer auth, không nhét base64 ZIP lớn vào JSON 128 KiB hoặc tăng giới hạn toàn gateway. Storage key do server tạo, file ngoài static web root. Download luôn kiểm tra ACL.

Frontend dự kiến `features/problem-builder/`: controller, api client, state, stepper, preview và bảy step renderer riêng; tái sử dụng `ui/template.js`. Backend: BuilderService/Repository, AssetsService, ValidationService và adapters Judge trong package assignment. Không chuyển toàn bộ logic vào `practice.js`, modal form hay một class bảy bước khổng lồ.

## 10. Mở rộng Java Judge và upload

Giữ đường chấm Program legacy để hồi quy không thay đổi. Bổ sung execution primitives dùng chung và dispatch theo version specification:

- ProgramRunner: main class/package, nhiều file hợp lệ, stdin/stdout, checker cấu hình.
- MethodRunner: parse/validate signature và kiểu dữ liệu hỗ trợ, trusted harness sinh từ schema, arguments/results/exceptions; JUnit từ artifact allowlist.
- ClassRunner: constructor/signature/visibility checks và chuỗi thao tác/behavior tests, trusted structure validator.

Không nối trực tiếp method signature hoặc đường dẫn người dùng vào shell command. Command dùng argv, file paths đã chuẩn hóa. Không chạy Maven/Gradle/pom của ZIP tải lên một cách tự động. Thư viện ngoài chỉ bật cho dependency đã được quản trị phê duyệt, đóng gói trong image và có checksum, không tải mạng lúc chấm.

Trusted harness/JUnit tests không được gửi về trình duyệt; chạy bên trong sandbox. Việc dùng chung JVM với code sinh viên không tạo ra bảo đảm tuyệt đối rằng code ác ý không thể dò classpath/reflection; thiết kế runner phải tính đến giới hạn này, giữ reference/expected riêng phía server và không đưa credentials/host files vào container.

Kiểm soát ZIP: chặn absolute path, drive/UNC, `..`, backslash traversal, NUL, tên trùng sau chuẩn hóa, symlink/entry không hỗ trợ; giới hạn số entry, dung lượng nén và tổng giải nén, từng file và quota. Import vào staging, kiểm tra xong mới commit; lỗi không ghi dở hoặc đè draft khác. Giới hạn cụ thể được cấu hình và kiểm thử, không suy ra từ ảnh.

Checker giữ semantics legacy cho đề cũ; cấu hình mới mới dùng exact/token/abs+relative float tolerance theo đặc tả. Custom checker là plugin đáng tin chạy cách ly. Điểm nhóm/test, all-or-nothing/partial và feedback phải nhất quán ở runner, lưu kết quả và DTO sinh viên.

## 11. Phase triển khai và điều kiện chuyển phase

| Phase | Kết quả cụ thể | Bằng chứng bắt buộc |
|---|---|---|
| 0 — Audit | Tài liệu này; source/schema/capability map | Đọc source và phân biệt rõ hiện có/đề xuất |
| 1 — Data/API | Migration nháp/specification, access policy, revision, contract | Migration dữ liệu cũ, restart, rollback lỗi, Student/foreign Teacher denied |
| 2 — Wizard/Thông tin | Route/menu Teacher, stepper, metadata/Week, autosave | Lưu nháp thiếu bước, reload đúng bước, hai tab conflict, không tạo Week ngoài ý muốn |
| 3 — Đề bài | Markdown/preview/checklist/assets | XSS/link/assets ACL, chuyển cấu trúc giữ dữ liệu phù hợp, không lộ reference/tests |
| 4 — Template | Monaco/cây file/upload/compile; Program, rồi Method/Class adapters | ZIP độc hại, multiple files, compile-only thật, các JDK được hỗ trợ, student bundle |
| 5 — Test manager | Typed tests, groups, import/generator/reference | Import nguyên tử, seed lặp lại được, tests/group/expected có validation thật |
| 6 — Chấm điểm | Limits/checker/scoring/feedback gắn runner | Exact/token/float, timeout/OOM/OLE, điểm tổng nhất quán, hidden feedback |
| 7 — Kiểm định | Async jobs, hash binding, lịch sử/check results | Reference đúng, sample sai, Docker unavailable, thay dữ liệu làm stale |
| 8 — Publish/review | Approve/publish/archive, immutable snapshots | Không bypass publish gate qua API cũ, author/reviewer roles, đề đã giao giữ nguyên |
| 9 — Integration | Builder → Bank → Week → Assign/Contest → Student Judge | Bộ acceptance thực tế, screenshot desktop/laptop/mobile và báo cáo giới hạn |

Mỗi phase có danh sách file sửa, migration/API, test và lỗi còn lại. Không đánh dấu phase Judge/Validation hoàn thành bằng UI fixture hoặc sandbox test double. Program/Method/Class chỉ được cho sử dụng khi runner tương ứng qua end-to-end; không đưa hai radio chưa chấm được vào sản phẩm như tính năng đã hoàn tất.

## 12. Acceptance map cho 15 kịch bản

1. Program: tạo/publish/assign/nộp đúng và sai qua Docker thật.
2. Method: signature + JUnit/harness thật, đúng/sai/exception; không bọc nhãn Program.
3. Class: constructor, nhiều methods, state/structure/visibility đúng và sai.
4. Save draft/thoát/reload: dữ liệu từ database, đúng owner/step/revision.
5–6. Gắn Week 4 và Week 5: một version, hai liên kết; quyền Week được kiểm tra.
7–8. Template/test import hợp lệ và lỗi: staging/transaction, không ghi dở; path traversal/zip bomb/duplicate/corrupt cases.
9. Reference AC và sample sai bị phát hiện bằng Judge; kết quả không tự gán.
10. Không xuất bản khi thiếu/failed/stale validation; thử gọi trực tiếp API cũ và mới.
11. Sửa thành version mới sau khi giao: assignment/exam cũ giữ template/test/config/reference cũ, kể cả queued job.
12. Student/non-author: thử mọi DTO, assets, export, preview, job log; không lộ hidden/reference.
13. Hai editor save/publish/approve đồng thời: expectedRevision/hash và idempotency, không overwrite âm thầm.
14. Sandbox: CPU/RAM/PID/network/time/output/filesystem/cleanup thực tế; Engine unavailable không PASS.
15. Giao diện: 1672×941 theo ảnh, 1366×768 laptop, mobile; footer/stepper/editor scroll độc lập, không mất form khi save/poll.

## 13. Trạng thái sau khảo sát

Khảo sát kiến trúc trong tài liệu này là cơ sở thiết kế. Sau khảo sát, source đã được bổ sung draft workspace/API, ba bước Thông tin–Đề bài–Template, lưu nháp và lịch sử, upload riêng tư, Monaco nội bộ và compile-only Docker. Trạng thái chi tiết, file liên quan, bằng chứng kiểm thử và những bước chưa hoàn tất được cập nhật tại [TIEN_DO_PROBLEM_BUILDER_VI.md](TIEN_DO_PROBLEM_BUILDER_VI.md). Chưa chứng nhận runner Method/Class, kiểm định toàn bộ hoặc workflow xuất bản mới.
