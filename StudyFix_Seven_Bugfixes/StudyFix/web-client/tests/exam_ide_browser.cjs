const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
 const root=path.resolve(__dirname,'../static'),out=path.resolve(__dirname,'../../server/build-debug-lessons/ide-support');fs.mkdirSync(out,{recursive:true});
 const styles=[...fs.readFileSync(path.join(root,'index.html'),'utf8').matchAll(/<link rel="stylesheet" href="([^"]+)"\s*\/>/g)].map(m=>`<link rel="stylesheet" href="${m[1]}">`).join('');
 const html=`<!doctype html><html lang="vi"><meta charset="utf-8">${styles}<div id="app"></div><div id="toast-region"></div><script type="module">
 import '/core/events.js';import {state} from '/core/state.js';import {registerPage} from '/core/router.js';import {render} from '/ui/render.js';import {renderOrganization,organization as o} from '/features/exam-organization/index.js';import {loadExam} from '/features/contests/exam-monitoring.js';
 const now=Date.now(),exam={id:'IDE_EXAM',roomId:'fixture-room',title:'Thi lập trình với IDE',code:'IDE_EXAM',subject:'Java',status:'RUNNING',state:'PUBLISHED',access:'STUDENT',startAt:now-60000,endAt:now+3600000,durationMinutes:60,attemptStartedAt:now,policy:{},classes:[]},candidate={userId:'student',displayName:'Học sinh',className:'Java',startedAt:now,finishedAt:0};
 Object.assign(state,{booting:false,page:'exam-organization',token:'fixture',identity:{userId:'student',role:'STUDENT',displayName:'Học sinh'},room:null,contestCode:'public class Main {}',contestDetail:{contest:exam,problems:[]},contestWork:{contestId:exam.id,problem:{id:'Q',title:'Bài thi',statement:'In kết quả',inputFormat:'n',outputFormat:'kết quả',entryFile:'Main.java'}}});
 Object.assign(o,{owner:'student:fixture',view:'detail',contestId:exam.id,directory:{exams:[exam]},detail:{exam,candidates:[candidate],submissions:[],messages:[],audit:[],serverTime:now},lastLoaded:now});
 window.state=state;window.organization=o;registerPage('exam-organization',renderOrganization);render();await loadExam(exam.id);render();
 </script></html>`;
 const csp=fs.readFileSync(path.resolve(root,'../../server/src/studyfix/WebGatewayServer.java'),'utf8').match(/h.set\("Content-Security-Policy", "([^"]+)"\)/)[1];
 const server=http.createServer((req,res)=>{res.setHeader('Content-Security-Policy',csp);if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');return res.end(html);}const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.zip')?'application/zip':'text/css; charset=utf-8');res.end(fs.readFileSync(file));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[],events=[];page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));
  let kind='BROWSER',seq=1,changes=[],offline=false;
  const sample=next=>{kind=next;changes.push({sequence:++seq,at:Date.now(),kind});};
  await page.route('http://127.0.0.1:18765/**',async route=>{if(offline)return route.abort();await route.fulfill({json:{protocol:1,boot:'bridge',sequence:seq,sampledAt:Date.now(),kind,truncated:false,changes},headers:{'Access-Control-Allow-Origin':'http://127.0.0.1:'+server.address().port}});});
  await page.route('**/api/assignments',async route=>{const body=route.request().postDataJSON();let data={accepted:true};if(body.type==='EXAM_EVENT')events.push(body);if(body.type==='EXAM_STATUS')data={enabled:true,live:true,generation:'g',examSessionId:'session',startAt:Date.now()-60000,endAt:Date.now()+3600000,serverTime:Date.now()};if(body.type==='EO_GET')data=await page.evaluate(()=>structuredClone(organization.detail));await route.fulfill({json:{status:'OK',data}});});
  await page.goto('http://127.0.0.1:'+server.address().port);await page.locator('#exam-student-status:not([hidden])').waitFor();
  await page.locator('[data-action="exam-ide-help"]').click();await page.getByRole('heading',{name:'Sử dụng VS Code / NetBeans khi thi'}).waitFor();assert.equal((await page.request.get('http://127.0.0.1:'+server.address().port+'/downloads/studyfix-ide-support.zip')).status(),200);await page.locator('#active-modal [data-action="close-modal"]').click();
  await page.locator('[data-action="exam-connect-ide"]').click();await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('đã kết nối'),{timeout:8000}).catch(async error=>{throw Error(error.message+' '+await page.locator('#toast-region').textContent());});
  sample('VSCODE');await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});Object.defineProperty(document,'hasFocus',{configurable:true,value:()=>false});document.dispatchEvent(new Event('visibilitychange'));});
  await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('Đang dùng VS Code'));assert.equal(events.filter(e=>e.eventType==='TAB_SWITCH').length,0);
  sample('NETBEANS');await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('Đang dùng NetBeans'));assert.equal(events.filter(e=>e.eventType==='TAB_SWITCH').length,0);
  sample('BROWSER');await page.waitForFunction(()=>/đã rời|mất focus/.test(document.getElementById('exam-student-status').textContent));await page.waitForTimeout(100);assert.equal(events.filter(e=>e.eventType==='TAB_SWITCH').length,1);
  sample('VSCODE');await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('Đang dùng VS Code'));
  offline=true;await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('Muốn dùng VS Code'));await page.waitForTimeout(100);assert.equal(events.filter(e=>e.eventType==='TAB_SWITCH').length,2);
  offline=false;sample('NETBEANS');await page.waitForFunction(()=>document.getElementById('exam-student-status').textContent.includes('Đang dùng NetBeans'));
  await page.screenshot({path:path.join(out,'student-netbeans-allowed.png')});
  sample('BROWSER');await page.waitForFunction(()=>/đã rời|mất focus/.test(document.getElementById('exam-student-status').textContent));await page.screenshot({path:path.join(out,'student-browser-warning.png')});assert.deepEqual(errors,[]);
  console.log('IDE Chrome PASS: current exam UI, download/help, CSP loopback access, VS Code/NetBeans exemptions, browser warnings, helper disconnect/recovery (foreground samples mocked).');
 }finally{if(browser)await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1)});