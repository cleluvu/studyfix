import {safeMarkdown} from "../problem-builder/markdown.js";
import {state} from "../../core/state.js";
import {assignment} from "../../core/api.js";
import {modal,clearModal} from "../../ui/modal.js";
import {esc} from "../../shared/format.js";
import {toast} from "../../ui/toast.js";
import {openCatalogCreate} from "./catalog-create.js";

export async function editBankQuestion(id,onChanged){const token=state.token,data=await assignment("BANK_GET",{exerciseId:id});if(state.token!==token)return;if(!data.exercise.canManage)throw new Error("Bạn không có quyền sửa đề này.");if(data.exercise.oj){const {editExerciseInBuilder}=await import("../problem-builder/controller.js");await editExerciseInBuilder(id,onChanged);clearModal();return;}openCatalogCreate(onChanged,data.exercise);}
export async function deleteBankQuestion(id,revision,onChanged){
 if(!confirm("Xóa đề khỏi ngân hàng? Bài giao, kỳ thi và bài nộp đã có vẫn được giữ nguyên."))return;
 const token=state.token;await assignment("BANK_DELETE",{exerciseId:id,expectedRevision:Number(revision)});if(token!==state.token)return;clearModal();await onChanged();toast("Đã xóa đề khỏi ngân hàng.","success");
}
export async function viewBankQuestion(id,onChanged){
 const token=state.token,data=await assignment("BANK_GET",{exerciseId:id});if(token!==state.token)return;const e=data.exercise;
 modal(`<div class="modal-head"><div><h3>${esc(e.displayCode||e.id)} · ${esc(e.title)}</h3><p>${esc(e.topic)} · ${esc(e.difficulty)} · ${e.publication==="DRAFT"?"Bản nháp":"Đã xuất bản"} · v${e.revision}</p></div><button class="btn btn-soft" id="bank-preview-close">Đóng</button></div><div class="modal-body bank-preview"><h3>Đề bài</h3><article class="pb-markdown">${safeMarkdown(e.statement)}</article><h4>Đầu vào</h4><p>${esc(e.inputFormat)}</p><h4>Đầu ra</h4><p>${esc(e.outputFormat)}</p><h4>Ràng buộc</h4><p>${esc(e.constraints)}</p>${(e.publicTests||[]).map((t,i)=>`<div class="test-row"><strong>Ví dụ ${i+1}</strong><div>Input<pre>${esc(t.input)}</pre></div><div>Output<pre>${esc(t.expectedOutput)}</pre></div></div>`).join("")}${e.mode==="DEBUG_COLLAB"?`<details><summary>Code lỗi ban đầu</summary><pre>${esc(e.starterCode)}</pre></details>`:""}${e.canManage?'<div class="bank-preview-actions"><button class="btn btn-soft" id="bank-preview-edit">Sửa đề</button><button class="btn btn-danger" id="bank-preview-delete">Xóa đề</button></div>':""}<p id="bank-preview-error" role="alert"></p></div>`);
 document.getElementById("bank-preview-close").onclick=clearModal;
 const run=fn=>async event=>{event.target.disabled=true;try{await fn();}catch(error){const el=document.getElementById("bank-preview-error");if(el)el.textContent=error.message;event.target.disabled=false;}};
 if(e.canManage){if(e.oj)document.getElementById("bank-preview-edit").title="Tạo phiên bản mới trong Problem Builder";document.getElementById("bank-preview-edit").onclick=run(()=>editBankQuestion(id,onChanged));document.getElementById("bank-preview-delete").onclick=run(()=>deleteBankQuestion(id,e.revision,onChanged));}
}
