// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.
// Lưu danh sách hàm hiển thị của từng trang.
//
// ui/render.js cần gọi hàm hiển thị của trang hiện tại, như renderRoom,
// renderExercises, renderContests, renderOverview,... Các module chức năng này
// cũng cần gọi render() trong ui/render.js sau khi
// đổi trạng thái. Import trực tiếp sẽ tạo vòng phụ thuộc, nên
// mỗi module đăng ký một lần bằng registerPage() trong app.js.
// Khi hiển thị, ui/render.js tra hàm đã đăng ký tại đây.

const pages = {};

export function registerPage(name, renderFn) {
  pages[name] = renderFn;
}

export function renderPage(name) {
  const renderFn = pages[name] || pages.overview;
  return renderFn ? renderFn() : "";
}
