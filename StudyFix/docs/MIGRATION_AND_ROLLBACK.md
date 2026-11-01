# StudyFix — Migration & rollback an toàn

## Phạm vi hiện tại

Bản phòng học theo nhánh bổ sung schema riêng trong **core database**: `classroom_schema` có các mốc 1 (phiên, nhánh, chấm, sự kiện) và 2 (chat). Các bảng mới là `classroom_sessions`, `classroom_branches`, `classroom_checks`, `classroom_events`, `classroom_chat`. DDL và mốc migration được tạo trong transaction của `Store`; không đổi dữ liệu users/rooms/revisions cũ. Các lượt chấm QUEUED/RUNNING còn dở được đánh dấu JUDGE_ERROR khi mở lại database. Nhánh và snapshot của vòng cũ được giữ lại. Lịch sử chat trước bản này vốn chỉ ở RAM không thể backfill.

Merge cập nhật Master/revision, phase và sự kiện trong **cùng transaction core database**. Learning database bên dưới không đổi schema do tính năng nhánh này. Xem [quy trình và kiểm thử](CLASSROOM_BRANCH_WORKFLOW_VI.md).

Learning database dùng schema version **6** (`p3_schema_meta.version`). Các migration hiện tại được thực thi tăng dần/kiểu bổ sung; bao gồm liên kết contest trực tiếp tới exercise catalog, test results riêng cho contest, teacher trial riêng, và snapshot người nhận bài. Core database (`studyfix.db`) không cùng schema với learning database (`studyfix-learning.db`).

Các kiểm thử migration chạy trên DB tạm. Không chạy migration trên database đang sử dụng của bạn khi tạo gói.

## Trước lần chạy đầu tiên bản cập nhật

1. Đóng Server Manager và mọi process `studyfix.*`; xác nhận không còn listener giữ database.
2. Sao chép **toàn bộ thư mục `server/data/`** sang thư mục backup có timestamp. Vì SQLite có thể đang ở WAL mode, hãy giữ `studyfix.db`, `studyfix-learning.db` và cả file `-wal`/`-shm` nếu tồn tại.
3. Giữ ZIP source gốc và ghi lại hashes nếu database có dữ liệu thật.
4. Đầu tiên chạy trên **bản sao database** trong staging: kiểm tra login, rooms, messages, code/history, assignment, submission, contest cũ và leaderboard.
5. Chỉ khi staging qua nghiệm thu mới đưa bản mới vào thư mục project đang dùng.

## Rollback

1. Dừng toàn bộ listener và ứng dụng.
2. Không mở database đã nâng schema bằng binary cũ.
3. Di chuyển bộ database sau migration ra thư mục riêng để giữ log/chứng cứ; khôi phục nguyên trạng toàn bộ backup `server/data/` (bao gồm WAL/SHM nếu được backup).
4. Khôi phục source/binary cũ tương ứng với database backup.
5. Chạy smoke test đăng nhập, vào phòng, chat, history và đọc contest/assignment cũ.

Không xóa DB hoặc sửa foreign key bằng tay. Nếu migration thất bại, giữ log và bản sao DB; không tiếp tục dùng một DB đang ở trạng thái không rõ.
