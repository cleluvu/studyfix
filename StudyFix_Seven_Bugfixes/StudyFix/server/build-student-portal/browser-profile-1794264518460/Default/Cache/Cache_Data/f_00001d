import {startOrganizationFromExam} from "../exam-organization/index.js";
import {state} from '../../core/state.js';
import {assignment as rawAssignment} from '../../core/api.js';
import {render,addRenderHook} from '../../ui/render.js';
import {toast,fail} from '../../ui/toast.js';
import {modal,clearModal} from '../../ui/modal.js';
import {navigate,setExamBankHandlers} from '../../core/polling.js';
import {addNavigationGuard} from '../../core/navigation-guards.js';
import {esc} from '../../shared/format.js';
import {eb,blankExam,defaultGrading,sumPoints,questionFor} from './state.js';
import {previewMarkup,sourceQuestions} from './view.js';
export {renderExamBank} from './view.js';

let request=0,filterTimer=null,previousFocus=null,previousOverflow='';
async function assignment(type,data={}){const token=state.token;const result=await rawAssignment(type,data);if(token!==state.token)throw Error('Session changed; reopen the exam bank.');return result;}
const active=()=>state.page==='exam-bank'&&state.identity?.role==='TEACHER';
function lock(){eb.busy=true;document.querySelectorAll('.exam-bank input,.exam-bank select,.exam-bank textarea,.exam-bank button,[data-action="eb-schedule-confirm"]').forEach(e=>e.disabled=true);}
const storageKey=()=>`studyfix.examBank.${state.identity?.userId}.view`;
function remember(){if(!active())return;try{sessionStorage.setItem(storageKey(),JSON.stringify({view:eb.view,versionId:eb.exam.id||'',step:eb.step}));}catch{}}
function ownScope(){if(eb.owner===state.identity?.userId)return;closePreview();++request;clearTimeout(filterTimer);Object.assign(eb,{owner:state.identity?.userId||'',view:'list',step:1,list:[],stats:{},total:0,page:1,pages:1,query:'',status:'',sourceFilter:'',selected:new Set(),questions:[],weeks:[],exam:blankExam(),dirty:false,error:'',initialized:false,importPreview:null,source:'weeks',questionQuery:'',updateImport:false});}
async function loadDirectory(){const token=state.token,seq=++request,view=eb.view;const data=await assignment('EXAMBANK_LIST',{query:eb.query,filterStatus:eb.status,source:eb.sourceFilter,page:eb.page,allVersions:view==='versions'});if(token!==state.token||seq!==request||view!==eb.view)return false;Object.assign(eb,{list:data.exams,stats:data.stats,total:data.total,page:data.page,pages:data.totalPages});return true;}
async function loadSources(){const token=state.token,data=await assignment('EXAMBANK_SOURCES');if(token!==state.token)return;eb.questions=data.questions;eb.weeks=data.weeks;}
function acceptExam(exam){eb.exam={...exam,grading:{...defaultGrading(),...exam.grading}};eb.dirty=false;eb.error='';}
export async function loadExamBank(){ownScope();if(!active())return;try{eb.error='';if(!eb.initialized){await loadSources();let saved;try{saved=JSON.parse(sessionStorage.getItem(storageKey())||'null');}catch{}if(saved?.versionId&&['wizard','detail'].includes(saved.view)){try{acceptExam((await assignment('EXAMBANK_GET',{versionId:saved.versionId})).exam);eb.view=saved.view==='wizard'&&eb.exam.status==='DRAFT'?'wizard':'detail';eb.step=Math.min(5,Math.max(1,saved.step||1));}catch{eb.view='list';}}else if(['import','versions'].includes(saved?.view))eb.view=saved.view;eb.initialized=true;}if(['list','versions'].includes(eb.view))await loadDirectory();}catch(e){eb.error=e.message;}remember();}
function leaveDraft(){if(eb.dirty&&!confirm('Bản nháp có thay đổi chưa lưu. Rời màn hình và bỏ những thay đổi này?'))return false;eb.dirty=false;return true;}
async function showView(view){if(!leaveDraft())return;closePreview();eb.view=view;eb.error='';eb.page=1;if(['list','versions'].includes(view))await loadDirectory();remember();render();}
async function openVersion(id,tab='Tổng quan'){if(!leaveDraft())return;acceptExam((await assignment('EXAMBANK_GET',{versionId:id})).exam);eb.view='detail';eb.detailTab=tab;remember();render();}
function ensureMetadata(){if(!eb.exam.title.trim()||!eb.exam.code.trim())throw Error('Nhập mã đề và tên đề thi.');if(!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(eb.exam.code))throw Error('Mã đề chỉ gồm chữ, số, _ hoặc -.');if(!Number.isFinite(Number(eb.exam.totalPoints))||Number(eb.exam.totalPoints)<=0||Number(eb.exam.durationMinutes)<1)throw Error('Thời lượng và tổng điểm phải lớn hơn 0.');}
function cleanDoc(){const e=eb.exam;return {title:e.title,description:e.description,durationMinutes:Number(e.durationMinutes),totalPoints:Number(e.totalPoints),sourceWeeks:e.sourceWeeks,items:e.items.map(i=>({exerciseId:i.exerciseId,points:Number(i.points)})),grading:e.grading};}
async function saveDraft(){ensureMetadata();if(!eb.dirty&&eb.exam.id)return;const token=state.token,data=await assignment('EXAMBANK_SAVE',{versionId:eb.exam.id||'',expectedRevision:eb.exam.revision,code:eb.exam.code,document:cleanDoc()});if(token!==state.token)return;acceptExam(data.exam);remember();}
function addQuestions(ids){const chosen=new Set(eb.exam.items.map(i=>i.exerciseId));for(const id of ids){if(chosen.has(id))continue;const q=eb.questions.find(q=>q.id===id);if(!q)continue;if(eb.exam.items.length>=200)throw Error('Một bộ đề tối đa 200 câu.');eb.exam.items.push({exerciseId:id,points:20,question:q});chosen.add(id);}eb.dirty=true;}
function distribute(){const n=eb.exam.items.length;if(!n)throw Error('Chọn câu hỏi trước khi chia điểm.');const cents=Math.round(Number(eb.exam.totalPoints)*100),each=Math.floor(cents/n);if(each<1)throw Error('Tổng điểm quá nhỏ so với số câu.');eb.exam.items.forEach((q,i)=>q.points=(each+(i<cents%n?1:0))/100);eb.dirty=true;}
async function newDraft(ids=[]){if(!leaveDraft())return;await loadSources();eb.exam=blankExam();eb.view='wizard';eb.step=1;eb.source='weeks';eb.questionQuery='';eb.dirty=false;if(ids.length){addQuestions(ids);distribute();}remember();render();}
export async function createExamFromQuestions(ids){if(state.identity?.role!=='TEACHER')return;await navigate('exam-bank');if(!active())return;await newDraft(ids);}
async function exportFile(ids,format='JSON'){if(!ids.length)throw Error('Chọn ít nhất một phiên bản đề để xuất.');const data=await assignment('EXAMBANK_EXPORT',{versionIds:ids,format});const bytes=Uint8Array.from(atob(data.base64),c=>c.charCodeAt(0)),url=URL.createObjectURL(new Blob([bytes],{type:data.contentType}));const a=document.createElement('a');a.href=url;a.download=data.filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('Đã xuất gói đề thi.','success');}
function showPreview(){let host=document.querySelector('#exam-bank-preview');if(!host){previousFocus=document.activeElement;previousOverflow=document.body.style.overflow;host=document.createElement('div');host.id='exam-bank-preview';document.body.append(host);document.body.style.overflow='hidden';}host.innerHTML=previewMarkup();host.querySelector('button')?.focus();}
function closePreview(){const host=document.querySelector('#exam-bank-preview');if(host){host.remove();document.body.style.overflow=previousOverflow;previousFocus?.isConnected&&previousFocus.focus();}eb.preview=null;}
async function importFile(file){eb.importPreview=null;if(!file)return;if(!/\.(zip|json)$/i.test(file.name)||file.size>2*1024*1024)throw Error('Chọn file JSON hoặc ZIP tối đa 2 MiB.');const token=state.token,format=file.name.toLowerCase().endsWith('.zip')?'ZIP':'JSON';const response=await fetch(`/api/exam-bank/import?format=${format}&update=${eb.updateImport}`,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/octet-stream'},body:file});const data=await response.json();if(token!==state.token)return;if(!response.ok||data.ok===false)throw Error(data.message||'Không thể kiểm tra gói đề.');eb.importPreview=data.data;toast('Đã kiểm tra gói đề. Xem lại rồi xác nhận nhập.','success');}
function scheduleDialog(){if(!state.room)throw Error('Vào lớp do bạn quản lý trước khi tổ chức thi.');modal(`<div class="modal-head"><h3>Tổ chức kỳ thi từ bộ đề</h3><button class="btn btn-soft btn-sm" data-action="eb-schedule-close">Đóng</button></div><div class="card-body"><p>Phiên bản: <b>${esc(eb.exam.code)} · v${eb.exam.version}.0</b>. Giữ thứ tự, điểm và cấu hình của đề.</p><div class="form-row"><label>Tên kỳ thi</label><input id="eb-schedule-title" class="field" value="${esc(eb.exam.title.slice(0,120))}" maxlength="120"></div><div class="form-row"><label>Bắt đầu sau</label><select id="eb-schedule-start" class="select"><option value="0">Bắt đầu ngay</option><option value="5">5 phút</option><option value="15">15 phút</option><option value="60">1 giờ</option></select></div><div class="form-row"><label>Thời lượng (phút)</label><input id="eb-schedule-duration" class="field" type="number" min="1" max="43200" value="${eb.exam.durationMinutes}"></div><button class="btn btn-primary" data-action="eb-schedule-confirm">Tạo kỳ thi trong lớp hiện tại</button></div>`);}
export async function examBankClick(action,d){if(state.identity?.role!=='TEACHER'||eb.busy)return;ownScope();eb.error='';let needsRender=true;try{
 if(action==='eb-new'){await navigate('exam-bank');if(!active())return;await newDraft();return;}
 if(action==='eb-list'||action==='eb-versions'||action==='eb-io'){await navigate('exam-bank');if(!active())return;await showView(action==='eb-list'?'list':action==='eb-versions'?'versions':'import');return;}
 switch(action){
  case 'eb-reload':await loadExamBank();break;
  case 'eb-open':await openVersion(d.id);return;
  case 'eb-history':await openVersion(d.id,'Lịch sử phiên bản');return;
  case 'eb-step':eb.step=Number(d.step);break;
  case 'eb-next':if(eb.step===1)ensureMetadata();if(eb.step===2&&!eb.exam.items.length)throw Error('Chọn ít nhất một câu hỏi.');eb.step=Math.min(5,eb.step+1);break;
  case 'eb-previous':eb.step=Math.max(1,eb.step-1);break;
  case 'eb-generate-code':eb.exam.code='EXM_'+Date.now().toString(36).toUpperCase();eb.dirty=true;break;
  case 'eb-source-weeks':eb.source='weeks';eb.questionQuery='';break;
  case 'eb-source-all':eb.source='all';eb.questionQuery='';break;
  case 'eb-remove':eb.exam.items=eb.exam.items.filter(i=>i.exerciseId!==d.id);eb.dirty=true;break;
  case 'eb-move-up':case 'eb-move-down':{const i=eb.exam.items.findIndex(i=>i.exerciseId===d.id),j=i+(action==='eb-move-up'?-1:1);if(j>=0&&j<eb.exam.items.length)[eb.exam.items[i],eb.exam.items[j]]=[eb.exam.items[j],eb.exam.items[i]];eb.dirty=true;break;}
  case 'eb-distribute':distribute();break;
  case 'eb-save':lock();await saveDraft();toast('Đã lưu bản nháp vào SQLite.','success');break;
  case 'eb-finish':{ensureMetadata();if(!eb.exam.items.length||sumPoints()!==Number(eb.exam.totalPoints))throw Error('Cần có câu hỏi và tổng điểm phải khớp tổng điểm đề.');const approve=document.querySelector('#eb-approve')?.checked;lock();await saveDraft();if(approve)acceptExam((await assignment('EXAMBANK_APPROVE',{versionId:eb.exam.id,expectedRevision:eb.exam.revision})).exam);eb.view='detail';eb.detailTab='Tổng quan';toast(approve?'Đã duyệt phiên bản đề thi.':'Đã lưu bản nháp.','success');break;}
  case 'eb-edit':if(eb.exam.status!=='DRAFT')throw Error('Tạo phiên bản mới để sửa đề đã duyệt.');eb.view='wizard';eb.step=1;await loadSources();break;
  case 'eb-clone':lock();acceptExam((await assignment('EXAMBANK_CLONE',{versionId:eb.exam.id,expectedRevision:eb.exam.revision})).exam);eb.view='wizard';eb.step=1;await loadSources();toast('Đã tạo phiên bản nháp mới.','success');break;
  case 'eb-archive':if(!confirm('Lưu trữ phiên bản đề này? Kỳ thi đã tạo vẫn giữ nguyên.'))return;lock();acceptExam((await assignment('EXAMBANK_ARCHIVE',{versionId:eb.exam.id,expectedRevision:eb.exam.revision})).exam);toast('Đã lưu trữ đề.','success');break;
  case 'eb-detail-tab':eb.detailTab=d.tab;break;
  case 'eb-preview':lock();if(eb.view==='wizard')await saveDraft();eb.preview=(await assignment('EXAMBANK_PREVIEW',{versionId:eb.exam.id})).exam;eb.previewIndex=0;eb.busy=false;render();showPreview();needsRender=false;break;
  case 'eb-preview-question':eb.previewIndex=Number(d.index);showPreview();needsRender=false;break;
  case 'eb-preview-close':closePreview();needsRender=false;break;
  case 'eb-page':eb.page=Number(d.page);await loadDirectory();break;
  case 'eb-io-import':eb.io='import';break;
  case 'eb-io-export':eb.io='export';break;
  case 'eb-export-one':lock();await exportFile([d.id]);break;
  case 'eb-export-current':eb.selected.add(eb.exam.id);eb.view='import';eb.io='export';break;
  case 'eb-export-selected':lock();await exportFile([...eb.selected],d.format||'ZIP');break;
  case 'eb-import-commit':if(!eb.importPreview)throw Error('Chọn và kiểm tra file trước.');lock();const data=await assignment('EXAMBANK_IMPORT_COMMIT',{previewId:eb.importPreview.previewId});eb.importPreview=null;toast(`Đã nhập ${data.imported} đề dưới dạng bản nháp.`,'success');eb.view='list';await loadDirectory();break;
  case 'eb-schedule-close':clearModal();needsRender=false;break;
  case 'eb-schedule':await startOrganizationFromExam(eb.exam.id);needsRender=false;break;
  case 'eb-schedule-confirm':{const title=document.querySelector('#eb-schedule-title').value,durationMinutes=Number(document.querySelector('#eb-schedule-duration').value),startInMinutes=Number(document.querySelector('#eb-schedule-start').value);lock();await assignment('CONTEST_CREATE',{examVersionId:eb.exam.id,title,durationMinutes,startInMinutes});clearModal();eb.busy=false;await navigate('contests');toast('Đã tạo kỳ thi từ đúng phiên bản đề.','success');break;}
 }
 }catch(e){eb.error=e.message;fail(e);}finally{eb.busy=false;remember();if(needsRender&&active())render();else if(active()&&!eb.preview)render();}}
document.addEventListener('input',event=>{const e=event.target;if(!active())return;if(e.dataset.ebField){eb.exam[e.dataset.ebField]=e.type==='number'?Number(e.value):e.value;eb.dirty=true;return;}if(e.dataset.ebScore){eb.exam.items.find(i=>i.exerciseId===e.dataset.ebScore).points=Number(e.value);eb.dirty=true;document.querySelector('#eb-score-total').textContent=sumPoints();document.querySelector('#eb-score-warning').textContent=sumPoints()===Number(eb.exam.totalPoints)?'✓ Tổng điểm hợp lệ.':'Tổng điểm chưa khớp mục tiêu.';return;}if(e.id==='eb-question-search'){eb.questionQuery=e.value;document.querySelector('#eb-source-questions').innerHTML=sourceQuestions();return;}if(e.id==='eb-search'){eb.query=e.value;clearTimeout(filterTimer);const owner=eb.owner;filterTimer=setTimeout(async()=>{if(!active()||owner!==state.identity?.userId)return;try{eb.page=1;if(await loadDirectory())render();}catch(error){eb.error=error.message;render();}},200);}});
document.addEventListener('change',async event=>{const e=event.target;if(!active()||eb.busy)return;try{
 if(e.dataset.ebConfig){eb.exam.grading[e.dataset.ebConfig]=e.type==='checkbox'?e.checked:['timeLimitMs','memoryLimitMb','outputLimitKb'].includes(e.dataset.ebConfig)?Number(e.value):e.value;eb.dirty=true;if(e.dataset.ebConfig==='inherit')render();return;}
 if(e.dataset.ebSelect){e.checked?eb.selected.add(e.dataset.ebSelect):eb.selected.delete(e.dataset.ebSelect);document.querySelector('#eb-selection-summary').textContent=`Đã chọn ${eb.selected.size} phiên bản · ${eb.total} kết quả`;const all=document.querySelector('#eb-select-all');if(all)all.checked=eb.list.every(r=>eb.selected.has(r.id));document.querySelector('[data-action="eb-export-selected"]').disabled=eb.selected.size===0;return;}
 if(e.id==='eb-select-all'){for(const row of eb.list)e.checked?eb.selected.add(row.id):eb.selected.delete(row.id);render();return;}
 if(e.dataset.ebQuestion){if(e.checked)addQuestions([e.dataset.ebQuestion]);else{eb.exam.items=eb.exam.items.filter(i=>i.exerciseId!==e.dataset.ebQuestion);eb.dirty=true;}render();return;}
 if(e.dataset.ebWeek){const w=eb.weeks.find(w=>w.id===e.dataset.ebWeek),ids=w.exerciseIds.filter(id=>eb.questions.some(q=>q.id===id));if(e.checked){addQuestions(ids);if(!eb.exam.sourceWeeks.some(r=>r.id===w.id))eb.exam.sourceWeeks.push({id:w.id,title:`Week ${w.weekNo}: ${w.title}`});}else{eb.exam.items=eb.exam.items.filter(i=>!ids.includes(i.exerciseId));eb.exam.sourceWeeks=eb.exam.sourceWeeks.filter(s=>s.id!==w.id);eb.dirty=true;}render();return;}
 if(e.id==='eb-status-filter'||e.id==='eb-source-filter'){eb[e.id==='eb-status-filter'?'status':'sourceFilter']=e.value;eb.page=1;await loadDirectory();render();return;}
 if(e.id==='eb-import-update'){eb.updateImport=e.checked;eb.importPreview=null;render();return;}
 if(e.id==='eb-import-file'){lock();await importFile(e.files[0]);render();}
 }catch(error){eb.error=error.message;fail(error);render();}finally{if(e.id==='eb-import-file'){eb.busy=false;render();}}});
document.addEventListener('dragover',e=>{const z=e.target.closest?.('#eb-dropzone');if(z){e.preventDefault();z.classList.add('drag');}});
document.addEventListener('dragleave',e=>e.target.closest?.('#eb-dropzone')?.classList.remove('drag'));
document.addEventListener('drop',async e=>{if(!active()||!e.target.closest?.('#eb-dropzone'))return;e.preventDefault();if(eb.busy)return;lock();try{await importFile(e.dataTransfer.files[0]);}catch(error){eb.error=error.message;fail(error);}finally{eb.busy=false;render();}});
document.addEventListener('keydown',e=>{const dialog=document.querySelector('#exam-bank-preview .preview');if(!dialog)return;if(e.key==='Escape'){e.preventDefault();closePreview();}if(e.key==='Tab'){const buttons=[...dialog.querySelectorAll('button:not(:disabled)')],first=buttons[0],last=buttons.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}});
globalThis.addEventListener?.('beforeunload',e=>{if(active()&&eb.dirty){e.preventDefault();e.returnValue='';}});
addNavigationGuard(()=>{if(!active())return true;if(eb.busy)return false;if(!leaveDraft())return false;closePreview();return true;});
addRenderHook(()=>{if(!state.identity||state.identity.userId!==eb.owner){closePreview();clearTimeout(filterTimer);++request;}});
setExamBankHandlers({load:loadExamBank});
