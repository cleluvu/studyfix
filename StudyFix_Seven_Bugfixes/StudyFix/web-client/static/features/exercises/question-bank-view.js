import {esc} from "../../shared/format.js";
import {btn} from "../../ui/components.js";
import {templateIcon,templateHeader} from "../../ui/template.js";
import {unreadChatCount} from "../classroom/chat-notifications.js";

export const bankCalendar=templateIcon("calendar");
export const renderBankTopbar=(state,section="Luyện tập")=>templateHeader(state,section,state.identity?.role==="TEACHER"?"Ngân hàng câu hỏi":"Luyện code",unreadChatCount());
export function renderBankTabs(state){return state.identity?.role==="TEACHER"?`<nav class="bank-page-tabs" aria-label="Ngân hàng bài tập">${[["all","Luyện tập"],["private","Kì thi - Thực hành"],["create","Tạo bài tập"]].map(([view,label])=>`<button class="btn ${state.bankView===view?"btn-primary":"btn-soft"}" data-action="practice-view" data-view="${view}" ${state.bankView===view?'aria-current="page"':""}>${label}</button>`).join("")}</nav>`:"";}
export function renderBankFooter(p,state){
 const teacher=state.identity?.role==="TEACHER",selected=p.selected?.size||0;
 const chosen=[...(p.selected?.values()||[])],published=chosen.every(e=>e.publication!=="DRAFT");
 return `<div class="bank-list-footer">${teacher?`<button class="bank-select-summary" data-action="practice-select-page"><span class="bank-selection-mark">${templateIcon("check")}</span>Đã chọn ${selected} bài</button><div class="bank-footer-actions">${chosen.some(e=>e.oj)?btn("Duyệt phát hành OJ","practice-oj-publish","btn-soft",chosen.every(e=>e.oj)?"":"disabled"):""}${btn(`${templateIcon("exam")} Thêm vào đề thi`,"practice-bank-contest","btn-soft",selected&&published&&chosen.every(e=>e.mode==="BUILD_FROM_SCRATCH")?"":"disabled")}${state.bankView==="weeks"?btn(`${templateIcon("send")} Giao bài trong Week`,"practice-bank-assign","btn-primary",selected&&selected<=100&&published&&chosen.every(e=>e.mode==="BUILD_FROM_SCRATCH")?"":'disabled title="Chọn các bài đã phát hành trong Week"'):""}</div>`:'<span>Chọn một bài tập để bắt đầu luyện tập.</span>'}</div>`;
}
export function renderQuestionBank(p,state,view){
 const teacher=state.identity?.role==="TEACHER",privateBank=state.bankView==="private",title=privateBank?"Kì thi - Thực hành":"Luyện tập";
 const stats=teacher?[["Tổng số bài",p.stats.total,"all","layers"],["Đã xuất bản",p.stats.published,"accepted","check"],["Bản nháp",p.stats.drafts,"failed","file"],["Đã sử dụng trong đề thi",p.stats.used,"fresh","layers"]]:[["Tổng số bài",p.stats.total,"all","layers"],["Đã AC",p.stats.accepted,"accepted","check"],["Chưa đạt",p.stats.failed,"failed","file"],["Chưa làm",p.stats.fresh,"fresh","layers"]];
 return `<div class="question-bank ${teacher?"is-teacher":"is-student"}">${renderBankTopbar(state,title)}<div class="bank-page">${renderBankTabs(state)}
  <div class="bank-page-heading"><span class="bank-heading-icon">${bankCalendar}</span><div><h2>${teacher?title:"Luyện tập lập trình"}</h2><p>${teacher?(privateBank?"Bài tập riêng tư dành cho kì thi và thực hành · Không hiển thị với sinh viên":"Quản lý bài tập luyện tập lập trình Java"):"Chọn bài, luyện tập và theo dõi kết quả của bạn"}</p></div></div>${view.warning}
  <section class="bank-summary-cards" aria-label="Thống kê ngân hàng">${stats.map(([name,value,tone,icon])=>`<div class="bank-summary-card ${tone}"><span class="bank-summary-icon">${templateIcon(icon)}</span><div><span>${name}</span><strong>${Number(value)||0}</strong></div></div>`).join("")}</section>
  <section class="bank-main-panel" aria-label="Danh sách câu hỏi"><div class="bank-toolbar">${view.filters}${teacher?btn("import file","practice-oj-import","btn-soft"):""}</div>
   <div class="bank-results-caption"><span id="practice-catalog-count">${p.total} câu hỏi</span>${p.topic?`<span>Week: ${esc(p.topic)}</span>`:""}</div>
   <div class="table-wrap"><table class="data-table bank-all-table"><thead><tr>${teacher?`<th class="bank-check-cell"><input type="checkbox" id="bank-select-page" data-action="practice-select-page" aria-label="Chọn tất cả câu hỏi trang này" ${p.items.length&&p.items.every(e=>p.selected.has(e.id))?"checked":""}/></th>`:""}<th>Mã bài</th><th>Tên bài tập</th><th>Week / Dạng bài</th><th>Độ khó</th>${teacher?"<th>Số lần dùng</th>":"<th>Kết quả</th>"}<th>Thao tác</th></tr></thead><tbody id="practice-catalog-rows">${view.rows}</tbody></table></div>
   ${renderBankFooter(p,state)}<div id="practice-catalog-pagination" class="bank-pagination">${view.pagination}</div>
  </section>${view.assignment||""}
 </div></div>`;
}
