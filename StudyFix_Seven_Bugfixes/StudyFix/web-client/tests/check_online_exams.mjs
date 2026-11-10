import assert from 'node:assert/strict';
globalThis.sessionStorage={getItem:()=>null};
globalThis.document={querySelector:()=>null,addEventListener(){}};
const {state}=await import('../static/core/state.js');
const {organization:o,newDocument,viewNames}=await import('../static/features/exam-organization/state.js');
const {renderOrganization}=await import('../static/features/exam-organization/view.js');
const {layout}=await import('../static/ui/render.js');
state.identity={userId:'teacher',role:'TEACHER',displayName:'Teacher'};state.page='exam-organization';
const exam={id:'exam',code:'ONLINE',title:'Kỳ thi Java',status:'SCHEDULED',access:'OWNER',startAt:Date.now()+3600000,endAt:Date.now()+7200000,classes:[{id:'class',code:'JAVA'}],candidateCount:1,totalPoints:100};
const student={userId:'student',studentCode:'SV001',displayName:'Nguyễn An',className:'JAVA',submittedQuestions:0,assignedQuestions:1,attempts:0};
o.sources={classes:[{id:'class',code:'JAVA',name:'Lớp Java'}],students:[{id:'student',name:'Nguyễn An',studentCode:'SV001',classId:'class',classCode:'JAVA'}],exams:[{id:'version',title:'Đề Java',version:1}]};
o.directory={exams:[exam],stats:{},page:1,totalPages:1,total:1};o.contestId='exam';
o.detail={exam,candidates:[student],submissions:[],audit:[],messages:[],publications:[],monitor:{students:[],recentEvents:[]}};
const retired=/Phòng thi|phòng máy|Cán bộ coi thi|Hình thức thi|Phân công &|data-view="assign"|eo-filter-format|eo-candidate-room|eo-result-room|eo-room-dialog|eo-seat-/;
let checks=0;
const verify=html=>{assert.doesNotMatch(html,retired);checks++;};
assert.deepEqual(Object.keys(newDocument()).filter(k=>['format','roomIds','proctors'].includes(k)),[]);
assert.ok(!Object.hasOwn(viewNames,'assign'));
for(const view of ['list','detail','monitor','results']){o.view=view;verify(renderOrganization());}
o.view='detail';let html=renderOrganization();assert.match(html,/eo-add-students/);assert.match(html,/id="eo-candidate-query"/);assert.match(html,/SV001/);
o.draft={id:'draft',revision:1,document:{...newDocument(),classIds:['class'],studentIds:['student'],examVersionId:'version'}};o.definition={title:'Đề Java',items:[{exerciseId:'Q1',points:100}],totalPoints:100};o.view='create';
for(let step=1;step<=5;step++){o.step=step;verify(renderOrganization());}
o.view='monitor';verify(layout(renderOrganization()));
state.identity={userId:'student',role:'STUDENT',displayName:'Nguyễn An'};o.view='detail';o.detail.exam={...exam,access:'STUDENT'};verify(renderOrganization());assert.doesNotMatch(renderOrganization(),/eo-add-students/);
console.log(`Online exam UI: ${checks} views/steps verified; roster management retained, retired controls absent.`);
