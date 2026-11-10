# Nhập gói OJ ZIP — số lượng bài linh hoạt

Nút **Ngân hàng câu hỏi → Tất cả bài tập → Nhập gói OJ ZIP** không còn bắt buộc đúng 100 bài, 12 Week hoặc mã JAVA001–JAVA100. Số bài được đọc từ nội dung thực tế: có thể nhập một bài, vài bài hoặc nhiều hơn 100 bài. Gói rỗng báo không có bài để nhập.

## Hành vi khi nhập

1. Chọn ZIP đúng cấu trúc OJ.
2. Server kiểm tra file, manifest, code, cấu hình chấm, testcase và Week nếu có.
3. Giao diện hiển thị đúng số bài/Week/test, số Program/Method/Class; cấu trúc không có bài hiển thị 0.
4. Xem danh sách rồi nhấn **Xác nhận nhập bản nháp**.

Nhập từ giao diện là **nhập bổ sung**, không xóa các bài khác khỏi ngân hàng. Liên kết các bài khác trong Week được giữ. Cùng mã thuộc quyền quản lý của bạn tạo phiên bản mới khi gói thay đổi; bản đã giao/kỳ thi cũ không bị sửa. Nhập lại đúng cùng ZIP không tạo bài trùng. Gói cũ không được ghi đè phiên bản mới hơn. Không được sửa bài hoặc Week của giảng viên khác.

Bài mới ở trạng thái DRAFT, cần kiểm định và duyệt trước khi phát hành. Số lượng linh hoạt không có nghĩa gói tự động đạt kiểm định hoặc tự phát hành.

## Cấu trúc gói

Giữ định dạng gói OJ hiện có, ví dụ:

```text
my-package/
  manifest.json
  weeks.json                 # có thể bỏ nếu tất cả bài chưa gắn Week
  problems/
    SUM_01/
      problem.json
      ... các file được problem.json tham chiếu ...
```

Manifest có danh sách `problems`. Các trường `total_problems` và `week_count` không bắt buộc; nếu khai báo thì phải khớp dữ liệu thực. Ví dụ phần đầu manifest:

```json
{
  "total_problems": 1,
  "week_count": 0,
  "categories_enabled": false,
  "problems": [
    {"id": "SUM_01", "java_structure": "PROGRAM", "test_count": 2}
  ]
}
```

Đây chỉ là ví dụ manifest, không phải toàn bộ gói. Mỗi bài vẫn cần `problem.json`, đề bài, starter, reference, judge config, input/harness và expected đúng hợp đồng. `problem.json.id` trùng ID manifest và tên thư mục bài. Mã gồm chữ/số/`_`/`-`, tối đa 64 ký tự.

Không gắn Week: bỏ `weeks.json` và đặt `problem.json.week` bằng `0` hoặc bỏ trường này. Có Week: khai báo danh sách Week với `week_number` dương, không trùng, `problem_ids` chỉ tham chiếu bài có trong gói; `problem.json.week` phải khớp liên kết đó. Không bắt buộc Week liên tục từ 1 đến 12. Project hiện không dùng module Category.

Testcase và runner vẫn theo hợp đồng OJ đang hỗ trợ: Program/Method/Class, Java17, reference/template tối đa 32 KiB, test Public và Hidden, tổng điểm 100. File Excel hoặc ZIP chỉ chứa Excel chưa phải gói OJ hợp lệ.

## Giới hạn an toàn vẫn áp dụng

Đã bỏ mốc số lượng bài nghiệp vụ, **không bỏ giới hạn tài nguyên**:

- ZIP tải lên tối đa 16 MiB.
- Tối đa 4000 entry/file trong ZIP; mỗi file tối đa 1 MiB, tổng giải nén 20 MiB.
- Chặn đường dẫn vượt thư mục, symlink, file trùng và dữ liệu sai định dạng.
- Chỉ Teacher được nhập; preview có thời hạn và gắn với tài khoản.

Gói quá lớn cần chia thành nhiều ZIP; mỗi lần nhập đều bổ sung dữ liệu. Cơ chế sửa fixture JAVA092 cũ chỉ áp dụng đúng SHA-256 của gói 100 bài ban đầu, không tự thay đổi nội dung bài khác có cùng mã.

## Source và kiểm thử

- `server/src/studyfix/assignment/catalog/OjPackage.java`: bỏ số lượng/mã cố định, Week tùy chọn, đếm và kiểm tra số liệu thực.
- `server/src/studyfix/assignment/persistence/OjRepository.java`: nhập bổ sung, báo cáo số lượng thực, giữ thành viên Week, chuyển liên kết sang snapshot mới và bảo toàn lịch sử.
- `web-client/static/features/exercises/oj-import.js`: giao diện và số liệu động.
- `server/test/studyfix/OjImportIntegrationTest.java`: HTTP/SQLite thật với 1/7/100/150 bài, 0/1/12/21 Week, mã tự đặt, idempotence, quyền sở hữu và phiên bản.
- `web-client/tests/oj_import_browser.cjs`: Chrome nhập/xem trước/xác nhận gói một bài và gói 150 bài.

Không cần migration database mới. CLI `replace` vẫn là thao tác vận hành chủ động thay ngân hàng; không dùng thay cho nhập bổ sung trên giao diện.

Lệnh kiểm thử: `ant oj-import-test`; đặt `STUDYFIX_BUILDER_BROWSER_MODULES` tới node_modules có Playwright để bật Chrome. Test dùng database tạm, không tự nhập fixture vào dữ liệu đang dùng. Log kiểm thử lần thay đổi này: `server/build/oj-flexible-verified.log`.

Đã đạt kiểm thử gói 1/7/100/150 bài, 0/1/12/21 Week; Chrome nhập thực tế gói 1 và 150 bài. Hồi quy OJ chấm Docker, ngân hàng và Week đạt. Log cuối: `server/build/oj-flexible-final.log`; 20 kiểm tra frontend đạt tại `server/reports/OJ_FLEXIBLE_FRONTEND_TESTS.json`. [Ảnh xem trước gói một bài](previews/OJ_IMPORT/single-problem.png).

Server đã cập nhật qua launcher hiện có. Bản sao lưu trước triển khai: `server/backups/before-flexible-oj-20261109-060852/`. Báo cáo `server/reports/OJ_FLEXIBLE_VERIFICATION.json` xác nhận metadata ngân hàng đang dùng giữ nguyên sau khởi động; không tự nạp gói thử vào ngân hàng thật. Chạy Ctrl+Shift+R và đăng nhập lại để tải giao diện mới.
