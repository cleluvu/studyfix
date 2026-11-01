// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

import { state } from "./state.js";
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
