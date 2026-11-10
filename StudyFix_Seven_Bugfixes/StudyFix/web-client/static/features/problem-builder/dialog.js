import {b} from "./state.js";
import {state} from "../../core/state.js";
import {renderBuilder} from "./view.js";

/** The wizard lives above the question bank, not in the application router. */
export function renderBuilderDialog(){
 let overlay=document.getElementById("builder-dialog");
 const visible=b.open&&state.identity?.role==="TEACHER"&&state.identity.userId===b.owner&&state.token===b.token&&state.page==="exercises";
 if(!visible){b.open=false;overlay?.remove();document.body.classList.remove("builder-dialog-open");document.getElementById("app")?.removeAttribute("inert");return;}
 if(!overlay){overlay=document.createElement("div");overlay.id="builder-dialog";overlay.className="pb-dialog-backdrop";overlay.innerHTML='<section class="pb-dialog" role="dialog" aria-modal="true" aria-labelledby="builder-dialog-title" tabindex="-1"><header class="pb-dialog-header"><div><h2 id="builder-dialog-title">Tạo bài tập Java</h2><p>Ngân hàng câu hỏi · Tất cả bài tập</p></div><button class="btn btn-soft" data-action="builder-close" aria-label="Đóng trình tạo bài tập">Đóng ✕</button></header><div class="pb-dialog-content"></div></section>';document.body.append(overlay);overlay.querySelector(".pb-dialog").focus({preventScroll:true});}
 document.body.classList.add("builder-dialog-open");document.getElementById("app")?.setAttribute("inert","");
 overlay.querySelector('[data-action="builder-close"]').disabled=!!(b.busy||b.uploading);
 const content=overlay.querySelector(".pb-dialog-content"),markup=renderBuilder();
 if(content._builderMarkup===markup)return;
 const active=document.activeElement,selection=active?.selectionStart!=null?[active.selectionStart,active.selectionEnd]:null;
 const identity=active?.id?{id:active.id}:active?.dataset?.builderField?{field:active.dataset.builderField}:active?.dataset?.builderTest?{test:active.dataset.builderTest,idValue:active.dataset.id}:null;
 const top=content.scrollTop;content.innerHTML=markup;content._builderMarkup=markup;content.scrollTop=top;
 let replacement;if(identity?.id)replacement=document.getElementById(identity.id);else if(identity?.field)replacement=[...content.querySelectorAll("[data-builder-field]")].find(el=>el.dataset.builderField===identity.field);else if(identity?.test)replacement=[...content.querySelectorAll("[data-builder-test]")].find(el=>el.dataset.builderTest===identity.test&&el.dataset.id===identity.idValue);
 if(replacement){replacement.focus({preventScroll:true});if(selection&&replacement.setSelectionRange)try{replacement.setSelectionRange(...selection);}catch(_){}}
}
