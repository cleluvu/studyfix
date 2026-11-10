import {esc,initial,roleName} from "../shared/format.js";

const paths={
 history:'<path d="M3 10a9 9 0 1 1 1 7M3 3v7h7m2-4v6l4 2"/>',
 download:'<path d="M12 2v13m-5-5 5 5 5-5M4 16v5h16v-5"/>',
 archive:'<path d="M3 3h18v5H3Zm2 5v13h14V8M9 12h6"/>',
 home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
 calendar:'<rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 3v5m8-5v5M4 11h16M8 15h2m4 0h2M8 18h2m4 0h2"/>',
 layers:'<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5"/>',
 check:'<circle cx="12" cy="12" r="9"/><path d="m7 12 3 3 7-7"/>',
 file:'<path d="M6 3h8l4 4v14H6V3Zm8 0v5h4M9 12h6M9 16h6"/>',
 plus:'<path d="M12 4v16M4 12h16"/>',
 search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
 folder:'<path d="M3 7V5h7l2 2h9v13H3V7Z"/>',
 eye:'<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
 trash:'<path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
 edit:'<path d="m15 4 5 5M4 20l5-1L21 7l-5-5L4 14v6Z"/>',
 send:'<path d="m22 2-7 20-4-9-9-4L22 2ZM11 13 22 2"/>',
 exam:'<path d="M8 4H5v17h14V4h-3M9 2h6v5H9V2ZM8 11h8m-8 4h5"/>',
 users:'<circle cx="9" cy="7" r="3"/><path d="M2 21v-4a7 7 0 0 1 14 0v4M17 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5v2"/>',
 network:'<circle cx="12" cy="5" r="3"/><circle cx="5" cy="19" r="3"/><circle cx="19" cy="19" r="3"/><path d="M12 8v5H5v3m7-3h7v3"/>',
 video:'<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-4v12l-5-4"/>',
 chart:'<path d="M3 3v18h19M7 17V9h3v8m4 0V5h3v12"/>',
 info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
 list:'<path d="M9 5h12M9 12h12M9 19h12M3 5h1m-1 7h1m-1 7h1"/>',
 settings:'<path d="m9 3-1 3-3 1-2 4 2 2v4l4 2 3-1 3 1 4-2v-4l2-2-2-4-3-1-1-3H9Z"/><circle cx="12" cy="11" r="3"/>',
 bell:'<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3ZM10 21h4"/>',
 chevron:'<path d="m6 9 6 6 6-6"/>',
 logout:'<path d="M10 3H4v18h6m4-15 6 6-6 6m-6-6h12"/>',
 code:'<path d="m7 7-5 5 5 5m10-10 5 5-5 5M14 3l-4 18"/>'
};
Object.assign(paths,{
 lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3"/>',
 download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
 play:'<circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4V8Z"/>',
 clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
 bolt:'<path d="m13 2-9 12h7l-1 8 10-13h-7l1-7Z"/>',
 arrow:'<path d="M4 12h16m-6-6 6 6-6 6"/>',
 graduate:'<path d="m2 9 10-5 10 5-10 5-10-5Zm4 3v6c4 3 8 3 12 0v-6M22 9v8"/>'
});
export const templateIcon=name=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]||paths.file}</svg>`;

/** Presentation only. The account menu uses the existing logout action. */
export function templateHeader(state,section,parent="",unread=0){
 if(state.identity?.role==="TEACHER")unread=state.teacherDashboardUnread||0;
 return `<header class="template-topbar bank-topbar"><nav class="bank-breadcrumb" aria-label="Đường dẫn"><button data-action="navigate" data-page="${state.identity?.role==="TEACHER"?"admin":"overview"}" aria-label="Trang chủ">${templateIcon("home")}</button><span>/</span>${parent?`<span>${esc(parent)}</span><span>/</span>`:""}<strong>${esc(section)}</strong></nav><div class="template-header-tools"><button type="button" class="template-notifications" ${state.identity?.role==="TEACHER"?'data-action="td-notifications"':'data-action="chat-open"'} aria-label="${unread?`${unread} ${state.identity?.role==="TEACHER"?"thông báo":"tin nhắn"} chưa đọc`:"Thông báo"}">${templateIcon("bell")}${unread?'<i></i>':""}</button><details class="template-account"><summary><span class="avatar">${esc(initial(state.identity?.displayName||state.identity?.username||""))}</span><span>${roleName(state.identity?.role)}</span>${templateIcon("chevron")}</summary><div class="template-account-menu"><strong>${esc(state.identity?.displayName||state.identity?.username||"")}</strong><small>${esc(roleName(state.identity?.role))}</small>${state.identity?.role==="TEACHER"?'<button data-action="navigate" data-page="admin-settings">Cài đặt cá nhân</button>':""}<button data-action="logout">${templateIcon("logout")} Đăng xuất</button></div></details></div></header>`;
}
