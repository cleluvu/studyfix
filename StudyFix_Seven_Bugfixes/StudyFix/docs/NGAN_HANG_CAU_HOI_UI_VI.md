# Ngân hàng câu hỏi: Tất cả bài tập và Theo Week

> Cập nhật mới: chức năng ngân hàng của giáo viên đã tách khỏi luyện tập sinh viên. Xem [Ngân hàng đề giáo viên](NGAN_HANG_DE_GIAO_VIEN_VI.md) để biết API, migration, quyền sửa/xóa, trạng thái xuất bản và cách kiểm thử hiện hành. Các đoạn mô tả trước đây về AC/WA dùng chung cho hai vai trò bên dưới đã được thay thế.

## Template tái tạo theo hai ảnh tham chiếu — 09/11/2026

Bản cập nhật này chỉ thay lớp trình bày. Giữ nguyên database, schema, endpoint, xác thực/phân quyền, luồng tạo/giao bài, tạo kỳ thi, quản lý Week, tìm kiếm, chấm bài và lịch sử nộp hiện có.

`web-client/static/template.css` và `ui/template.js` cung cấp sidebar tím than 275 px, header trắng 60 px, breadcrumb, menu tài khoản, bộ SVG, màu và thành phần dùng chung trên các trang quản lý. Sidebar giữ các đường dẫn và quyền hiện tại, thương hiệu StudyFix và hai mục con Tất cả bài tập / Theo Week. Menu tài khoản ở header dùng thao tác Đăng xuất hiện có. Biểu tượng thông báo chỉ có chấm khi có số tin nhắn chưa đọc thật.

Hai trang ngân hàng được thay renderer/style theo ảnh: trang tất cả có bốn thẻ thống kê, thanh lọc, bảng và thanh chọn/giao bài; trang Week có danh sách tuần bên trái, chi tiết/tab/bảng bên phải. Bảng dài cuộn trong panel Week. Kiểm tra bố cục tại 1672 × 941 và mobile 390 × 844.

Các cột hiện tại thay thế mô tả cột của bản trước bên dưới:

- Tất cả bài tập: Chọn (Teacher), Mã bài, Tên bài tập, Category, Độ khó, Kết quả, Thao tác. Đã bỏ cột Loại bài.
- Theo Week: Chọn (Teacher), Mã bài, Tên bài tập, Dạng bài, Độ khó, Thao tác. Dạng bài lấy trực tiếp từ `topic` hiện có trong database, ví dụ Mảng, Mảng một chiều, Lớp đối tượng nếu dữ liệu được nhập như vậy; không lấy từ `mode` Lập trình/Sửa lỗi. Kết quả AC/chưa đạt được giữ bằng màu hàng và nhãn trong tên bài.
- Các bộ lọc vẫn dùng trường và API hiện tại. Bộ lọc chủ đề trên Week đổi nhãn thành Dạng bài; bộ lọc chế độ xử lý của trang tất cả giữ nguyên hành vi.

Các số thống kê dùng Tổng bài / Đã AC / Chưa đạt / Chưa làm từ API thực tế. Nút mắt mở bài bằng action cũ; nút thùng rác trong Week chỉ gỡ bài khỏi tuần bằng action cũ. Không thêm trạng thái xuất bản hoặc sửa/xóa câu hỏi toàn ngân hàng khi chưa có nghiệp vụ/API tương ứng.

Các file trình bày được cập nhật: `ui/render.js`, `ui/template.js`, `template.css`, `question-bank.css`, `week-bank.css`, `index.html`, hai renderer `question-bank-view.js` và `week-bank-view.js`, cùng phần render bảng/bộ lọc trong `practice.js`. Không thay handler nghiệp vụ trong `practice.js`.

Kiểm chứng: 20 script hồi quy frontend; Chrome kiểm tra hai bảng, tìm kiếm khi gõ, các tab, quản lý Week bằng HTTP fixture, màu kết quả, phân trang, phân quyền hiển thị, menu tài khoản, template trang quản lý và điều hướng mobile. Ảnh nằm ở `server/build/question-bank-qa`. Fixture chỉ dùng trong kiểm thử, không ghi vào database thật. Đối chiếu SHA-256 xác nhận 65 file backend/core/nghiệp vụ và phần handler luyện tập không đổi, ngoại trừ import SVG trình bày. Không yêu cầu migration hoặc khởi động lại backend cho bản chỉnh template này; nhấn Ctrl+Shift+R để tải lại CSS/JS.

Sidebar có đúng hai mục con: **Tất cả bài tập** và **Theo Week**. Không có mục Theo Category. Bộ lọc Category vẫn nằm trong danh sách tất cả bài tập và dùng trường chủ đề (`topic`) hiện có.

## Giao diện

Tất cả bài tập giữ bảng toàn chiều rộng, bốn ô thống kê, tìm kiếm khi gõ, các bộ lọc và phân trang. Bài đã AC có nền xanh, bài chưa đạt có nền đỏ, đồng thời có nhãn kết quả.

Theo Week dùng bố cục mẫu: danh sách tuần bên trái, nội dung tuần được chọn bên phải. Mỗi tuần có số Week, tên, mô tả và số bài thực tế. Bên phải có ba tab: Danh sách bài tập, Thông tin Week, Thống kê. Thanh tìm kiếm lọc khi gõ, cùng bộ lọc loại bài và độ khó. Bảng giữ mã, tên, chủ đề, loại bài, độ khó, kết quả cá nhân và thao tác làm bài. Trên màn hình nhỏ, danh sách tuần chuyển thành thanh cuộn ngang, bảng cuộn trong vùng riêng.

Các loại bài vẫn là **Lập trình từ đầu** và **Sửa lỗi**, đúng dữ liệu hiện tại. Không giả lập Method/Program/Class, số tuần, số câu hay các trạng thái xuất bản chưa có trong source.

## Cách sử dụng

1. Đăng nhập giáo viên, mở Ngân hàng câu hỏi → Theo Week → **Thêm Week**.
2. Nhập số Week (1–99), tên nội dung và mô tả. Số Week không được trùng trong ngân hàng chung.
3. Chọn Week → **Thêm bài vào Week**. Tìm mã/tên bài, tích chọn trên các trang rồi bấm **Thêm câu hỏi đã chọn**. Mỗi lần thêm tối đa 100 bài. Bài đã có được đánh dấu và không thêm trùng.
4. Nút **×** cạnh bài chỉ gỡ liên kết với tuần, không xóa câu hỏi khỏi ngân hàng hoặc lịch sử nộp.
5. **Quản lý Week** cho phép sửa tên/nội dung/số tuần hoặc xóa tuần do giáo viên đó tạo. Xóa tuần giữ nguyên câu hỏi và bài nộp.
6. Giáo viên có thể tích bài để mở form tạo đề thi hoặc giao bài hiện có. Thêm vào đề thi hỗ trợ 1–20 bài Lập trình từ đầu; giao bài hiện tại chọn một bài. Các thao tác này cần đang tham gia lớp và vẫn dùng API phân quyền hiện có.
7. Sinh viên xem các tuần và luyện bài, không có các nút tạo/sửa/xóa/giao bài. Thống kê AC/chưa đạt/chưa làm là kết quả của chính tài khoản đang xem.

Week thuộc ngân hàng chung, dùng được ngoài phòng học. Mọi tài khoản đăng nhập đều được xem; chỉ giáo viên tạo Week được sửa, thêm/gỡ bài hoặc xóa Week đó. Không tự tạo tuần mẫu khi triển khai; lần đầu cần giáo viên thêm tuần.

## Luồng và file xử lý

- `web-client/static/ui/render.js`: menu con Tất cả bài tập / Theo Week.
- `web-client/static/core/state.js`: nhớ loại màn ngân hàng khi tải lại trang.
- `features/exercises/practice.js`: chuyển màn, lọc bài trong tuần, chọn bài và tái sử dụng editor/chạy/nộp/lịch sử.
- `features/exercises/question-bank-view.js`: bảng tất cả bài và header dùng chung.
- `features/exercises/week-bank-view.js`: bố cục tuần, danh sách, thông tin và thống kê.
- `features/exercises/week-bank.js`: tải tuần, form tạo/sửa, quản lý tuần và chọn bài từ ngân hàng.
- `web-client/static/week-bank.css`, `question-bank.css`, `index.html`: giao diện tím–trắng và responsive.
- `server/src/studyfix/WebGatewayServer.java`: cho phép các action Week qua gateway xác thực hiện có.
- `server/src/studyfix/assignment/service/AssignmentService.java`: xử lý `PRACTICE_WEEK_LIST/SAVE/DELETE/ADD/REMOVE`; `PRACTICE_LIST` nhận thêm `weekId` để giới hạn danh sách, thống kê và phân trang.
- `server/src/studyfix/assignment/persistence/QuestionWeekRepository.java`: lưu tuần và liên kết bài, kiểm tra quyền giáo viên tạo, ràng buộc số tuần duy nhất, transaction thêm/gỡ bài và xóa liên kết.

Hai bảng `p3_question_weeks` và `p3_question_week_items` được tạo tự động trong database học tập SQLite hiện có khi AssignmentService khởi tạo. Không thay database quản lý tài khoản/lớp MySQL. Câu hỏi vẫn dùng `p3_exercises`, không nhân bản source hay bài nộp. Liên kết tuần–bài được lưu bền vững sau khi tắt/mở server.

## Cập nhật và kiểm thử

Đóng Server Manager cũ, chạy lại lệnh khởi động server hiện có để build backend mới. Sau đó nhấn **Ctrl + Shift + R** ở trình duyệt. Backend cũ sẽ chưa nhận các action Week.

Backend: Ant target `question-week-test` dùng HTTP thật, gateway/service thật và SQLite tạm. Kiểm tra tạo/sửa/xóa, chặn Student và giáo viên khác sửa, số tuần trùng, thêm bài không trùng, transaction lỗi không ghi dở, tìm kiếm trong tuần, gỡ bài không xóa câu hỏi và dữ liệu tồn tại sau khởi động lại. Target phụ thuộc `classroom-test` để kiểm tra hồi quy lớp học.

Frontend: `question_bank_browser.cjs` dùng Chrome với dữ liệu HTTP kiểm thử có khai báo, kiểm tra hai menu, bố cục desktop/mobile, tạo/sửa/xóa tuần, chọn/gỡ bài, các tab, tìm khi gõ, phân trang, màu AC/chưa đạt và ẩn quản lý với Student. Ảnh kiểm chứng nằm ở `server/build/question-bank-qa/teacher-week-desktop.png` và `teacher-week-mobile.png`. Đây là ảnh fixture để kiểm tra UI, không phải dữ liệu tự thêm vào ngân hàng của người dùng.

Các script frontend đang dùng cho web tiếp tục được chạy. Script tĩnh cũ `check_seven_bugfixes.mjs` còn tham chiếu thư mục Swing client đã bị xóa trước yêu cầu này nên không được tính là kiểm thử đạt của thay đổi Week.
