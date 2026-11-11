import {newStructureRule} from './structure-rules.js';
import {renderBuilderDialog} from "./dialog.js";
import {configureBuilderWorkflow,workflowClick,refreshBuilderWorkflow,loadReviewInbox} from "./workflow-controller.js";
import {updateStatementPreview} from "./statement-step.js";
import {templateFiles,activeTemplateFile} from "./template-step.js";
import {mountBuilderEditor,disposeBuilderEditor,captureBuilderEditor} from "./editor.js";
import {uploadBuilderAsset,assetBlob,clearBuilderAssets,observeProblemAssets} from "./assets.js";
import {state} from "../../core/state.js";
import {b,defaults} from "./state.js";
import {builderApi} from "./api.js";
import {assignment} from "../../core/api.js";
import {render,addRenderHook,addBeforeRenderHook} from "../../ui/render.js";
import {modal,clearModal} from "../../ui/modal.js";
import {esc,dateTime} from "../../shared/format.js";

import {addNavigationGuard} from "../../core/navigation-guards.js";
import {cryptoRandomId} from "../../shared/crypto.js";

let timer=null,pending=null,generation=0;
const key=()=>`studyfix.builder.active.${state.identity?.userId}`;
const valid=()=>state.identity?.role==="TEACHER"&&state.identity.userId===b.owner&&state.token===b.token;
function remember(id){try{id?sessionStorage.setItem(key(),id):sessionStorage.removeItem(key());}catch(_){} }
function scope(){if(b.owner===state.identity?.userId&&b.token===state.token)return;clearTimeout(timer);clearBuilderAssets();generation++;pending=null;Object.assign(b,{owner:state.identity?.userId||"",token:state.token,record:null,payload:null,items:[],weeks:[],capabilities:null,step:1,busy:false,saving:false,dirty:false,error:"",conflict:false,lastSaved:0,open:false,onChanged:null,uploading:false,compile:null,workflow:null,reviewers:[],reviews:[],reviewing:null,selectedTests:new Set(),publishedExerciseId:null});}
function setRecord(record,compile=null){b.record=record;const base=defaults();b.payload={...base,...record.payload,template:{...base.template,...record.payload.template},grading:{...base.grading,...record.payload.grading}};b.payload.grading.structure={schemaVersion:1,enabled:false,weight:30,gate:false,rules:[],...b.payload.grading.structure};if(b.payload.grading.checker==="LEGACY_NORMALIZED")b.payload.grading.checker="TOKEN";b.workflow={};b.selectedTests=new Set();b.publishedExerciseId=null;b.step=record.currentStep;b.dirty=false;b.conflict=false;b.error="";b.compile=compile;b.activePath=null;b.lastSaved=record.updatedAt;remember(record.id);void refreshBuilderWorkflow().then(()=>{if(valid()&&b.record?.id===record.id&&!b.dirty)render();}).catch(e=>{if(valid()&&b.record?.id===record.id){b.error=e.message;status();}});}
function status(){if(!valid())return;const el=document.getElementById("builder-save-status");if(el)el.textContent=b.conflict?"Xung đột — cần mở lại":b.saving?"Đang lưu…":b.dirty?"Chưa lưu":`Đã lưu r${b.record?.revision||0}`;const err=document.getElementById("builder-error");if(err){err.innerHTML=esc(b.error)+(b.conflict?'<button class="btn btn-soft" data-action="builder-reload">Mở bản trên server</button>':"");err.hidden=!b.error;} }
export async function loadBuilder({resume=true}={}){scope();if(state.identity?.role!=="TEACHER")return;b.busy=true;try{
 const token=state.token,[cap,list,weeks]=await Promise.all([builderApi("CAPABILITIES"),builderApi("LIST"),assignment("PRACTICE_WEEK_LIST")]);if(token!==state.token)return;b.capabilities=cap;b.items=list.drafts||[];b.weeks=weeks.weeks||[];await loadReviewInbox();
 if(resume&&!b.record){let id;try{id=sessionStorage.getItem(key());}catch(_){}if(id)try{const result=await builderApi("GET",{draftId:id});if(token===state.token)setRecord(result.draft,result.compile);}catch(e){if(token===state.token){remember("");b.error=e.message;}}}
 }catch(e){if(valid())b.error=e.message;}finally{if(valid())b.busy=false;}}
export async function saveBuilder(){clearTimeout(timer);if(pending){await pending;if(b.dirty&&!b.conflict)return saveBuilder();return !b.dirty;}if(!valid()||!b.record||b.conflict)return false;if(!b.dirty)return true;
 const epoch=generation,id=b.record.id,snapshot=JSON.stringify(b.payload),step=b.step,expectedRevision=b.record.revision;b.saving=true;status();
 const task=(async()=>{try{const result=await builderApi("SAVE",{draftId:id,expectedRevision,currentStep:step,payload:JSON.parse(snapshot)});if(epoch!==generation||!valid()||b.record?.id!==id)return false;b.record=result.draft;b.lastSaved=result.draft.updatedAt;b.error="";b.dirty=JSON.stringify(b.payload)!==snapshot||b.step!==step;return !b.dirty;
 }catch(e){if(epoch===generation&&valid()){b.error=e.message;b.conflict=e.code==="CONFLICT";}return false;}finally{if(epoch===generation&&valid()){b.saving=false;status();}}})();pending=task;await task;if(pending===task)pending=null;if(valid()&&b.dirty&&!b.error)timer=setTimeout(()=>void saveBuilder(),900);return !b.dirty;}
function changed(){const banner=document.querySelector(".pb-validation-banner");if(banner){banner.classList.remove("ok");banner.classList.add("warn");const note=banner.querySelector("p");if(note)note.textContent="Nội dung đã đổi — cần kiểm định lại.";}const publish=document.querySelector("[data-action=builder-publish]");if(publish)publish.disabled=true;b.dirty=true;if(!b.conflict)b.error="";status();const compile=document.getElementById("builder-compile-current");if(compile&&b.compile)compile.textContent="Nội dung đã đổi — cần biên dịch lại.";clearTimeout(timer);if(!b.conflict)timer=setTimeout(()=>void saveBuilder(),900);}
function writableFile(){b.payload.template.files=templateFiles(b).map(file=>({...file}));return activeTemplateFile(b);}
function sourceChanged(value){if(!valid()||!b.open||b.step!==3)return;const file=writableFile();file.content=value;if(file.path==="Main.java")b.payload.template.source=value;changed();}
async function open(id){if(b.dirty&&!await saveBuilder())return;const epoch=generation,result=await builderApi("GET",{draftId:id});if(epoch!==generation||!valid())return;setRecord(result.draft,result.compile);render();}
async function newDraft(){if(b.dirty&&!await saveBuilder())return;const epoch=generation;const {code}=await builderApi("SUGGEST_CODE");if(epoch!==generation||!valid())return;const result=await builderApi("CREATE",{requestKey:cryptoRandomId(),payload:{...defaults(),code,bankScope:state.creationBankScope||"PRACTICE",visibility:state.creationBankScope==="EXAM_PRIVATE"?"PRIVATE":"PUBLIC"}});if(epoch!==generation||!valid())return;setRecord(result.draft,result.compile);render();}
export async function openBuilderPage(){scope();if(!valid())return;if(!b.open){if(b.dirty&&!await saveBuilder())return;clearBuilderAssets();Object.assign(b,{record:null,payload:null,workflow:null,step:1,dirty:false,error:"",conflict:false,compile:null});remember("");}b.open=true;await loadBuilder({resume:false});}
export async function openBuilderDialog(onChanged=null,baseExerciseId=""){
 scope();if(!valid()||b.busy||b.uploading)return;if(b.dirty&&!await saveBuilder())return;
 clearBuilderAssets();Object.assign(b,{record:null,payload:null,workflow:null,step:1,dirty:false,error:"",conflict:false,compile:null});
 b.onChanged=onChanged;state.page="exercises";state.exerciseMode="practice";state.bankView="create";b.open=true;b.busy=true;render();
 try{await loadBuilder({resume:false});if(!valid()||!b.open||b.error)return;b.busy=true;if(baseExerciseId){const result=await builderApi("CREATE",{requestKey:cryptoRandomId(),baseExerciseId});if(valid()&&b.open)setRecord(result.draft,result.compile);}else await newDraft();}
 catch(error){if(valid())b.error=error.message;}finally{b.busy=false;render();}
}
export async function editExerciseInBuilder(id,onChanged=null){return openBuilderDialog(onChanged,id);}
async function closeBuilderDialog(){
 if(b.uploading||b.busy){b.error="Chờ thao tác hiện tại hoàn tất trước khi đóng.";status();return;}
 if(b.dirty&&!await saveBuilder())return;
 const callback=b.onChanged,destination=b.payload?.bankScope==="EXAM_PRIVATE"?"private":"all";
 b.open=false;b.onChanged=null;disposeBuilderEditor();clearModal();
 if(["PUBLISHED","ARCHIVED"].includes(b.workflow?.state)){remember("");b.record=null;b.payload=null;b.workflow=null;b.publishedExerciseId=null;}
 const {practiceClick}=await import("../exercises/practice.js");await practiceClick("practice-view",{view:destination});
 window.scrollTo({top:0,behavior:"instant"});
 if(callback)try{await callback();}catch(error){console.warn("Question bank refresh failed",error);}
}
export async function builderClick(action,d){scope();if(!valid())return;if(action==="builder-close"||action==="builder-finish"){await closeBuilderDialog();return;}if(b.busy||!b.open)return;b.error="";
 try{
  if(action==="builder-structure-add"){b.payload.grading.structure??={schemaVersion:1,enabled:true,weight:30,gate:false,rules:[]};b.payload.grading.structure.rules.push(newStructureRule(b));changed();render();return;}
  if(action==="builder-structure-remove"){b.payload.grading.structure.rules=b.payload.grading.structure.rules.filter(r=>r.id!==d.id);changed();render();return;}
  if(await workflowClick(action,d))return;
  if(b.uploading)throw Error("Đang tải file, vui lòng chờ hoàn tất.");
  if(action==="builder-compile"){if(b.dirty&&!await saveBuilder())return;const id=b.record.id,epoch=generation;b.busy=true;render();try{const result=await builderApi("COMPILE",{draftId:id,expectedRevision:b.record.revision});if(epoch===generation&&valid()&&b.record?.id===id)b.compile=result.compile;}finally{b.busy=false;render();}return;}
  if(action==="builder-upload-zip"){document.getElementById("builder-template-zip").click();return;}
  if(action==="builder-file-select"){b.activePath=d.path;render();return;}
  if(action==="builder-file-add"){const path=prompt("Đường dẫn file mới, ví dụ src/Helper.java");if(!path)return;if(!/^[A-Za-z0-9_./-]+\.(java|md|txt|json|xml|properties)$/.test(path)||path.startsWith("/")||path.split("/").some(part=>!part||part===".."||part==="."))throw Error("Đường dẫn file không hợp lệ.");if(templateFiles(b).some(file=>file.path.toLowerCase()===path.toLowerCase()||file.path.toLowerCase().startsWith(path.toLowerCase()+"/")||path.toLowerCase().startsWith(file.path.toLowerCase()+"/")))throw Error("File đã tồn tại.");if(templateFiles(b).length>=32)throw Error("Tối đa 32 file.");b.payload.template.files=[...templateFiles(b),{path,content:"",visibility:"PUBLIC",editable:path.endsWith(".java")}];b.activePath=path;changed();render();return;}
  if(action==="builder-file-remove"){if(templateFiles(b).length<2)return;if(!confirm("Xóa file khỏi template nháp?"))return;b.payload.template.files=templateFiles(b).filter(file=>file.path!==activeTemplateFile(b).path);b.activePath=b.payload.template.files[0].path;changed();render();return;}
  if(action==="builder-upload-image"||action==="builder-upload-pdf"){const input=document.getElementById("builder-asset-file");input.accept=action.endsWith("image")?"image/png,image/jpeg":"application/pdf";input.value="";input.click();return;}
  if(action==="builder-download"){const url=await assetBlob(d.id),a=document.createElement("a");a.href=url;a.download=d.name||"attachment";a.click();return;}
  if(action==="builder-markdown"){const input=document.getElementById("builder-statement-md");const wrappers={heading:["## ",""],bold:["**","**"],italic:["*","*"],list:["- ",""],code:["\n```java\n","\n```\n"]};const [start,end]=wrappers[d.format]||["",""];const selected=input.value.slice(input.selectionStart,input.selectionEnd);input.setRangeText(start+selected+end,input.selectionStart,input.selectionEnd,"end");input.dispatchEvent(new Event("input",{bubbles:true}));input.focus();return;}
  if(action==="builder-save"){await saveBuilder();return;}
  if(action==="builder-new"){b.busy=true;render();try{await newDraft();}finally{b.busy=false;render();}return;}
  if(action==="builder-open"){await open(d.id);return;}
  if(action==="builder-directory"){if(b.dirty&&!await saveBuilder())return;remember("");b.record=null;b.payload=null;await loadBuilder();render();return;}
  if(action==="builder-code"){const epoch=generation;const result=await builderApi("SUGGEST_CODE");if(epoch!==generation||!valid())return;b.payload.code=result.code;changed();render();return;}
  if(action==="builder-delete"){if(!confirm("Xóa bản nháp này? Câu hỏi đã phát hành và bài đã giao vẫn được giữ nguyên."))return;await builderApi("DELETE_DRAFT",{draftId:d.id,expectedRevision:Number(d.revision)});if(b.record?.id===d.id){b.record=null;remember("");}await loadBuilder();render();return;}
  if(action==="builder-history"){
   if(b.dirty&&!await saveBuilder())return;const id=b.record.id,{history}=await builderApi("HISTORY",{draftId:id});if(!valid()||b.record?.id!==id)return;
   modal(`<div class="modal-head"><h3>Lịch sử lưu nháp</h3><button class="btn btn-soft" data-action="close-modal">Đóng</button></div><div class="modal-body">${history.map(row=>`<div class="pb-draft-row"><span>r${row.revision} · Bước ${row.currentStep} · ${dateTime(row.createdAt)}</span><button class="btn btn-soft" data-action="builder-restore" data-revision="${row.revision}">Khôi phục</button></div>`).join("")}</div>`);return;
  }
  if(action==="builder-restore"){if(!confirm("Khôi phục tạo revision mới từ bản đã chọn. Tiếp tục?"))return;const result=await builderApi("RESTORE",{draftId:b.record.id,expectedRevision:b.record.revision,sourceRevision:Number(d.revision)});if(!valid())return;setRecord(result.draft,result.compile);clearModal();render();return;}
  if(action==="builder-reload"){if(b.dirty&&!confirm("Mở bản trên server sẽ thay nội dung chưa lưu ở cửa sổ này. Tiếp tục?"))return;const id=b.record.id;b.dirty=false;b.conflict=false;await open(id);return;}
  if(action==="builder-step"||action==="builder-next"){
   const target=action==="builder-next"?b.step+1:Number(d.step);if(target<1||target>7)return;if(b.dirty&&!await saveBuilder())return;const epoch=generation;b.busy=true;render();try{const result=await builderApi("ADVANCE",{draftId:b.record.id,expectedRevision:b.record.revision,targetStep:target});if(epoch!==generation||!valid())return;setRecord(result.draft,result.compile);document.querySelector(".pb-dialog-content")?.scrollTo(0,0);}finally{b.busy=false;render();}

  }
 }catch(e){if(valid()){b.error=e.message;status();render();}}
}
document.addEventListener("click",event=>{const el=event.target.closest?.("[data-action^='builder-']");if(!el)return;event.preventDefault();event.stopImmediatePropagation();void builderClick(el.dataset.action,el.dataset);},true);
document.addEventListener("input",event=>{
 const el=event.target;if(!valid()||!b.open||!b.payload)return;
 if(el.dataset.builderSource){sourceChanged(el.value);return;}
 if(el.hasAttribute("data-builder-file-visibility")){const file=writableFile();file.visibility=el.value;if(el.value!=="PUBLIC")file.editable=false;changed();render();return;}
 if(el.hasAttribute("data-builder-file-editable")){writableFile().editable=el.checked;changed();return;}
 if(el.dataset.structureRule){const r=b.payload.grading.structure.rules.find(r=>r.id===el.dataset.ruleId);if(!r)return;const k=el.dataset.structureRule;r[k]=el.type==="checkbox"?el.checked:k==="weight"?Number(el.value):["parameters","modifiers","forbiddenModifiers"].includes(k)?el.value.split(";").map(s=>s.trim()).filter(Boolean):el.value;if(k==="kind"){r.type=el.value==="TYPE"?"CLASS":"";r.visibility="";r.name="";r.parameters=[];r.target="";}changed();if(k==="kind")render();return;}
 if(el.dataset.action==="builder-structure-toggle"){b.payload.grading.structure??={schemaVersion:1,weight:30,gate:false,rules:[]};b.payload.grading.structure.enabled=el.checked;if(el.checked&&!b.payload.grading.structure.rules.length)b.payload.grading.structure.rules.push(newStructureRule(b));changed();render();return;}
 if(el.dataset.builderField){const path=el.dataset.builderField.split(".");let data=b.payload;while(path.length>1){const part=path.shift();data=data[part]??={};}data[path[0]]=el.type==="checkbox"?el.checked:el.type==="number"?(el.value===""?null:Number(el.value)):el.value;if(el.dataset.builderField==="bankScope")b.payload.visibility=el.value==="EXAM_PRIVATE"?"PRIVATE":"PUBLIC";changed();if(b.step===2)updateStatementPreview(b);if(el.type==="radio"){el.closest("fieldset")?.querySelectorAll("label").forEach(label=>label.classList.toggle("active",!!label.querySelector("input:checked")));}for(const [id,value] of [["pb-summary-structure",b.payload.javaStructure],["pb-summary-difficulty",b.payload.difficulty]]){const node=document.getElementById(id);if(node)node.textContent=value;}}
 if(el.dataset.builderField==="javaStructure"&&!b.payload.template.files&&/class Main\b/.test(b.payload.template.source||"")){const name=el.value==="PROGRAM"?"Main":el.value==="METHOD"?"Solution":"Student";b.payload.template={mainClass:name,entryFile:name+".java",source:el.value==="PROGRAM"?defaults().template.source:`public class ${name} {\n    // TODO: implement API described in the problem\n}\n`};}
 if(el.dataset.builderWeek){const ids=new Set(b.payload.weekIds||[]);el.checked?ids.add(el.dataset.builderWeek):ids.delete(el.dataset.builderWeek);b.payload.weekIds=[...ids];changed();}
});
window.addEventListener("beforeunload",event=>{if(valid()&&b.open&&b.dirty){event.preventDefault();event.returnValue="";}});
document.addEventListener("keydown",event=>{if(!valid()||!b.open||document.getElementById("active-modal"))return;if(event.key==="Escape"){event.preventDefault();event.stopImmediatePropagation();void closeBuilderDialog();return;}if(event.key!=="Tab")return;const dialog=document.querySelector("#builder-dialog .pb-dialog"),nodes=[...dialog?.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]')||[]].filter(el=>el.getClientRects().length&&!el.closest("[inert]"));if(!nodes.length)return;const first=nodes[0],last=nodes.at(-1);if(event.shiftKey&&(document.activeElement===first||document.activeElement===dialog)){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}},true);
addNavigationGuard(async()=>{if(!valid()||!b.open||!b.dirty)return true;if(!confirm("Lưu bản nháp trước khi rời trang? Chọn Hủy để ở lại."))return false;return saveBuilder();});
observeProblemAssets();
configureBuilderWorkflow({valid,saveBuilder,changed,setRecord});

addBeforeRenderHook(captureBuilderEditor);
addRenderHook(()=>{scope();renderBuilderDialog();});
addRenderHook(()=>{if(valid()&&b.open&&["PUBLISHED","ARCHIVED"].includes(b.workflow?.state)){document.querySelectorAll('.pb-workspace input,.pb-workspace textarea,.pb-workspace select,.pb-workspace [data-action^="builder-test-"],.pb-workspace [data-action="builder-expected"],.pb-workspace [data-action="builder-validate"],.pb-workspace [data-action="builder-file-add"],.pb-workspace [data-action="builder-file-remove"],.pb-workspace [data-action="builder-upload-zip"]').forEach(el=>el.disabled=true);}});
addRenderHook(()=>{scope();if(valid()&&b.open&&b.payload){if(b.step===2)updateStatementPreview(b);if(b.step===3){void mountBuilderEditor(b.record.id,activeTemplateFile(b),sourceChanged,["PUBLISHED","ARCHIVED"].includes(b.workflow?.state));return;}}disposeBuilderEditor();});

document.addEventListener("change",async event=>{if(event.target.id!=="builder-template-zip"||!valid()||!b.record||b.uploading)return;const file=event.target.files?.[0];if(!file)return;const id=b.record.id,epoch=generation;b.uploading=true;try{if(b.dirty&&!await saveBuilder())return;const asset=await uploadBuilderAsset(id,file);if(epoch!==generation||!valid()||b.record?.id!==id)return;const result=await builderApi("TEMPLATE_IMPORT",{draftId:id,expectedRevision:b.record.revision,assetId:asset.id});if(epoch!==generation||!valid()||b.record?.id!==id)return;setRecord(result.draft,result.compile);b.activePath=null;render();}catch(error){if(valid()){b.error=error.message;status();}}finally{if(epoch===generation)b.uploading=false;}});

document.addEventListener("change",async event=>{if(event.target.id!=="builder-asset-file"||!valid()||!b.record||b.uploading)return;const file=event.target.files?.[0];if(!file)return;const id=b.record.id,epoch=generation;b.uploading=true;try{const asset=await uploadBuilderAsset(id,file);if(epoch!==generation||!valid()||b.record?.id!==id)return;if(asset.mediaType.startsWith("image/")){b.payload.statement.description=(b.payload.statement.description||"")+`\n![${asset.name.replace(/[\[\]]/g,"")}](/api/problem-builder/assets/${asset.id})\n`;const input=document.getElementById("builder-statement-md");if(input)input.value=b.payload.statement.description;}else{b.payload.statement.attachments=[...(b.payload.statement.attachments||[]),asset];}changed();updateStatementPreview(b);await saveBuilder();render();}catch(error){if(valid()){b.error=error.message;status();}}finally{if(epoch===generation)b.uploading=false;}});
