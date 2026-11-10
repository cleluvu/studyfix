const groups = new Set(["exercises", "exam-bank", "exam-organization"]);
let currentPage;
let openGroup = null;

export function syncSidebar(page) {
  if (page !== currentPage) {
    currentPage = page;
    openGroup = groups.has(page) ? page : null;
  }
}

export function toggleSidebarGroup(group) {
  if (groups.has(group)) openGroup = openGroup === group ? null : group;
}

export function sidebarToggle(group) {
  return `data-action="toggle-sidebar-group" data-group="${group}" aria-expanded="${openGroup === group}" aria-controls="sidebar-${group}"`;
}

export function sidebarPanel(group) {
  return `id="sidebar-${group}"${openGroup === group ? "" : " hidden"}`;
}
