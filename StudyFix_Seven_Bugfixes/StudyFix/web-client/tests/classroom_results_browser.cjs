// Real Chrome with the same CSS bundle as index.html. HTTP simulates queued/running/completed grading.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
async function assertEditorLayout(page){
 await page.locator('.meeting-code-workspace:has(#classroom-branch) .meeting-editor-actions').scrollIntoViewIfNeeded();
 const boxes=await page.evaluate(()=>{
  const editor=document.getElementById('classroom-branch'),workspace=editor.closest('.meeting-code-workspace'),bar=workspace.querySelector('.meeting-editor-actions');
  const rect=el=>{const r=el.getBoundingClientRect();return {top:r.top,bottom:r.bottom,left:r.left,right:r.right,height:r.height};};
  return {content:rect(workspace.closest(".meeting-content")),workspace:rect(workspace),editor:rect(editor),bar:rect(bar),result:rect(document.getElementById('classroom-result')),buttons:[...bar.querySelectorAll('button')].map(el=>({action:el.dataset.action,...rect(el)})),overflow:workspace.scrollHeight-workspace.clientHeight};
 });
 assert.ok(boxes.result.top>=boxes.workspace.bottom-1,'grading result is below the complete editor card');
 assert.ok(boxes.editor.height>40,'the code retains editable space');
 for(const b of boxes.buttons){assert.ok(b.top>=boxes.workspace.top&&b.bottom<=boxes.workspace.bottom+1,`${b.action} is fully inside the editor card`);assert.ok(b.left>=boxes.workspace.left&&b.right<=boxes.workspace.right,`${b.action} is not horizontally clipped`);assert.ok(b.top>=boxes.content.top-1&&b.bottom<=boxes.content.bottom+1,`${b.action} is fully visible in the scrolling stage`);}
 assert.ok(boxes.overflow<=2,'file header, editor and actions fit without hidden overflow');
}
(async()=>{
 const root=path.resolve(__dirname,'../static'),out=path.resolve(__dirname,'../../server/build-debug-lessons/student-results');fs.mkdirSync(out,{recursive:true});
 const styles=[...fs.readFileSync(path.join(root,'index.html'),'utf8').matchAll(/<link rel="stylesheet" href="([^"]+)"\s*\/>/g)].map(m=>`<link rel="stylesheet" href="${m[1]}">`).join('');
 const html=`<!doctype html><html lang="vi"><meta charset="utf-8">${styles}<div id="app"></div><div id="toast-region"></div><script type="module">
 import '/core/events.js';import {state} from '/core/state.js';import {registerPage} from '/core/router.js';import {render} from '/ui/render.js';import {renderRoom} from '/features/room/room.js';
 registerPage('room',renderRoom);Object.assign(state,{booting:false,page:'room',token:'fixture',peerId:'ps',identity:{userId:'s',role:'STUDENT'},room:{roomId:'1',roomCode:'JAVA',version:1,code:'// buggy',members:[],history:[]},classroomRoomId:'1',classroomDrafts:{},meetingPanel:'',meetingView:'branch',classroom:{isOwner:false,phase:'BRANCHING',round:1,exercise:{id:'DBG',title:'Tính tổng hai số'},events:[],branches:[{userId:'s',username:'student',displayName:'Student',version:0,baseVersion:1,code:'// buggy',checks:[]}]}});window.state=state;window.refresh=render;render();
 </script></html>`;
 const server=http.createServer((req,res)=>{if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(html);}const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':'text/css; charset=utf-8');res.end(fs.readFileSync(file));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1366,height:768}}),errors=[],actions=[];let outcome='WRONG_ANSWER',sequence=0,polls=0,remote;
  page.on('pageerror',e=>errors.push(e.message));
  const result=()=>({verdict:outcome,passed:outcome==='ACCEPTED'?2:0,total:2,score:outcome==='ACCEPTED'?100:0,maxScore:100,durationMs:35,feedback:outcome==='COMPILE_ERROR'?"Main.java:4: error: ';' expected":outcome==='ACCEPTED'?'Đạt toàn bộ testcase.':'Có testcase sai.',publicTests:outcome==='COMPILE_ERROR'?[]:[{index:1,hidden:false,passed:outcome==='ACCEPTED',input:'5 3',expectedOutput:'8',actualOutput:outcome==='ACCEPTED'?'8':'2',message:outcome==='ACCEPTED'?'Đạt':'Sai kết quả'},{index:2,hidden:true,passed:outcome==='ACCEPTED',input:'HIDDEN_INPUT_SENTINEL',expectedOutput:'HIDDEN_ANSWER_SENTINEL',actualOutput:'HIDDEN_ACTUAL_SENTINEL',message:'HIDDEN_MESSAGE_SENTINEL'}]});
  await page.route('**/api/classroom',async route=>{
   const req=route.request();remote ||= await page.evaluate(()=>structuredClone(window.state.classroom));const b=remote.branches[0];
   if(req.method()==='POST'){
    const body=req.postDataJSON();actions.push(body.action);
    if(body.action==='BRANCH_SAVE'){b.code=body.code;b.version++;b.resultStale=Boolean(b.latestCheck?.id);}
    if(body.action==='BRANCH_CHECK'){polls=0;b.latestCheck={id:'check'+(++sequence),branchVersion:b.version,status:'QUEUED',result:{}};b.resultStale=false;b.checks=[b.latestCheck];}
   }else if(b.latestCheck?.status==='QUEUED'||b.latestCheck?.status==='RUNNING'){
    polls++;b.latestCheck.status=polls<2?'RUNNING':'DONE';if(polls>=2)b.latestCheck.result=result();
   }
   await route.fulfill({json:{classroom:structuredClone(remote)}});
  });
  await page.route('**/api/classroom/checks/*',async route=>route.fulfill({json:{check:{...remote.branches[0].latestCheck,code:remote.branches[0].code}}}));
  await page.route('**/api/rooms/current',async route=>route.fulfill({json:{room:await page.evaluate(()=>window.state.room)}}));
  await page.goto('http://127.0.0.1:'+server.address().port);await page.waitForSelector('#classroom-result');
  assert.ok((await page.locator('#classroom-result').textContent()).includes('chưa chấm bài'));
  const initial=await page.locator('#classroom-result').boundingBox(),editor=await page.locator('#classroom-branch').boundingBox();assert.ok(initial.y>=editor.y+editor.height,'feedback appears below the code editor');await assertEditorLayout(page);
  await page.locator('#classroom-branch').fill('class Main { /* student attempt */ }');await page.waitForTimeout(1600);assert.equal(actions.filter(a=>a==='BRANCH_CHECK').length,0,'autosave does not grade');
  await page.getByRole('button',{name:'Nộp bài',exact:true}).click();await page.waitForFunction(()=>document.getElementById('classroom-result').textContent.includes('Đang chờ chấm'));assert.ok(await page.locator('[data-action="classroom-run-check"]').isDisabled());
  await page.locator('#classroom-branch').focus();await page.waitForFunction(()=>document.getElementById('classroom-result').textContent.includes('WA · Sai kết quả'));
  assert.equal(await page.evaluate(()=>document.activeElement.id),'classroom-branch','result polling preserves editor focus');
  assert.ok((await page.locator('#classroom-result').textContent()).includes('Kết quả mong đợi'));assert.ok((await page.locator('#classroom-result').textContent()).includes('Kết quả của bạn'));
  assert.ok(!(await page.locator('body').textContent()).includes('HIDDEN_'),'hidden testcase details never rendered');
  assert.ok((await page.locator('#toast-region').textContent()).includes('WA · Sai kết quả'),'completion toast is shown without global polling');
  await assertEditorLayout(page);await page.screenshot({path:path.join(out,'student-wa.png')});
  outcome='ACCEPTED';await page.locator('#classroom-branch').fill('class Main { /* fixed */ }');await page.waitForTimeout(1600);assert.ok((await page.locator('#classroom-result').textContent()).includes('Kết quả này thuộc bản đã nộp trước'));
  await page.getByRole('button',{name:'Nộp bài',exact:true}).click();await page.waitForFunction(()=>document.getElementById('classroom-result').textContent.includes('AC · Đúng'));assert.ok((await page.locator('#classroom-result').textContent()).includes('2/2 testcase'));
  await assertEditorLayout(page);await page.screenshot({path:path.join(out,'student-ac.png')});
  outcome='COMPILE_ERROR';await page.locator('#classroom-branch').fill('class Main { invalid }');await page.waitForTimeout(1600);await page.getByRole('button',{name:'Nộp bài',exact:true}).click();await page.waitForFunction(()=>document.getElementById('classroom-result').textContent.includes('CE · Lỗi biên dịch'));
  assert.ok((await page.locator('#classroom-result').textContent()).includes("';' expected"));
  await page.locator('[data-action="classroom-check-detail"]').first().click();await page.waitForSelector('#active-modal');assert.ok((await page.locator('#active-modal').textContent()).includes('CE · Lỗi biên dịch'));await page.locator('[data-action="close-modal"]').click();
  for(const [width,height] of [[1920,1080],[1366,768],[1024,600],[420,900],[640,420]]){await page.setViewportSize({width,height});await assertEditorLayout(page);}
  await page.setViewportSize({width:420,height:900});await assertEditorLayout(page);await page.screenshot({path:path.join(out,'student-ce-mobile.png')});
  assert.deepEqual(errors,[]);console.log('Chrome PASS: full CSS at laptop/mobile sizes, visible feedback, submit receipt, independent QUEUED/RUNNING/WA/AC/CE updates, diagnostics, hidden-test privacy, stale result warning, detail dialog and preserved editor focus.');
 }finally{if(browser)await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1);});