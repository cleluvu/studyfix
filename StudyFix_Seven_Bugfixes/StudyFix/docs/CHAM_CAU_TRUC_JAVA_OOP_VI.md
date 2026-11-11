# Chấm cấu trúc Java OOP

Chức năng được bật theo từng bài tại Tạo bài tập → Chấm điểm → Chấm cấu trúc Java OOP. Bài cũ mặc định không bật. Chỉnh sửa bài đã phát hành giữ lại rubric hiện có; sau khi sửa cần kiểm định và duyệt/phát hành lại theo luồng bài tập.

## Soạn tiêu chí

- Bật kiểm tra cấu trúc. Tỷ trọng mặc định 30%, phần còn lại chấm test hành vi; tỷ trọng 100% chỉ chấm cấu trúc.
- Thêm tiêu chí TYPE, FIELD, CONSTRUCTOR, METHOD, EXTENDS, IMPLEMENTS, OVERRIDE, ANNOTATION.
- Tên class là tên chứa khai báo, ví dụ Student. Tên kiểu nên dùng tên đầy đủ, ví dụ java.lang.String. Danh sách tham số phân cách bằng dấu chấm phẩy: java.lang.String;double. Không ghi tên biến tham số.
- TYPE dùng CLASS, INTERFACE, ENUM, RECORD hoặc ANNOTATION ở ô kiểu. FIELD/METHOD dùng kiểu field/kiểu trả về. CONSTRUCTOR dùng danh sách tham số, bỏ trống tên method và kiểu trả về.
- Modifier bắt buộc/bị cấm là các tên viết hoa, phân cách dấu chấm phẩy. Ví dụ FIELD private và cấm STATIC vẫn cho phép private final nếu không cấm FINAL.
- EXTENDS/IMPLEMENTS đặt target là kiểu cha/interface. Mặc định phải trực tiếp; bỏ chọn trực tiếp thì chấp nhận quan hệ subtype gián tiếp.
- METHOD/OVERRIDE mặc định phải khai báo ngay trong class. Có thể tắt để xét member kế thừa. OVERRIDE dùng target là class/interface khai báo method gốc; ANNOTATION với target java.lang.Override kiểm tra annotation trong source, không thay thế kiểm tra override thật.
- Không có quy tắc tự suy diễn tên field/method từ lời giải chuẩn. Mỗi yêu cầu phải được giáo viên cấu hình rõ.

Mỗi tiêu chí có trọng số riêng; tổng trọng số không bắt buộc bằng 100. Điểm cấu trúc chia theo tỷ lệ trọng số đạt. Tùy chọn bắt buộc yêu cầu tất cả tiêu chí đạt, nếu không thì 0 điểm và không chạy test hành vi. Nếu không bắt buộc, thiếu API khiến harness không biên dịch được sẽ không nhận điểm hành vi, nhưng có thể nhận phần điểm cấu trúc đã đạt. Lỗi hạ tầng không nhận điểm.

Ví dụ 5 tiêu chí bằng nhau, tỷ trọng 30% cấu trúc, 70% hành vi: 3/5 tiêu chí đạt và toàn bộ hành vi đúng → 18 + 70 = 88/100. Bật bắt buộc → 0/100. private final không bị chấm sai private; static method hoặc method thiếu public không thỏa quy tắc PUBLIC và cấm STATIC.

## Kiểm định và phản hồi

Chạy kiểm định bằng lời giải chuẩn và mẫu sai trước phát hành. Mẫu sai bị STRUCTURE_ERROR được tính là bộ chấm đã phát hiện lỗi; lời giải chuẩn phải vượt cả tiêu chí cấu trúc và hành vi đang áp dụng. Tiêu chí được thêm vào nội dung đề công khai khi phát hành/import để sinh viên biết yêu cầu.

Báo cáo có ruleId, kind, passed, message, line, weight, points và maxPoints. Kết quả thi tuân thủ thời điểm công bố và chế độ hiển thị test: NONE ẩn, PASSED chỉ tiêu chí đạt, FAILED chỉ tiêu chí không đạt. Giảng viên xem đầy đủ. Công bố kết quả chốt cả báo cáo cấu trúc; chấm lại không thay đổi báo cáo đã công bố.

Các snippet Reflection cũ vẫn chạy như test hành vi. Cần rà soát hoặc thay các snippet kiểm tra modifier bằng phép so sánh toàn bộ bitmask, tránh mâu thuẫn với rubric mới. Hệ thống không tự sửa bộ test hay chấm lại bài nộp cũ. AST kiểm tra hợp đồng khai báo, không chứng minh thuật toán đúng, immutable đúng hoặc chống hard-code; cần test hành vi và các mẫu sai đủ mạnh.

## Kỹ thuật và SQLite

StructureRules kiểm tra schema, tối đa 100 quy tắc, không nhận code checker. StructureInspector dùng JavacTask.parse/analyze, Elements, Types và Trees. Mã inspector và JSON parser đáng tin được đóng gói dưới dạng source trong JAR bằng hook Ant; biên dịch Java 17 và phân tích bên trong Docker. Không nạp class sinh viên vào JVM server, không chạy static initializer, tắt annotation processor. Giới hạn CPU/RAM, mạng, output, execution time áp dụng qua DockerJavaSandbox. Mỗi lượt có thêm chi phí khởi chạy/biên dịch inspector.

Rubric lưu ở payload_json.grading.structure của p3_builder_drafts và snapshot phát hành; hợp đồng Judge lưu ở p3_oj_exercises.config_json.structure_rules. Gói import OJ hỗ trợ trường structure_rules, vẫn giữ định dạng manifest hiện có. Export/import ngân hàng đề tiếp tục tham chiếu snapshot bài tập có checksum bao gồm cấu hình Judge; phải có bài tương ứng ở máy nhận.

p3_structure_results(submission_id PRIMARY KEY, result_json NOT NULL) lưu báo cáo theo receipt luyện tập, bài giao, lượt thử giáo viên và bài thi, cùng giao dịch ghi kết quả. Các bản công bố thi chốt structureResults trong rows_json. Không thay đổi dữ liệu cấu hình của các bài cũ khi thêm bảng này.

Chạy `ant oop-structure-test` để kiểm tra Docker thật, private final, public/static, constructor, quan hệ kế thừa/interface/override/annotation, không khởi tạo class khi phân tích, kiểm định/phát hành, điểm 88/100, cấu trúc bắt buộc và lưu SQLite. `OopStructureIntegrationTest --browser` kiểm tra thêm trình soạn rubric bằng Chrome; đặt STUDYFIX_BROWSER_TEST_MODULES đến node_modules chứa Playwright. `ant exam-organization-test` kiểm tra việc ẩn/hiện báo cáo theo quy chế.
