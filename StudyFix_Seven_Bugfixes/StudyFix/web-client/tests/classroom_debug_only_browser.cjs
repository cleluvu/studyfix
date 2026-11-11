// Real Chrome DOM interactions; classroom/catalog HTTP is mocked.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
 const root=path.resolve(__dirname,'../static'),out=path.resolve(__dirname,'../../server/build/classroom-debug-only');fs.mkdirSync(out,{recursive:true});
 const html=`<!doctype html><meta charset="utf-8"><link rel="stylesheet" href="/app.css"><link rel="stylesheet" href="/meeting.css"><div id="app"></div><div id="toast-region"></div><script type="module">
 import '/core/events.js';import {state} from '/core/state.js';import {registerPage} from '/core/router.js';import {render} from '/ui/render.js';import {renderRoom} from '/features/room/room.js';
 registerPage('room',renderRoom);window.fixture=(owner,phase,code='// buggy')=>{sessionStorage.clear();Object.assign(state,{booting:false,page:'room',token:'fixture',peerId:owner?'pt':'ps',identity:{userId:owner?'t':'s',role:owner?'TEACHER':'STUDENT'},room:{roomId:'1',roomCode:'JAVA',version:1,code,members:[],history:[]},classroomRoomId:'1',classroomDrafts:{},meetingPanel:'',meetingView:owner?'master':'branch',classroom:{isOwner:owner,phase,round:1,exercise:{id:'DBG',title:'Sửa lỗi tính tổng'},events:[],branches:owner?[]:[{userId:'s',username:'student',displayName:'Student',version:0,baseVersion:1,code,checks:[]}]}});render();};window.state=state;window.refresh=render;fixture(true,'LECTURE');
 </script>`;
 const server=http.createServer((req,res)=>{if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(html);}const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':'text/css; charset=utf-8');res.end(fs.readFileSync(file));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1440,height:960}}),errors=[],actions=[],assignmentTypes=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.route('**/api/assignments',async route=>{const b=route.request().postDataJSON();assignmentTypes.push(b.type);assert.equal(b.type,'CATALOG_LIST');await route.fulfill({json:{status:'OK',data:{exercises:[{id:'DBG',title:'Sửa lỗi tính tổng'}]}}});});
  await page.route('**/api/debug-library',async route=>route.fulfill({json:{items:[{id:'LIBRARY',title:'Bài lỗi đã soạn',status:'READY',version:1}]}}));
  await page.route('**/api/classroom',async route=>{
   const body=route.request().postDataJSON();actions.push(body.action);
   const c=await page.evaluate(body=>{const c=window.state.classroom,b=c.branches[0];if(body.action==='SELECT_LIBRARY')window.state.room.code='// selected buggy code';if(body.action==='BRANCH_SAVE'){b.code=body.code;b.version++;}if(body.action==='BRANCH_CHECK'){b.latestCheck={id:'check1',status:'DONE',result:{verdict:'ACCEPTED',feedback:'All tests passed',passed:2,total:2,score:100,maxScore:100,durationMs:10}};b.resultStale=false;}return c;},body);
   await route.fulfill({json:{classroom:c}});
  });
  await page.route('**/api/rooms/current',async route=>route.fulfill({json:{room:await page.evaluate(()=>window.state.room)}}));
  await page.goto('http://127.0.0.1:'+server.address().port);
  await page.locator('[data-action="classroom-panel"][data-panel="assignments"]').click();
  assert.equal(await page.locator('[data-action="room-assignments"],#assignment-form,[data-action="open-assignment"]').count(),0);
  await page.locator('.meeting-panel-actions [data-action="classroom-debug-pick"]').click();
  await page.locator('[data-action="classroom-debug-select"]').click();
  await page.waitForFunction(()=>document.getElementById('classroom-master')?.value==='// selected buggy code');
  assert.ok(actions.includes('SELECT_LIBRARY'));assert.ok(!actions.includes('ASSIGN_CREATE'));
  await page.screenshot({path:path.join(out,'teacher-debug-only.png')});
  await page.evaluate(()=>fixture(false,'BRANCHING'));
  await page.locator('#classroom-branch').fill('public class Main { /* fixed */ }');
  await page.waitForTimeout(1700);assert.ok(actions.includes('BRANCH_SAVE'));assert.ok(!actions.includes('BRANCH_CHECK'),'Autosave must not submit for grading');
  await page.getByRole('button',{name:'Nộp bài',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.meeting-terminal-head')?.textContent.includes('AC · Đúng'));
  assert.equal(actions.filter(a=>a==='BRANCH_CHECK').length,1);assert.equal(assignmentTypes.filter(a=>a==='ASSIGN_CREATE').length,0);
  await page.locator('[data-action="classroom-panel"][data-panel="assignments"]').click();
  assert.equal(await page.locator('.meeting-panel-actions [data-action="classroom-debug-pick"]').count(),0);
  assert.equal(await page.locator('[data-action="room-assignments"],#assignment-form,[data-action="open-assignment"]').count(),0);
  await page.screenshot({path:path.join(out,'student-submit-ac.png')});
  assert.deepEqual(errors,[]);console.log('Chrome PASS: teacher selects buggy code without assignments; student autosaves, explicitly submits and sees AC; no teacher picker or assignment UI for student. HTTP mocked.');
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1);});
