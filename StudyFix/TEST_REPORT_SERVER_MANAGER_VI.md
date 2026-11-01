# Báo cáo kiểm thử StudyFix — bản cập nhật hiện tại

Báo cáo kiểm thử cũ này được thay bằng bảng tổng hợp được đối chiếu với source hiện tại tại [`docs/IMPLEMENTATION_STATUS_VI.md`](docs/IMPLEMENTATION_STATUS_VI.md). Các con số dưới đây là kết quả chạy trong môi trường đóng gói; kiểm thử Windows/JDK 26 và Docker Engine thật vẫn phải thực hiện tại máy đích.

- Ant `integration-test`: `BUILD SUCCESSFUL` với JDK 21 override, UTF-8 locale và Xvfb cho các test Swing.
- Gateway HTTP/HTTPS: 32 checks PASS.
- Assignment module/migration: 40 checks PASS.
- Assignment TCP protocol: 61 checks PASS.
- Edit-session/takeover/draft: 12/12 PASS.
- Recovery: 22 checks PASS.
- History: 78 checks PASS.
- Core TCP/reconnect/restart: 40 checks PASS.
- Screen relay TCP: 19 checks PASS.
- PersonOne: 57 checks PASS.
- Server Manager live: 3 checks PASS.
- Console interleave: 2 checks PASS.
- Console client: 6 checks PASS.
- Tổng báo cáo đếm: 372 checks.

Kiểm tra bổ sung: `node --check web-client/static/app.js` PASS; client source biên dịch bằng `javac --release 17` PASS. Không coi test media WebRTC qua browser thật, Docker grading trên engine thật, hoặc build release 26 trên Windows là đã nghiệm thu trong môi trường đóng gói này.
