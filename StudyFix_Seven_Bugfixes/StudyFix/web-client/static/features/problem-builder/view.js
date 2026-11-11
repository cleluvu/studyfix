import {renderStatementStep} from "./statement-step.js";
import {renderTemplateStep} from "./template-step.js";
import {state} from "../../core/state.js";
import {b,steps} from "./state.js";
import {esc,dateTime} from "../../shared/format.js";
import {templateIcon} from "../../ui/template.js";
import {renderGeneralStep} from "./general-step.js";
import {renderTestStep} from "./test-step.js";
import {renderGradingStep} from "./grading-step.js";
import {renderValidationStep} from "./validation-step.js";
import {renderPublishStep} from "./publish-step.js";
export function renderBuilder(){
 if(state.identity?.role!=="TEACHER")return '<div class="notice-box warn">Chỉ giáo viên được sử dụng Problem Builder.</div>';
 const finished=["PUBLISHED","ARCHIVED"].includes(b.workflow?.state);
 const error=`<div id="builder-error" role="alert" class="notice-box warn" ${b.error?"":"hidden"}>${esc(b.error)}${b.conflict?'<button class="btn btn-soft" data-action="builder-reload">Mở bản trên server</button>':""}</div>`;
 const header=`<div class="pb-page"><div class="pb-heading"><span>${templateIcon("code")}</span><div><h2>Tạo bài tập Java</h2><p>Thiết kế đề bài</p></div><button class="btn btn-soft" data-action="builder-close">← Về danh sách bài tập</button></div>${error}`;
 if(!b.record)return `${header}<section class="pb-panel"><button class="btn btn-primary" data-action="builder-new" ${b.busy?"disabled":""}>${templateIcon("plus")} Tạo bài tập</button>${b.busy?'<p role="status">Đang tải…</p>':""}</section></div>`;
 return `${header}<nav class="pb-stepper" aria-label="Các bước tạo bài">${steps.map((label,i)=>`<button data-action="builder-step" data-step="${i+1}" class="${b.step===i+1?"active":i+1<b.step?"done":""}" ${i+1>b.step?"disabled":""}><b>${i+1<b.step?"✓":i+1}</b><span>${i+1}. ${label}</span></button>`).join("")}</nav><div class="pb-workspace" ${b.busy?"inert":""}>${b.step===2?renderStatementStep(b):b.step===3?renderTemplateStep(b):b.step===4?renderTestStep(b):b.step===5?renderGradingStep(b):b.step===6?renderValidationStep(b):b.step===7?renderPublishStep(b):renderGeneralStep(b,state.identity)}</div><div class="pb-summary"><strong>${templateIcon("layers")} Tóm tắt thông tin</strong><span>Cấu trúc: <b id="pb-summary-structure">${esc(b.payload.javaStructure)}</b></span><span>Độ khó: <b id="pb-summary-difficulty">${esc(b.payload.difficulty)}</b></span><span>Trạng thái: <b>${esc(({DRAFT:"Bản nháp",IN_REVIEW:"Đang duyệt",APPROVED:"Đã duyệt",PUBLISHED:"Đã phát hành",ARCHIVED:"Đã lưu trữ"})[b.workflow?.state]||"Bản nháp")}</b></span><span id="builder-save-status" role="status">${b.dirty?"Chưa lưu":`Đã lưu r${b.record.revision}`}</span></div><footer class="pb-footer"><div>${!finished&&b.step>1?`<button class="btn btn-soft" data-action="builder-step" data-step="${b.step-1}">Quay lại</button>`:""}${finished?`<button class="btn btn-primary" data-action="builder-finish">${templateIcon("check")} Hoàn tất · Về danh sách</button>`:`<button class="btn btn-soft" data-action="builder-save" ${b.busy?"disabled":""}>${templateIcon("file")} Lưu</button><button class="btn btn-primary" data-action="builder-next" ${b.step<7&&b.capabilities?.implementedSteps?.includes(b.step+1)?"":"disabled"}>${templateIcon("chevron")} Tiếp theo</button>`}</div></footer></div>`;
}
