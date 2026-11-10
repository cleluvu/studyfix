// Tách từ app.js để dễ quản lý. Xem cách chia file trong README.md ở thư mục StudyFix, mục 6.3.

import { state, restoreView } from "./state.js";
import { logout, refreshMe } from "../features/auth/auth.js";
import { loadCurrentRoom, loadPageData, startPolling } from "./polling.js";
import { render } from "../ui/render.js";

export async function init() {
  if (!state.token) {
    state.booting = false;
    render();
    return;
  }
  try {
    await refreshMe();
    await loadCurrentRoom(false);
    restoreView();
    state.meetingClosed=state.page!=="room";
    state.booting = false;
    startPolling();
    await loadPageData();
    render();
  } catch (e) {
    state.booting = false;
    await logout(false);
    render();
  }
}
