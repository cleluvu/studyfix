// Xử lý chung việc mở và đóng hộp thoại, kể cả hộp thoại được thêm sau khi trang đã tải.
export function modal(content) {
  clearModal();
  const el = document.createElement("div");
  el.className = "modal-backdrop";
  el.id = "active-modal";
  el.setAttribute("role", "presentation");
  el.innerHTML = `<section class="modal" role="dialog" aria-modal="true" tabindex="-1">${content}</section>`;
  document.body.appendChild(el);
  el.querySelector('[role="dialog"]')?.focus({ preventScroll: true });
}

export function clearModal() {
  document.getElementById("active-modal")?.remove();
}
