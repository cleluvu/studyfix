# StudyFix — Kế hoạch tái cấu trúc nghiệp vụ và UI/UX

**Phạm vi:** phát triển tiếp từ source `StudyFix.zip` mày gửi; không tạo một server/domain mới và không thay thế âm thầm source đang chạy. Tài liệu này là plan để duyệt trước khi triển khai, chưa phải bản code đã sửa.

## 0. Quyết định định hướng

1. **Làm đúng luồng nghiệp vụ trước, rồi mới thay UI.** Không chỉ đổi tên nút; những lỗi hiện tại nằm cả ở API và schema (đặc biệt luồng tạo contest).
2. **Giữ Java backend và các core protocol hiện tại** cho đăng nhập/phòng/chat/code cộng tác/relay trong giai đoạn đầu. Không viết lại `Server`, `Room`, `StudyFixGateway` trừ khi một yêu cầu cụ thể không có extension point an toàn.
3. Vì giao diện Swing hiện có nhiều tab lồng nhau và người dùng muốn UX kiểu online judge, phương án mục tiêu là **web client React + TypeScript**, được thêm song song vào project hiện tại. Swing client cũ vẫn được giữ như fallback đến khi mọi luồng mới đạt acceptance tests.
4. Bổ sung một **Web/API gateway mỏng** để web client giao tiếp với Java services. Không cho trình duyệt truy cập thẳng TCP protocol riêng của Java hoặc cổng relay hiện có. Gateway chỉ map session/user hiện có sang API; không nhân đôi luật phân quyền, chấm điểm hoặc dữ liệu.
5. Server Manager vẫn là một UI riêng. Giai đoạn đầu giữ Java Swing `ServerAdmin` và bổ sung trạng thái assignment/queue, tránh trộn trang quản trị server với giao diện học sinh. Có thể thay UI Server Manager thành web dashboard ở giai đoạn sau nếu cần, nhưng không phải điều kiện để hoàn thành UX học tập.

---

## 1. Đánh giá source hiện tại theo file đã kiểm tra

### Client

- `client/src/main/java/vn/studyfix/client/ui/MainFrame.java`: `JTabbedPane` cấp ngoài đang có 7 mục: Code cộng tác, Chat, Thành viên, Lịch sử code, Chia sẻ màn hình, Bài tập/Nộp bài, Hoạt động/Phản hồi.
- `client/src/main/java/vn/studyfix/client/ui/SharedCodePanel.java`: có nút Tải code, Xin quyền sửa, Trả quyền, mở vùng chọn, Nạp toàn bộ code (GV), xem bản nháp, hoàn tất lượt sửa và hủy.
- `client/src/main/java/vn/studyfix/client/exercise/ExerciseModule.java`: lại có 6 tab bên trong: Ngân hàng bài, Giao bài, Sửa lỗi cùng phòng, Làm bài cá nhân, Lịch sử & kết quả, Thi & xếp hạng. Đây là nguồn gây điều hướng lồng nhau và lặp thao tác.
- `ChatPanel.java` đưa một số message loại SYSTEM/GENERIC vào chat dưới tên “Server”, khiến khu vực chat lẫn giữa tin người dùng và trạng thái vận hành.
- `MembersPanel.java` có danh sách thành viên, nhưng không có tiêu đề tổng số thành viên nổi bật.
- `ServerAdmin.java` đã là một giao diện Swing riêng, xem phòng, version, người giữ quyền, history code và log; cần mở rộng bằng chỉ số assignment/worker thay vì dựng lại server quản trị từ đầu.

### Server / assignment

- Bài tập đã có `ExerciseMode.DEBUG_COLLAB` và `ExerciseMode.BUILD_FROM_SCRATCH`, catalog, statement, template/starter code, hints, tests, reference solution và Docker sandbox.
- `AssignmentService.java` hiện xử lý `CATALOG_LIST`, `CATALOG_GET`, `ASSIGN_CREATE`, `ASSIGN_LIST`, `ASSIGN_GET`, shared checks, `SUBMIT_CREATE`, contests và leaderboard.
- Lỗi tạo kỳ thi là có thật trong backend: `CONTEST_CREATE` nhận `assignmentIds`, kiểm tra từng ID bằng `requireAssignmentVisible`, bắt buộc do teacher-owner giao trước, rồi `p3_contest_problems` lưu `assignment_id` và có FK đến `p3_assignments`. Vì vậy mã `SEQ001` (exercise ID trong catalog) không thể được dùng thay UUID assignment.
- `AssignmentRepository.java` cần migration có version; tuyệt đối không xoá/ghi đè database người dùng.

### Reference bên ngoài

- Trang PTIT DLab công khai mô tả hệ thống luyện tập/thi lập trình trực tuyến và tự chấm: https://code.ptit.edu.vn/submit/J01025. Nội dung public hiện có thể chuyển về đăng nhập, nên không coi đó là tài liệu API.
- Trang `codeptit.vercel.app` là một trang community/đề lập trình riêng, không khẳng định là website chính thức của PTIT. Những pattern hữu ích để tham khảo là danh sách bài + tìm kiếm/lọc + tiến độ, panel đề bài, compiler/submission, trạng thái chấm và bảng xếp hạng: https://codeptit.vercel.app/.
- StudyFix sẽ học cách tổ chức tác vụ của online judge, không sao chép giao diện/nhận diện thương hiệu của bất kỳ trang nào.

---

## 2. Kiến trúc trải nghiệm mục tiêu

### 2.1 Điều hướng cấp chính

Giảm từ 7 tab cấp ngoài + 6 tab lồng trong xuống 4 khu vực chính, hiển thị theo role:

1. **Tổng quan** — trạng thái phòng, bài đến hạn, kỳ thi sắp tới, tiến độ gần đây.
2. **Phòng học** — workspace cộng tác đang chọn; Code cộng tác là nội dung chính, Chat và Thành viên nằm ở sidebar phải; Chia sẻ màn hình là tab/toolbar cùng cấp trực quan với Code cộng tác; history code ở cuối trang Code cộng tác dạng thu gọn.
3. **Bài tập** — một trang chọn/lọc bài và một vùng chi tiết; hành động khác nhau theo role. Không tạo các tab con rời cho ngân hàng, giao bài, sửa lỗi, làm bài cá nhân và history.
4. **Thi & xếp hạng** — học sinh xem contest và làm bài; giáo viên tạo contest trực tiếp từ ngân hàng, xem standings/chi tiết từng bài.

**Teacher-only navigation:** quản lý giao bài/tiến độ lớp có thể là một panel trong Bài tập và báo cáo kết quả; không đưa thành trang độc lập khi chưa cần. **Server Manager** là app/admin window riêng.

### 2.2 Layout trong Phòng học

- Top bar: tên phòng, số thành viên hiện tại, chỉ báo kết nối, role/current editor, nút Chia sẻ màn hình.
- Trung tâm: Code cộng tác; thẻ đề bài đang giao hiện bên trên/ cạnh editor (mô tả, input/output, constraints, examples), không bắt chuyển sang tab sửa lỗi.
- Toolbar code: `Xin quyền sửa`, `Trả quyền`, `Hoàn tất lượt sửa`, `Hủy lượt sửa` khi có draft; `Nạp/Thay code toàn bộ` chỉ cho giáo viên. Không có nút “Tải code” hiển thị thường xuyên; đồng bộ ban đầu và khi event/version đổi được tự động. Nếu cần refresh lỗi mạng, đưa vào menu phụ “Đồng bộ lại”.
- Nút `Kiểm tra code chung` ở ngay cạnh trạng thái version; vùng kết quả hiển thị `QUEUED/RUNNING/PASS/FAILED`, test đạt/tổng, loại lỗi an toàn và checked version/hash. Không gửi học sinh về trang bài tập để nộp lại shared code.
- Lịch sử code nằm cuối trang dưới dạng accordion `Lịch sử phiên bản`, có thể mở xem diff/nội dung theo quyền hiện hành.
- Sidebar phải: tabs nhỏ `Chat` và `Thành viên (N)`; tin nhắn chỉ có người gửi và system notice dạng banner/toast, không thêm tên “Server” như một người chat.
- Chia sẻ màn hình giữ cơ chế relay hiện tại, đổi vị trí điều hướng thôi; không sửa encoder/relay nếu regression chưa chỉ ra bug.

### 2.3 Phân quyền UI

| Thành phần | Teacher | Student |
|---|---|---|
| Ngân hàng bài | Có | Không hiện trang/nút |
| Giao bài | Có | Không hiện trang/nút |
| Nạp/Thay toàn bộ code chung | Có | Không hiện |
| Xin quyền sửa / Trả quyền | Có; teacher có ưu tiên cao nhất | Có theo rule lượt sửa |
| Kiểm tra code chung | Có trong bài `DEBUG_COLLAB` | Có trong bài `DEBUG_COLLAB`, theo quyền phòng/assignment |
| Làm bài cá nhân | Có chế độ thử bài; không tính như thành tích học sinh | Có khi được giao hoặc trong contest |
| Lịch sử nộp cá nhân | Không phải tab cá nhân riêng; nằm tại bài đang làm nếu role student | Nằm ngay trong bài; không có tab global “Lịch sử & kết quả” |
| Kết quả cả lớp | Có report theo assignment/contest | Không xem điểm/submission riêng của người khác |
| Thi & BXH | Tạo contest/chọn bài/đặt thời gian/xem leaderboard/drill-down | Danh sách contest/làm contest/xem standings theo chính sách công bố |
| Thành viên | Danh sách + tổng số + quản lý phù hợp | Danh sách + tổng số |

---

## 3. Các luồng nghiệp vụ chi tiết

### 3.1 Code cộng tác và sửa lỗi cùng phòng (gộp về cùng một workspace)

1. Teacher giao một bài `DEBUG_COLLAB` từ Bài tập.
2. Hệ thống tự lấy starter code của assignment và khởi tạo shared editor khi giao/nạp bài, có cảnh báo chỉ khi việc đó ghi đè code đã tồn tại. Không bắt teacher phải vào một trang khác để “nạp code lỗi” lần nữa.
3. Học sinh và giáo viên cùng thấy mô tả đề đang làm ở ngay trang Code cộng tác.
4. Shared code version nhận tự động lúc vào phòng và cập nhật khi server phát event. Xoá nút Tải code khỏi toolbar chính.
5. Teacher có quyền sửa ưu tiên cao hơn queue; không phải xếp đợi. Cần thêm semantics server-side (xem mục 5.2): nếu đang có draft chưa commit, không âm thầm mất code học sinh; gửi cảnh báo stale/teacher takeover và giữ bản nháp cục bộ để copy/restore nếu phù hợp.
6. Student hoặc teacher có thể xin/trả quyền theo rule. `Hoàn tất lượt sửa` chỉ commit bản sửa; không tự hiểu là PASS.
7. `Kiểm tra code chung` chạy snapshot/version đã xác minh trên server, kết quả hiện ngay tại Code cộng tác. Không tạo submission cá nhân, không cộng điểm mỗi thành viên hay đưa vào BXH trừ khi policy contest nhóm được bật rõ ràng.
8. `Nạp/Thay toàn bộ code (GV)` là thao tác ngoại lệ dành cho teacher để nạp starter mới hoặc thay code. Chỉ cần xác nhận nếu sẽ ghi đè code khác starter.
9. Khi mở assignment `DEBUG_COLLAB`, tải starter và statement theo assignment; nếu phòng đã có code, chỉ tự nạp trong trường hợp assignment vừa được giao và trạng thái/version đủ điều kiện, ngược lại phải hỏi xác nhận.

### 3.2 Bài tập / giao bài (một trang chung, role-based)

**Teacher view**

- Một danh sách toàn bộ bài với tìm kiếm theo mã/tên, chủ đề, độ khó, loại bài và trạng thái; giữ các bộ lọc hiện có.
- Click một row/card mở drawer/modal chi tiết: mã, tên, mode, mô tả, input/output, constraints, examples, hints được phép xem, public tests.
- Nút `Chọn để giao` đưa bài vào form giao bài nằm ngay bên dưới/ cạnh danh sách.
- Form chung cho cả hai mode: `Bài tập` (được chọn từ list, không nhập ID thủ công), `Loại bài`, `Phạm vi` (`Cả phòng` / `Cá nhân`), `Học sinh` (dropdown danh sách thành viên trong phòng, chỉ hiện khi cá nhân), `Hạn nộp`, `Giao bài`.
- `DEBUG_COLLAB` chỉ cho phạm vi Cả phòng; `BUILD_FROM_SCRATCH` hỗ trợ Cả phòng hoặc Cá nhân.
- Sau giao thành công hiển thị card assignment đang mở, hạn, số học sinh nhận và trạng thái; không chuyển sang tab “Giao bài” riêng.
- Teacher có nút `Thử làm` để mở editor cá nhân/personal preview. Bài thử của teacher không được tính vào thống kê học sinh, contest standings hoặc report AC của lớp.

**Student view**

- Không có ngân hàng đề và không có form giao bài.
- Mặc định xem `Bài được giao`, có search/filter nhẹ; bài chưa làm, đang chấm, đã AC, quá hạn phải nhìn ra được.
- Click bài mở một trang/route làm bài có đề bên trái và editor bên phải; nút `Nộp bài`, status/feedback/testcase an toàn và danh sách nộp gần đây nằm ở dưới editor.
- Với `DEBUG_COLLAB`, nút mở/nhảy sang khu vực Code cộng tác trong cùng app và hiển thị statement/check result ở đó; không đưa sang editor cá nhân.
- Loại bỏ các nút `Tải bài được giao`, `Mở đề/nạp template`, `Nguồn nộp`, `Tải kỳ thi`, `Tải danh sách kỳ thi` khỏi workspace cá nhân. Danh sách được lấy khi mở trang và cập nhật qua refresh/invalidation ở cấp trang. Không bỏ khả năng chọn bài thi; khi vào contest, bối cảnh contest được chọn từ route/detail, không qua combobox rối.
- Submission status/history giữ trong trang bài. Không có global tab `Lịch sử & kết quả` cho student.

### 3.3 Báo cáo kết quả cho teacher

- Thay tab “Lịch sử & kết quả” chung bằng khối `Tiến độ lớp` trong Bài tập.
- Dashboard tóm tắt: số học sinh nhận bài, số đã nộp, số AC, số đang chấm, tỷ lệ AC, bài còn chưa hoàn thành.
- Bảng theo bài: mã/tên, mode, số đã nộp, số AC, tỷ lệ AC, thời gian trung vị nếu dữ liệu đủ, trạng thái deadline.
- Click bài mở danh sách học sinh; click học sinh mở các bài đã AC/chưa AC và submission details theo quyền teacher. Không phát tán source code hoặc điểm cá nhân cho học sinh khác.
- Định nghĩa AC nhất quán: `verdict=PASS/AC` theo judge, không tính submission `QUEUED`, `RUNNING`, `FAILED`, `SANDBOX_UNAVAILABLE` là AC.

### 3.4 Thi & xếp hạng

**Yêu cầu cốt lõi:** teacher chọn từ catalog bài trực tiếp khi tạo contest. Không yêu cầu tạo assignment trước và không bắt gõ UUID.

- Form tạo contest: tên contest, loại contest, bắt đầu sau/ngày giờ, thời lượng, search/filter catalog, list chọn đa bài, danh sách bài đã chọn với nút bỏ chọn, rule chấm/score, chính sách công bố BXH, nút Tạo kỳ thi.
- Khi click bài trong catalog, detail drawer hiển thị đề; teacher chọn bài vào contest. Không dùng ID assignment làm dữ liệu người dùng.
- Student thấy danh sách contest: sắp diễn ra/đang diễn ra/đã kết thúc, thời gian còn lại theo server time, số bài; click `Vào thi` mở workspace tương tự làm bài cá nhân.
- Có hai format để tôn trọng cả hai loại bài trong ngân hàng:
  - `INDIVIDUAL`: dùng `BUILD_FROM_SCRATCH`, điểm và AC thuộc từng student.
  - `TEAM_COLLAB`: cho phép `DEBUG_COLLAB`, team/room là đơn vị nộp và bảng xếp hạng. Không được âm thầm coi bài sửa lỗi cộng tác là bài cá nhân; nếu chưa triển khai team scoring thì UI phải đánh dấu format `TEAM_COLLAB` là “giai đoạn tiếp theo” và chặn tạo loại contest đó rõ ràng. Yêu cầu “bất kỳ bài nào trong ngân hàng” được đáp ứng thông qua chọn format phù hợp, không làm sai semantics của kết quả.
- Leaderboard: xếp số bài AC giảm dần trước theo yêu cầu; tie-break được công bố trước contest (gợi ý: tổng điểm giảm dần → thời điểm đạt AC cuối cùng sớm hơn → username để ổn định). Giữ `scoringRuleVersion` để contest cũ không thay luật khi cập nhật.
- Teacher drill-down: xem tổng AC, mỗi bài có bao nhiêu student/team AC, từng student/team AC bài nào, điểm và chi tiết submissions.
- Teacher có thể tạo contest thử; nộp thử của teacher phải phân biệt với student participant và không lọt vào BXH chính thức.

### 3.5 Chat / member / screen share

- Không để string `Server` như sender trong chat. `CHAT` chỉ dành cho tin nhắn người dùng; event lỗi/room state hiển thị ở status banner/toast.
- Student chỉ có Chat trong sidebar. Teacher có phần `Quyền sửa` và Chat (tab/section riêng hoặc toolbar) để quan sát/điều phối lượt sửa. Nếu teacher là người sửa thì hiển thị rõ quyền hiện tại.
- Header/sidebar hiện `Thành viên (N)` và cập nhật realtime. Danh sách hiển thị role, online/offline, ai đang giữ quyền sửa; nút quản lý thành viên chỉ teacher.
- Giữ nguyên cơ chế share screen đang hoạt động, chỉ chuyển cách đặt nút/tab vào cùng visual hierarchy với Code cộng tác và Thi & xếp hạng.

### 3.6 Bỏ Hoạt động / phản hồi

- Loại bỏ tab hiện tại nếu nó không có tác vụ hữu ích/được hỗ trợ.
- Trước khi xóa view, kiểm tra có lỗi/quyền/error events nào đang được chuyển vào `ActivityPanel`; chuyển các message vận hành quan trọng sang notification/toast/status log trước, để không nuốt mất lỗi.

---

## 4. Mockup tham khảo để duyệt hướng

Hai mockup được tạo riêng cho StudyFix (là concept minh hoạ, chưa phải ảnh chụp source chạy thật):

1. `a_clean_modern_web_app_dashboard_screenshot_ui.png` — trang ngân hàng + detail drawer + form giao bài, phục vụ teacher.
2. `a_clean_modern_web_app_ui_screenshot_of_a_coding.png` — workspace code cộng tác với member count/chat/sidebar, nút quyền sửa, kết quả kiểm tra và history thu gọn.
3. `wide_screenshot_of_a_web_application_dashboard_ui.png` — màn hình tạo kỳ thi trực tiếp từ ngân hàng, chọn nhiều bài, xem trước và bảng xếp hạng.

Thiết kế mục tiêu: nền sáng, navy sidebar, xanh/teal làm màu nhấn, các trạng thái PASS/FAILED rõ ràng, list + drawer thay cho nhiều màn hình rời. Đây là concept để duyệt bố cục trước khi viết giao diện thật; chữ trong ảnh sinh tự động có thể có vài lỗi nhỏ và cần dùng design spec khi code.

---

## 5. Thiết kế kỹ thuật — tránh phá source

### 5.1 Thêm web client song song, không thay thế ngay Swing

Cấu trúc dự kiến thêm vào project hiện có:

```text
StudyFix/
  server/                         # Java core + assignment module hiện tại
    src/studyfix/...              # giữ nguyên phần lớn; chỉ thêm API/logic đã được duyệt
  client/                         # Swing legacy giữ làm fallback trong giai đoạn chuyển đổi
  web-client/                     # React + TypeScript + Vite; mới
    src/app/                      # app shell, role-based navigation
    src/features/room/             # collab, chat, members, screen share
    src/features/exercises/        # catalog, assignment, work, teacher reports
    src/features/contests/         # create contest, contest workspace, leaderboard
    src/shared/                    # components, types, validation, api client
  docs/
    UI_UX_REDESIGN_PLAN.md
    API_CONTRACT.md
    MIGRATION_AND_ROLLBACK.md
```

Gateway mới là adapter, không phải server domain thứ hai. Nó sẽ map authenticated session tới các service hiện hành. Trình duyệt không nên nhận `sessionId` có thể giả mạo hoặc kết nối raw Java protocol không được bảo vệ.

### 5.2 API / domain changes cần có

- `CATALOG_LIST` / `CATALOG_GET`: hỗ trợ search, mode, topic, difficulty, paging; view công khai không bao gồm `referenceSolution` hoặc hidden test expected.
- `ASSIGN_CREATE`: vẫn là assignment thông thường, dùng `exerciseId` lấy từ catalog và `scope/targetUser/deadline`.
- `ASSIGN_LIST` / `ASSIGN_GET`: student chỉ xem assignment được giao cho mình hoặc ROOM; teacher xem theo quyền owner/room.
- `DEBUG_STARTER_LOAD` / `SHARED_CODE_STATE` / `SHARED_CHECK_CREATE`: giữ semantics version/hash; tự động nạp starter chỉ theo event giao bài mới và state guard. `SHARED_CHECK_CREATE` không thể nhận source/version tuỳ ý mà server không xác minh.
- `SUBMIT_CREATE`: student chỉ nộp `BUILD_FROM_SCRATCH` qua assignment hoặc contest context; teacher trial cần flag/record riêng và không được count vào student reports/leaderboard.
- `CONTEST_CREATE`: đổi từ `assignmentIds` sang danh sách `exerciseIds` đã chọn từ catalog, cùng contest type và scoring policy. Server xác minh role, mode/contest-type, giới hạn số bài/thời gian; không yêu cầu assignment tồn tại trước.
- `CONTEST_PROBLEMS_GET`, `CONTEST_SUBMIT`, `CONTEST_RESULT`, `LEADERBOARD_GET`: route rõ ràng theo contest; thời gian server là nguồn quyết định.
- `CLASS_PROGRESS`/`ASSIGNMENT_REPORT`: trả số student nhận/nộp/AC, drill-down có quyền. Chỉ teacher được đọc báo cáo lớp.
- Chat: event và user chat phân loại riêng.

### 5.3 Migration contest — điểm phải làm cẩn thận

Hiện `p3_contest_problems.assignment_id` FK tới `p3_assignments`; vì thế backend yêu cầu bài giao trước. Không chỉ sửa form client được.

Trước khi migration:

1. Copy/backup `data/studyfix-learning.db`; lưu schema version và test data.
2. Thêm schema version mới và transaction migration.
3. Chuyển contest API sang `exerciseIds`. Dữ liệu contest cũ phải giữ nguyên association/leaderboard; không âm thầm chuyển hoặc xoá.
4. Chọn một thiết kế lưu được contest problem độc lập với assignment thông thường. Phương án ưu tiên là bảng contest problem theo `exercise_id` + metadata snapshot/version; submission được nối vào contest problem. Nếu cần tái dùng `p3_submissions` (đang FK `assignment_id`), bổ sung nguồn/contest mapping nội bộ và lọc các record nội bộ khỏi danh sách assignment thông thường. Chỉ chọn sau khi kiểm tra số migration/queries còn dùng `assignment_id`.
5. Có migration test: DB cũ -> DB mới -> contest cũ vẫn đọc được; tạo contest từ exercise chưa assign thành công; submit contest ghi đúng bài/user; leaderboard cũ không đổi.

### 5.4 Giáo viên ưu tiên quyền sửa

- Quy tắc phải ở server `Room`/edit-session service, không chỉ ẩn queue trên UI.
- Teacher request phải được cấp quyền ngay, ưu tiên hơn người đang chờ.
- Nếu student đang có active draft, server phải gửi `TEACHER_TAKEOVER` và đánh dấu draft/version của student là stale. Không âm thầm ghi đè draft trên màn hình. Giữ nội dung nháp local/cho copy; khi student commit stale version, từ chối với thông báo rõ. Nêu policy takeover trong UI và ghi log.
- Test race giữa teacher click và student commit; server vẫn là nguồn sự thật.

### 5.5 Starter code tự cập nhật

- Giao `DEBUG_COLLAB` assignment phát một sự kiện có assignmentId, exerciseId, starterVersion, roomId.
- Nếu room code rỗng hoặc có thể xác nhận là code trước đó thuộc assignment cũ và không còn draft, tự nạp starter đúng một lần.
- Nếu room code đang có nội dung tự do hoặc draft chưa commit, không ghi đè; hiện dialog “Thay code phòng bằng starter của bài X?” với preview. `Nạp/Thay toàn bộ code (GV)` là thao tác manual override còn lại.
- Chỉ teacher thấy nút thay code; student không thấy và server vẫn kiểm tra permission.

---

## 6. Công nghệ UI đề xuất

### Web client

- React + TypeScript + Vite: UI components rõ và dễ tinh chỉnh, tốc độ phát triển cao.
- Tailwind CSS + bộ component accessible (ví dụ shadcn/ui) để có design tokens, form/table/dialog/tooltip nhất quán.
- Monaco Editor hoặc CodeMirror 6 cho editor code; lựa chọn cuối cùng theo bundle size và khả năng tích hợp keyboard/code.
- TanStack Query (hoặc lớp fetch/cache tương đương) cho loading, retry, cache và invalidation; tránh phải nhấn “Tải lại” thủ công.
- WebSocket cho event thời gian thực và trạng thái phòng; REST/JSON cho catalog, assignment, submission, contest/report.
- Screen sharing: giai đoạn đầu có thể giữ native Swing screen share hoạt động; để web browser share trực tiếp cần browser `getDisplayMedia` + WebRTC hoặc gateway relay được thiết kế an toàn. Không coi TCP 5001 hiện tại tự động tương thích với browser.

### Server Manager

- Giai đoạn đầu giữ Swing `ServerAdmin` riêng và bổ sung chỉ số lấy từ server/assignment worker: trạng thái 5000/5001/5002, connections, rooms, active editor/queue, queue depth, worker states, log, graceful stop/restart.
- Không khởi tạo song song hai server để có GUI. Cùng một runtime phải sở hữu 3 listener; manager chỉ điều khiển instance đó.
- Các nút vận hành phải dùng cùng state/service với server thật. Không hiển thị “đang chạy” chỉ vì socket port còn nghe từ một process khác.

---

## 7. Roadmap theo các mốc nghiệm thu

### M0 — Baseline, backup, traceability

- Lưu ZIP gốc, copy database test, hash các file core và log build/test hiện tại.
- Chụp baseline các luồng login, room/chat, code edit/history, screen share, assignment/check, submission, contest/leaderboard.
- Lập mapping `Requirement ID -> file/API -> test case`; mọi thay đổi mới đều gắn requirement ID.
- Nghiệm thu: build baseline; mọi lỗi có trước phải ghi rõ, không quy là lỗi do UI mới.

### M1 — Sửa nghiệp vụ contest và test backend trước khi đổi UI

- Contest chọn exercise từ catalog, không đòi assignment có sẵn.
- Quy định format cá nhân/nhóm rõ ràng để bao phủ cả hai `ExerciseMode`.
- Teacher preview/trial tách khỏi student stats.
- Migration database có backup/rollback, giữ contest cũ.
- Nghiệm thu: tạo contest với `SEQ001` chưa assign; student vào thi/submit; contest cũ và BXH cũ vẫn hoạt động.

### M2 — Hoàn thiện workflow code cộng tác

- Bỏ nút Tải code visible, đổi sang auto sync + action “Đồng bộ lại” chỉ khi cần.
- Role-based controls; teacher takeover ưu tiên ở server; student không thấy full-code loader.
- Auto starter load có guard, statement cạnh editor, test button/result embedded, history accordion dưới cùng.
- Nghiệm thu: commit ≠ PASS; test theo đúng version; race/version stale; teacher takeover không silent-loss draft; share screen unchanged.

### M3 — Thiết kế component & role matrix

- Token màu, typography, spacing, focus/keyboard, status/error pattern, empty/loading/error state.
- Role-based routing/navigation; student không tải catalog/admin controls.
- Nghiệm thu: UI review bằng mockup/interactive prototype trước khi migrate full; accessibility/keyboard basics.

### M4 — Teacher Exercise Center

- Catalog list + filtering + detail drawer, assignment form inline, dropdown student list, teacher practice preview.
- Teacher class progress report AC overview and drill-down.
- Nghiệm thu: giao ROOM và INDIVIDUAL; DEBUG_COLLAB chỉ ROOM; student không thể gọi admin API bằng cách tự gửi request.

### M5 — Student Practice Workspace

- Bài được giao list, click mở detail + editor + submit; status/history nằm tại bài.
- Bỏ tab global history/results cho student; không lộ ngân hàng.
- Nghiệm thu: user A/B không xem submission của nhau; source snapshot bất biến; retry không tạo submission đôi.

### M6 — Contest and leaderboard workspace

- Teacher creates contest from catalog and selects problem list; student sees contest list/workspace; live timer driven by server time; leaderboard/drill-down.
- Scoring rule explicit and versioned; tie-break tests; hide/unhide ranklist according to policy.
- Nghiệm thu: boundary just before/start/end, submission after end rejected, AC count sorted descending, legacy contests preserved.

### M7 — Web gateway and frontend integration

- Build new `web-client/` alongside Swing client and add gateway adapter. Do not shut down legacy client until parity.
- Authentication/session handoff uses server-issued tokens; authorization checked server-side on every route; use rate limits/body size limits; no hidden tests/reference source in responses.
- Integrate browser editor, websocket room events and screen sharing (if share screen directly in browser, implement a dedicated WebRTC path; do not tunnel unsafe raw TCP protocol to browser).
- Nghiệm thu: every current role flow passes from browser; legacy Swing still builds and runs.

### M8 — Regression, performance, usability sign-off

- 3 clients: teacher + student A + student B; at least 2 rooms.
- Verify screen share, chat, collaboration lock/turns, edit history, assignment, Docker grading, contests and reports.
- Restart server between submissions and verify data durability. Simulate Docker stopped/image missing/queue full/timeout.
- Do a teacher walkthrough and student walkthrough; count clicks and remove redundant ones.
- Nghiệm thu: all P0/P1 cases pass, no regressions in core, DB migration backup/rollback verified, known limitations documented.

---

## 8. Test matrix bắt buộc

### Contest

- Create contest from `SEQ001` when no assignment exists -> succeeds.
- Select multiple exercises from catalog -> exact list order/IDs saved.
- Try DEBUG_COLLAB in individual contest -> rejected with clear reason; same problem allowed in TEAM_COLLAB only if that mode is enabled.
- Submit after start; reject before start and after end using server clock.
- Old contests and leaderboard after migration remain readable.
- Teacher's test submission never counts in official ranks.
- Score/AC count/tie-break deterministic and versioned.

### Collaboration

- Student sees no `Nạp toàn bộ code (GV)` and no catalog/admin page.
- Teacher can take edit turn immediately even with queue; student draft isn't silently lost.
- Assignment DEBUG_COLLAB automatically loads the correct starter when safe.
- Starter doesn't overwrite independent room code/draft without explicit confirmation.
- Check button disabled until commit/no active draft; result records checked version; newer version clearly marked.
- Hidden tests don't reveal expected output/reference source.

### Exercise UX / privacy

- Search/filter by code, title, topic, difficulty, mode.
- Teacher selects item by click and hands into assignment form, no manual UUID.
- Individual target is selected from dropdown of room students; invalid/non-room target is rejected.
- Student doesn't see bank, assignment manager, teacher reports, global history tab.
- Teacher sees per-problem count AC and student drill-down; other students can't access it.
- Student's status/history stays on the current task workspace.

### Core regression

- Login/logout, create/join/leave room, membership, chat isolation, turn queue, draft preview, commit/cancel, history, screen share, disconnect/reconnect, shutdown/restart.
- No blocking network call on Swing EDT or browser UI thread.
- `SANDBOX_UNAVAILABLE`, compile error, WA, runtime error, timeout, queue full are distinguishable; no fake PASS.

---

## 9. Phản biện / chốt quy tắc để tránh thiết kế sai

1. **“Bất kỳ bài nào từ ngân hàng cho kỳ thi”**: UX cần chọn trực tiếp từ catalog và không cần assign trước. Nhưng `DEBUG_COLLAB` là bài làm nhóm còn `BUILD_FROM_SCRATCH` là bài cá nhân; muốn hỗ trợ thực sự cả hai thì phải định nghĩa hai contest format (`INDIVIDUAL`, `TEAM_COLLAB`). Không nên bỏ kiểm tra mode và đưa shared snapshot vào leaderboard cá nhân một cách âm thầm.
2. **Teacher quyền cao nhất**: bypass queue có thể làm mất draft học sinh. Quyền ưu tiên phải ở server và có takeover notification/version conflict; không nên chỉ disable hàng đợi ở client.
3. **Tự nạp starter**: nên tự động ở lần giao bài nếu room code rỗng/đã an toàn; không tự overwrite code phòng không liên quan. Full-code load giữ là công cụ teacher có xác nhận, không phải thao tác bắt buộc cho mọi bài.
4. **Ẩn nút trên student không đủ bảo mật**: API vẫn xác thực role/user/room/assignment; student tự tạo request cũng phải bị chặn.
5. **Đưa UI lên web là thay đổi đáng kể**: cần API gateway, session handling và phương án screen share tương thích browser. Vì vậy giữ Swing fallback cho đến khi web client hoàn tất test parity; không xóa Swing ngay.
6. **Teacher tự làm để test**: tách riêng `teacher_trial`, không để điểm test của giáo viên làm sai thống kê AC/leaderboard học sinh.
7. **Kết quả AC lớp**: phải tính từ verdict đã chấm xong, không từ trạng thái submitted/queued hoặc kết quả shared check của cả phòng.

---

## 10. Checklist nghiệm thu cuối

- [ ] Contest được tạo từ catalog trực tiếp, không cần assignment trước.
- [ ] Cho phép chọn mọi bài với contest format phù hợp; semantics cá nhân/nhóm rõ.
- [ ] Main navigation gọn; không còn tab lồng 7 + 6 gây rối.
- [ ] Shared code page có statement, editor, shared check, result và history cùng một workspace.
- [ ] Tự đồng bộ shared code; không có nút Tải code thường trực.
- [ ] Teacher full-code load; student không thấy chức năng đó.
- [ ] Teacher có quyền ưu tiên sửa ở server, active draft không bị mất âm thầm.
- [ ] Chat không coi server notification là người chat; student thấy Chat, teacher có thêm quyền sửa.
- [ ] Member count hiển thị rõ và cập nhật realtime.
- [ ] Screen share giữ nguyên hoạt động qua regression.
- [ ] Teacher-only bank/assignment/class report; student thấy bài được giao và editor cá nhân.
- [ ] Student submission history ở ngay bài; không có global history/result page.
- [ ] Teacher report drill-down được AC theo lớp, bài, học sinh.
- [ ] Tab Activity/Feedback được bỏ chỉ sau khi chuyển toàn bộ lỗi/notification quan trọng sang toast/status/log.
- [ ] DB migration/rollback, contest cũ, assignment cũ, submission cũ được kiểm tra.
- [ ] Swing client cũ vẫn có thể build/chạy đến khi web parity được nghiệm thu.
- [ ] Không thay đổi ngoài scope nếu không có requirement ID và phê duyệt trong plan.

## 11. Điểm cần duyệt trước khi code

Plan này đề xuất hướng mục tiêu **web client hiện đại + Java backend hiện tại + Server Manager riêng**, với chuyển đổi tăng dần. Mockup trong thư mục giao hàng là concept thị giác, không phải UI đã được lập trình. Bắt đầu implementation chỉ sau khi mày duyệt 3 điều: (a) hướng web client song song Swing; (b) phân biệt contest cá nhân và contest nhóm để xử lý mọi mode; (c) policy teacher takeover khi student có draft đang mở.
