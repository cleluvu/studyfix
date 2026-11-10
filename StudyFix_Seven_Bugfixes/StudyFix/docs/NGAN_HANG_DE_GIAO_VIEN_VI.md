# Ngân hàng đề của giáo viên và luyện tập của sinh viên

## Chức năng theo vai trò

Giáo viên dùng API `BANK_LIST/GET/UPDATE/DELETE` và form `CATALOG_CREATE` để quản lý đề. Trang ngân hàng và Week không có kết quả AC/WA, bộ lọc trạng thái luyện tập, editor hay lịch sử code cá nhân. Bốn thẻ quản lý là Tổng số bài, Đã xuất bản, Bản nháp, Đã sử dụng trong đề thi. Bảng tất cả bài tập gồm Chọn / Mã bài / Tên bài / Category / Độ khó / Trạng thái / Số lần dùng / Thao tác.

Sinh viên tiếp tục dùng `PRACTICE_*` để làm bài, chạy thử, nộp bài và xem lịch sử của chính mình. Sidebar sinh viên ghi Luyện code. AC/chưa đạt và màu kết quả chỉ có ở luồng luyện tập. Sinh viên không được gọi API quản lý ngân hàng; backend kiểm tra quyền độc lập với giao diện.

## Hướng dẫn sử dụng

1. Đăng nhập giáo viên → Ngân hàng câu hỏi → Tất cả bài tập.
2. Tạo bài tập: nhập nội dung và testcase bằng form hiện có. Chọn Đã xuất bản hoặc Bản nháp. Mọi đề vẫn cần đủ dữ liệu hợp lệ và ít nhất một testcase công khai.
3. Bản nháp chỉ có tác giả được quản lý; chưa dùng được để giao bài, tạo kỳ thi, chọn code lỗi mới trong lớp hoặc luyện tập. Thống kê của mỗi giáo viên bao gồm đề công khai và bản nháp của mình.
4. Nút mắt chỉ xem trước đề và ví dụ công khai. Nút bút mở form sửa đã điền nội dung và testcase. Nút thùng rác yêu cầu xác nhận xóa mềm khỏi ngân hàng.
5. Giáo viên chọn các bài đã xuất bản để Thêm vào đề thi hoặc Giao bài cho lớp. Giữ quy tắc cũ: tạo kỳ thi cá nhân chọn 1–20 bài lập trình từ đầu; giao bài qua form hiện có chọn một bài, cần đang trong lớp. Nút sẽ bị vô hiệu khi lựa chọn chưa hợp lệ.
6. Trong Week, Dạng bài lấy từ `topic`, không phải `mode`. Xem/Sửa/Xóa là thao tác với đề. Nút × riêng có tooltip Gỡ khỏi Week chỉ gỡ liên kết, giữ câu hỏi trong ngân hàng.

Không có dropdown lọc trạng thái trên trang giáo viên. Trạng thái xuất bản vẫn được hiển thị trong bảng. Số lần dùng là số kỳ thi khác nhau chứa câu hỏi, cộng theo toàn bộ phiên bản của cùng đề; không phải số lần sinh viên nộp bài. Thẻ Đã sử dụng trong đề thi đếm số đề khác nhau có số lần dùng lớn hơn 0.

## Dữ liệu và bảo toàn các luồng cũ

Hệ thống giữ các database đang dùng. Metadata ngân hàng nằm trong bảng mới `p3_question_bank` ở database học tập SQLite, cùng `p3_exercises` và testcase. Không chuyển hoặc đặt lại database tài khoản/lớp MySQL.

Migration chạy tự động, lặp lại an toàn khi server khởi động:

- Thêm bảng metadata và chỉ mục một phiên bản hiện hành trên mỗi đề.
- Ghi nhận bài cũ là Đã xuất bản để tiếp tục sử dụng.
- Bài cũ chưa có tác giả được xem là ngân hàng dùng chung, giáo viên quản trị được chỉnh sửa. Phiên bản sửa mới ghi nhận người sửa làm tác giả; từ đó chỉ tác giả được sửa/xóa. Bài mới ghi tác giả ngay khi tạo.
- Không ghi đè metadata, nội dung hay testcase cũ khi khởi động lại.

Mỗi lần sửa luôn tạo **bản exercise/testcase mới** trong một transaction; bản cũ được lưu nguyên vẹn. Mã hiển thị được giữ, ID nội bộ của phiên bản mới thay đổi, revision tăng. Các liên kết Week chuyển sang phiên bản mới trong cùng transaction. Bài giao, kỳ thi và bài nộp đã có tiếp tục trỏ đến ID cũ, giữ đề/testcase đã sử dụng, kể cả lượt đang chấm.

Xóa đề chỉ đánh dấu xóa trên metadata của cả nhóm phiên bản. Đề biến mất khỏi ngân hàng và danh sách luyện tập; tạo bài giao/kỳ thi mới bằng ID đã xóa hoặc cũ bị chặn. Các dữ liệu exercise, testcase, assignment, contest và submission vẫn tồn tại để các luồng đã giao tiếp tục hoạt động.

Sửa/xóa kiểm tra quyền tác giả và revision hiện tại ở server, chống ghi đè từ hai cửa sổ hoặc request cũ. Sinh viên không đọc được đề nháp/test ẩn qua API luyện tập. Giáo viên khác chỉ được xem bản công khai, không có quyền sửa/xóa đề của tác giả và không nhận testcase ẩn.

## File chính

- `server/src/studyfix/assignment/persistence/QuestionBankRepository.java`: migration metadata, quyền, thống kê sử dụng, phiên bản bất biến, xóa mềm và transaction.
- `AssignmentRepository.java`: khởi tạo schema metadata và lọc đề chưa xuất bản/đã xóa/phiên bản cũ khỏi danh sách dùng mới.
- `AssignmentService.java`: API quản lý, tái sử dụng kiểm tra dữ liệu tạo đề, chặn dùng bản nháp/đã xóa để giao bài hoặc tạo kỳ thi.
- `QuestionWeekRepository.java`: số bài theo quyền, lọc đề không khả dụng và giữ liên kết tuần.
- `WebGatewayServer.java`, `AssignmentTcpServer.java`: cho phép action quản lý mới qua gateway có xác thực, kiểm tra đề khi chọn code lỗi mới trong lớp.
- `web-client/static/features/exercises/teacher-bank.js`: xem trước, sửa và xóa đề của giáo viên.
- `catalog-create.js`: form tạo/sửa dùng chung; chọn trạng thái xuất bản, điền đầy đủ testcase, gửi revision khi sửa.
- `practice.js`: lựa chọn API và giao diện theo vai trò, giữ luồng làm bài cho sinh viên.
- `question-bank-view.js`, `week-bank-view.js`, `week-bank.js`, `question-bank.css`, `ui/render.js`: thống kê, bảng, thao tác và nhãn menu theo vai trò.

## Cập nhật và kiểm thử

Đóng Server Manager cũ, chạy lại `run-server-manager.ps1` để build backend mới, sau đó Ctrl+Shift+R ở trình duyệt. Migration chạy tự động; không cần xóa database hay tạo lại tài khoản.

Kiểm thử bằng hai tài khoản:

1. Teacher tạo đề ở trạng thái Bản nháp. Teacher thấy đề, Student tìm theo mã không thấy và không mở được ID trực tiếp.
2. Teacher sửa đề, chọn Đã xuất bản, lưu. Student tải lại thấy đề và vẫn làm/nộp được.
3. Teacher dùng đề tạo kỳ thi, kiểm tra số lần dùng tăng theo số kỳ thi.
4. Teacher sửa nội dung/testcase. Kỳ thi cũ vẫn dùng đề và testcase trước sửa; lần giao mới dùng phiên bản mới.
5. Teacher xóa đề. Ngân hàng không còn đề; kỳ thi đã giao vẫn mở được. Khởi động lại server không khôi phục đề đã xóa.
6. Teacher khác đăng nhập: không thấy bản nháp riêng và không sửa/xóa được đề của tác giả.
7. Mở hai form sửa cùng revision, lưu một form rồi lưu form còn lại: server yêu cầu tải lại thay vì ghi đè.

Ant target `question-bank-test` chạy HTTP thật với SQLite tạm, gồm quyền, migration, bản nháp, rò rỉ test ẩn, revision cũ, số lần dùng, giữ testcase cũ, xóa mềm và khởi động lại. Các target `question-week-test`, `practice-test`, `exam-mode-test` kiểm tra hồi quy tương ứng. Không dùng database thật hoặc Docker để tạo kết quả giả trong các test này.

`web-client/tests/question_bank_browser.cjs` kiểm tra Chrome với HTTP fixture: Teacher có xem/tạo/sửa/xóa, không có editor/AC/lọc kết quả; Student có editor/lịch sử, không có quản lý; tìm kiếm, Week và responsive. Ảnh fixture nằm ở `server/build/question-bank-qa`, không phải dữ liệu được tự thêm vào hệ thống.
