// Real Chrome countdown and expiry controls, using server-shaped HTTP fixtures.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
 const root=path.resolve(__dirname,'../static'),out=path.resolve(__dirname,'../../server/build-debug-lessons/exam-timer');fs.mkdirSync(out,{recursive:true});
 const styles=[...fs.readFileSync(path.join(root,'index.html'),'utf8').matchAll(/<link rel="stylesheet" href="([^"]+)"\s*\/>/g)].map(m=>`<link rel="stylesheet" href="${m[1]}">`).join('');
 const html=`<!doctype html><html lang="vi"><meta charset="utf-8">${styles}<div id="app"></div><div id="toast-region"></div><script type="module">
 import '/core/events.js';import {state} from '/core/state.js';import {registerPage} from '/core/router.js';import {render} from '/ui/render.js';import {renderOrganization,organization as o} from '/features/exam-organization/index.js';
 const now=Date.now(),end=now+5000;const exam={id:'TIMER',title:'Kỳ thi nhiều ngày',code:'TIMER',subject:'Java',status:'RUNNING',state:'PUBLISHED',access:'STUDENT',startAt:now-60000,endAt:end,windowEndAt:now+86400000,durationMinutes:60,attemptStartedAt:now-3595000,attemptEndAt:end,policy:{},classes:[]};
 const candidate={userId:'student',displayName:'Học sinh',className:'Java',startedAt:exam.attemptStartedAt,finishedAt:0};
 Object.assign(state,{booting:false,page:'exam-organization',token:'fixture',identity:{userId:'student',role:'STUDENT',displayName:'Học sinh'},room:null,contestCode:'public class Main {}',contestDetail:{contest:{...exam},problems:[]},contestWork:{contestId:'TIMER',problem:{id:'Q',title:'Bài thi',statement:'In kết quả',inputFormat:'n',outputFormat:'kết quả',entryFile:'Main.java'}}});
 Object.assign(o,{owner:'student:fixture',view:'detail',contestId:'TIMER',directory:{exams:[exam]},detail:{exam,candidates:[candidate],submissions:[],messages:[],audit:[],serverTime:now},lastLoaded:now});
 window.state=state;window.organization=o;registerPage('exam-organization',renderOrganization);render();
 </script></html>`;
 const server=http.createServer((req,res)=>{if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(html);}const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':'text/css; charset=utf-8');res.end(fs.readFileSync(file));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/api/assignments',async route=>{const body=route.request().postDataJSON();let data={saved:true};if(body.type==='EO_GET')data=await page.evaluate(()=>structuredClone(organization.detail));await route.fulfill({json:{status:'OK',data}});});
  await page.goto('http://127.0.0.1:'+server.address().port);await page.waitForSelector('.eo-personal-timer');assert.ok((await page.locator('body').textContent()).includes('60 phút / học sinh'));
  assert.equal(await page.locator('#contest-code').getAttribute('readonly'),null);assert.ok(await page.locator('[data-action="submit-contest-work"]').isEnabled());
  await page.locator('#contest-code').fill('public class Main { /* local draft */ }');await page.screenshot({path:path.join(out,'personal-timer-active.png')});
  await page.waitForFunction(()=>document.querySelector('.eo-personal-timer')?.textContent.includes('Đã hết thời gian'),{timeout:12000});
  assert.ok(await page.locator('[data-action="submit-contest-work"]').isDisabled());assert.notEqual(await page.locator('#contest-code').getAttribute('readonly'),null);assert.ok(await page.locator('#contest-code-file').isDisabled());
  assert.equal(await page.locator('#contest-code').inputValue(),'public class Main { /* local draft */ }','expiry retains the student draft');
  assert.ok(await page.evaluate(()=>organization.detail.exam.windowEndAt>Date.now()+3600000),'global exam remains open after the individual timer expires');
  assert.ok((await page.locator('#toast-region').textContent()).includes('Đã hết thời gian làm bài'));
  await page.screenshot({path:path.join(out,'personal-timer-expired.png')});assert.deepEqual(errors,[]);console.log('Chrome PASS: individual countdown, expiry while exam remains open, locked editor/file/submission and preserved draft.');
 }finally{if(browser)await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1);});