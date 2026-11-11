import assert from 'node:assert/strict';
globalThis.sessionStorage={getItem:()=>null};
globalThis.document={title:'StudyFix',activeElement:null,querySelector:()=>null,querySelectorAll:()=>[],getElementById:()=>null,addEventListener(){}};
const {state}=await import('../static/core/state.js');
const {renderMeeting}=await import('../static/features/classroom/meeting-view.js');
let checks=0;
for(const owner of [true,false])for(const phase of ['WAITING','LECTURE','BRANCHING','MERGING','ENDED'])for(const panel of ['','chat','people','assignments','workspace','history']){
 state.identity={userId:owner?'t':'s',role:owner?'TEACHER':'STUDENT'};
 state.room={roomId:'1',version:1,code:'class Main {}',members:[],history:[]};
 state.meetingPanel=panel;state.meetingView='master';state.callRoster=[];state.chatItems=[];
 // Even old room assignments must never appear in the simplified classroom.
 state.assignments=[{id:'old',exerciseTitle:'LEGACY_ASSIGNMENT_SENTINEL'}];
 const branch={userId:'s',version:1,baseVersion:1,code:'code',displayName:'Student',latestCheck:{id:'check',status:'DONE',result:{verdict:'ACCEPTED'}}};
 const args={c:{isOwner:owner,phase,round:1,branches:[branch],exercise:{id:'DBG',title:'Debug'}},md:{code:'class Main {}'},b:owner?null:branch,branchDraft:{code:'code',baseVersion:1},control:'',chat:'CHAT',members:'MEMBERS',history:'HISTORY',recovery:''};
 const html=renderMeeting(args);
 assert.ok(!html.includes('room-assignments')&&!html.includes('open-assignment')&&!html.includes('assignment-form')&&!html.includes('LEGACY_ASSIGNMENT_SENTINEL'));
 assert.ok(html.includes('data-panel="assignments"')&&html.includes('id="classroom-master"'));
 if(panel==='assignments')assert.equal(html.includes('data-action="classroom-debug-pick"'),owner&&phase==='LECTURE');
 if(!owner){assert.ok(html.includes('AC · Đúng'));assert.equal(/id="classroom-branch"[^>]*readonly/.test(html),phase!=='BRANCHING');assert.equal(html.includes('data-action="classroom-run-check"'),phase==='BRANCHING');}
 assert.ok(!html.includes('classroom-auto-check'));
 state.meetingView='assignments';assert.ok(!renderMeeting(args).includes('meeting-assignment-workspace'));
 checks++;
}
console.log('Classroom template: '+checks+' states PASS (teacher/student, all phases and panels; legacy assignment UI excluded).');
