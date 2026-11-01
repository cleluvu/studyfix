# StudyFix Web FE

## Chạy

Web FE được phục vụ trực tiếp từ Java `WebGatewayServer`, không cần dev server Node trong runtime. Mặc định mở `http://localhost:8080` sau khi chạy `server/run-server-manager.ps1`.

## Source hiện tại

- `static/index.html`: entry document.
- `static/app.css`: responsive layout/design tokens/components.
- `static/app.js`: SPA logic và client calls sang `/api/*`.

Bản source này chủ đích dùng HTML/CSS/JavaScript không dependency để frontend chạy offline ngay sau khi build server. Plan có đề xuất React + TypeScript + Vite; phần React/Vite chưa được đóng gói vì môi trường build hiện tại không có truy cập npm registry. Không khai báo rằng React bundle đã build. Nếu chuyển qua React sau này, giữ API contract và acceptance tests trong `../docs/API_CONTRACT.md`; không đổi domain/authorization sang logic chỉ ở UI.

## Các khu vực

- Tổng quan
- Phòng học: code cộng tác, đề DEBUG_COLLAB, shared check, quyền sửa, chat, thành viên, lịch sử, browser screen share signaling
- Bài tập: teacher bank/detail/giao bài/report/trial; student assigned work/submission/result/history
- Thi & xếp hạng: teacher create from catalog, student contests/workspace, leaderboard

## Screen share

Browser `getDisplayMedia` yêu cầu secure context. Trên localhost, trình duyệt thường cho phép HTTP; trên IP LAN nên dùng HTTPS với chứng chỉ được tin cậy hoặc TLS reverse proxy. Gateway có thể đọc `STUDYFIX_WEB_KEYSTORE` và `STUDYFIX_WEB_KEYSTORE_PASSWORD` để bật TLS trực tiếp.
