// Run StudentPortalIntegrationTest --browser first. Uses installed Chrome and Node WebSocket.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build-student-portal'),f=JSON.parse(fs.readFileSync(path.join(out,'fixture.json')));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function main(){
 fs.rmSync(path.join(out,'browser-error.log'),{force:true});
 fs.writeFileSync(path.join(out,'browser-progress.log'),`starting ${process.pid}\n`);
 const chrome=spawn(process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--no-sandbox','--disable-features=RendererCodeIntegrity','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=0',`--user-data-dir=${path.join(out,'browser-profile-'+Date.now())}`],{windowsHide:true,stdio:['ignore','ignore','pipe']});
 let socket,id=0;const pending=new Map(),errors=[],passed=[],keepAlive=setInterval(()=>{},1000);
 try{
  const endpoint=await new Promise((resolve,reject)=>{let log='';const timer=setTimeout(()=>reject(Error('Chrome startup timeout')),20000);chrome.stderr.on('data',chunk=>{log+=chunk;const match=log.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match){clearTimeout(timer);resolve(match[1]);}});chrome.on('error',reject);});
  fs.appendFileSync(path.join(out,'browser-progress.log'),'chrome ready\n');
  socket=new WebSocket(endpoint);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
  fs.appendFileSync(path.join(out,'browser-progress.log'),'socket ready\n');
  socket.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const task=pending.get(m.id);if(task){pending.delete(m.id);m.error?task.reject(Error(m.error.message)):task.resolve(m.result);}}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description||m.params.exceptionDetails.text);};
  function call(method,params={},sessionId){return new Promise((resolve,reject)=>{const key=++id,timer=setTimeout(()=>{pending.delete(key);reject(Error('CDP timeout: '+method));},20000);pending.set(key,{resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}});socket.send(JSON.stringify({id:key,method,params,sessionId}));});}
  const target=await call('Target.createTarget',{url:'about:blank'}),attached=await call('Target.attachToTarget',{targetId:target.targetId,flatten:true}),session=attached.sessionId;
  fs.appendFileSync(path.join(out,'browser-progress.log'),'target ready\n');
  const c=(method,params)=>call(method,params,session);
  await c('Runtime.enable');await c('Page.enable');
  async function evaluate(expression){const result=await c('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);return result.result.value;}
  async function wait(expression){const until=Date.now()+20000;while(Date.now()<until){if(await evaluate(expression))return;await delay(100);}throw Error('Timeout: '+expression+'; '+await evaluate('document.body.innerText.slice(-1800)'));}
  await c('Page.navigate',{url:f.base});await wait('!!document.querySelector("#username")');
  await evaluate(`sessionStorage.setItem('studyfix.web.token',${JSON.stringify(f.token)});location.reload()`);
  await wait('!!document.querySelector(".student-portal .sp-stats")');
  assert.match(await evaluate('document.body.innerText'),/Tổng quan học sinh/);assert.equal(await evaluate('document.querySelectorAll(".student-portal .sp-metric").length'),4);passed.push('student login overview and live metrics');
  for(const page of ['overview','student-classes','student-ranking','student-progress','exercises','exam-organization']){
   await evaluate(`(async()=>{await (await import('/core/polling.js')).navigate(${JSON.stringify(page)})})()`);
   await wait(`document.body.innerText.includes(${JSON.stringify(({overview:'Tổng quan học sinh','student-classes':'Lớp học của tôi','student-ranking':'Bảng xếp hạng','student-progress':'Tiến độ học tập',exercises:'Tất cả bài tập','exam-organization':'Kỳ thi của tôi'})[page])})`);
   for(const width of [1440,1024,768,390]){
    await c('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});await delay(350);
    assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true,`${page}: overflow at ${width}`);
   }
   if(page==='overview'||page==='student-classes'||page==='student-progress'){
    const shot=await c('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});fs.writeFileSync(path.join(out,`${page}-mobile.png`),Buffer.from(shot.data,'base64'));
   }
   await c('Emulation.setDeviceMetricsOverride',{width:1440,height:960,deviceScaleFactor:1,mobile:false});passed.push(page+' at desktop, laptop, tablet, mobile');
  }
  await evaluate(`(async()=>{await (await import('/core/polling.js')).navigate('student-progress')})()`);
  assert.equal(await evaluate('document.querySelectorAll(".student-portal .sp-row").length>=20'),true);
  await evaluate('document.querySelector(\'[data-action="sp-page"][data-page="2"]\').click()');await wait('document.querySelector(".sp-pagination")?.innerText.includes("2 / 2")');passed.push('history pagination');
  await evaluate('document.querySelector("[data-action=sp-history][data-source=practice]").click()');await wait('!!document.querySelector("#active-modal")');assert.match(await evaluate('document.querySelector("#active-modal").innerText'),/PRIVATE_JAVA/);await evaluate('document.querySelector("[data-action=close-modal]")?.click()');passed.push('practice source and result history');
  await evaluate(`(async()=>{await (await import('/core/polling.js')).navigate('student-ranking');const form=document.querySelector('#sp-filter-form');form.elements.classId.value=${JSON.stringify(f.roomId)};form.elements.days.value='30';form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));})()`);
  await wait('document.querySelectorAll(".sp-table-wrap tbody tr").length===1');assert.equal(await evaluate('document.querySelectorAll(".sp-me").length'),1);passed.push('class/time ranking filters and personal position');
  await evaluate(`window.__originalFetch=window.fetch;window.fetch=(url,...args)=>String(url).startsWith('/api/student/portal')?Promise.reject(Error('QA unavailable')):window.__originalFetch(url,...args);document.querySelector('[data-action="sp-reload"]').click();`);
  await wait('!!document.querySelector(".student-portal [role=alert]")');assert.equal(await evaluate('document.querySelectorAll(".sp-me").length'),1);
  await evaluate(`window.fetch=window.__originalFetch;document.querySelector('.student-portal [role=alert] button').click()`);await wait('!document.querySelector(".student-portal [role=alert]")');passed.push('refresh failure preserves data and retry recovers');
  await evaluate(`(async()=>{document.querySelector('#active-modal')?.remove();await (await import('/core/polling.js')).navigate('student-classes')})()`);await evaluate('document.querySelector("[data-action=sp-join]").click()');await wait('!!document.querySelector(".meeting-shell")');
  assert.equal(await evaluate('!!document.querySelector("[data-op=START],[data-op=END],[data-op=MERGE]")'),false);passed.push('student meeting reuses classroom with teacher controls hidden');
  await evaluate(`(async()=>{await (await import('/core/polling.js')).navigate('overview')})()`);const desktop=await c('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});fs.writeFileSync(path.join(out,'overview-desktop.png'),Buffer.from(desktop.data,'base64'));
  assert.deepEqual(errors,[]);passed.push('no uncaught browser exceptions');fs.writeFileSync(path.join(out,'browser-results.json'),JSON.stringify({passed},null,2));console.log(passed.map(p=>'PASS '+p).join('\n'));
 }finally{clearInterval(keepAlive);socket?.close();chrome.kill();}
}
main().catch(e=>{fs.writeFileSync(path.join(out,'browser-error.log'),e.stack);console.error(e);process.exitCode=1;});
