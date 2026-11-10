// Actual DOM/input events; catalog HTTP is mocked. No real-server/media claim.
const fs=require('fs'),path=require('path'),http=require('http');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
  const root=path.resolve(__dirname,'../static');
  const html=`<!doctype html><meta charset="UTF-8"><link rel="stylesheet" href="/app.css"><link rel="stylesheet" href="/meeting.css"><div id="app"></div><div id="toast-region"></div><script type="module">
    import '/core/events.js';
    import {state} from '/core/state.js';
    import {classroomClick} from '/features/classroom/classroom.js';
    Object.assign(state,{token:'browser-test-token',identity:{userId:'teacher',role:'TEACHER'},room:{roomId:'1',members:[],history:[]},classroom:{phase:'LECTURE',round:0,isOwner:true},booting:true});
    await classroomClick('classroom-pick-exercise',{});
  </script>`;
  const server=http.createServer((req,res)=>{
    if(req.url==='/'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html);return;}
    const file=path.resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
    if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'application/octet-stream');res.end(fs.readFileSync(file));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  let browser;
  try{
    browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1280,height:900}}),errors=[];let catalogRequests=0;
    page.on('pageerror',e=>errors.push(e.message));const practiceRequests=[];
    const exercises=[{id:'A_SUM',title:'Tính tổng mảng',topic:'Mảng'},{id:'B_STR',title:'Đảo ngược chuỗi',topic:'Chuỗi'},{id:'C_SUM',title:'Tổng số chẵn',topic:'Mảng'}].map(e=>({...e,mode:'BUILD_FROM_SCRATCH',difficulty:'Dễ',practiceStatus:'NEW'}));
    await page.route('**/api/assignments',async route=>{
      const body=route.request().postDataJSON();
      if(body.type==='PRACTICE_LIST'){
        practiceRequests.push(body);if(body.query==='B_')await new Promise(resolve=>setTimeout(resolve,700));
        const selected=exercises.filter(e=>(!body.query||(e.id+' '+e.title).toLowerCase().includes(body.query.toLowerCase()))&&(!body.topic||e.topic===body.topic)&&(!body.mode||e.mode===body.mode)&&(!body.difficulty||e.difficulty===body.difficulty)&&(!body.filterStatus||e.practiceStatus===body.filterStatus));
        return route.fulfill({json:{status:'OK',data:{exercises:selected,topics:['Mảng','Chuỗi'],stats:{total:3,accepted:0,failed:0,fresh:3},total:selected.length,page:1,totalPages:1}}});
      }
      catalogRequests++;return route.fulfill({json:{status:'OK',data:{exercises:[{id:'DBG_STR001',title:'Sửa lỗi: Đảo ngược chuỗi'},{id:'DBG_ARR001',title:'Sửa lỗi: Tính tổng mảng'},{id:'DBG_STR002',title:'Sửa lỗi: Kiểm tra palindrome'}]}}});
    });
    await page.goto('http://127.0.0.1:'+server.address().port);const input=page.locator('#classroom-exercise-query'),rows=page.locator('#classroom-exercise-results [data-action="classroom-select-exercise"]');
    await input.waitFor();if(await rows.count()!==3)throw Error('Expected initial catalog');
    if(await input.evaluate(el=>!!el.closest('#app')))throw Error('Regression fixture must exercise dialog outside #app');
    await input.pressSequentially('d');if(await rows.count()!==3)throw Error('First character filter failed');
    await input.pressSequentially('a');if(await rows.count()!==1)throw Error('Results do not narrow while typing');
    await input.pressSequentially('o');if(await rows.first().getAttribute('data-id')!=='DBG_STR001')throw Error('Unaccented Vietnamese match failed');
    if(!await input.evaluate(el=>el===document.activeElement))throw Error('Filtering stole input focus');
    await input.fill('DBG_ARR001');if(await rows.count()!==1||await rows.first().getAttribute('data-id')!=='DBG_ARR001')throw Error('Code search failed');
    await input.fill('khong-ton-tai');if(await rows.count()!==0)throw Error('Empty search failed');
    await page.locator('[data-action="classroom-exercise-clear"]').click();if(await rows.count()!==3||await input.inputValue()!=='')throw Error('Clear search failed');
    await input.pressSequentially('dao');await page.screenshot({path:path.resolve(__dirname,'../../server/build/classroom-picker-live-search.png')});
    if(catalogRequests!==1)throw Error('Typing should filter the loaded catalog without repeated HTTP requests');
    if(errors.length)throw Error(errors.join('; '));
    console.log('Chrome picker search PASS: real input outside #app, progressive filtering, accent-insensitive/code search, focus, no results and clear. Catalog HTTP mocked.');
    await page.evaluate(async()=>{
      document.getElementById('active-modal')?.remove();
      const {state}=await import('/core/state.js'),{registerPage}=await import('/core/router.js'),{render}=await import('/ui/render.js'),{loadPractice,renderPractice}=await import('/features/exercises/practice.js');
      Object.assign(state,{page:'exercises',exerciseMode:'practice',identity:{userId:'student',role:'STUDENT',displayName:'Student'},room:null,roomId:'',booting:false});
      registerPage('exercises',renderPractice);await loadPractice();render();
    });
    const practiceQuery=page.locator('#practice-query'),practiceRows=page.locator('.practice-row');
    if(await practiceRows.count()!==3)throw Error('Initial practice catalog missing');
    const beforePractice=practiceRequests.length;
    await practiceQuery.pressSequentially('A_SU');
    await page.waitForFunction(()=>document.querySelectorAll('.practice-row').length===1&&document.querySelector('.practice-row').dataset.id==='A_SUM');
    if(practiceRequests.length!==beforePractice+1)throw Error('Typing burst was not debounced: '+JSON.stringify(practiceRequests.map(r=>r.query)));
    if(!await practiceQuery.evaluate(el=>document.activeElement===el&&el.selectionStart===4))throw Error('Practice search lost focus/caret');
    await page.locator('#practice-topic').selectOption('Mảng');await page.locator('#practice-difficulty').selectOption('Dễ');await page.locator('#practice-mode').selectOption('BUILD_FROM_SCRATCH');await page.locator('#practice-status').selectOption('NEW');
    await practiceQuery.fill('C_');await page.waitForFunction(()=>document.querySelector('.practice-row')?.dataset.id==='C_SUM');
    const last=practiceRequests.at(-1);if(last.page!==1||last.topic!=='Mảng'||last.difficulty!=='Dễ'||last.mode!=='BUILD_FROM_SCRATCH'||last.filterStatus!=='NEW')throw Error('Live search lost selected filters');
    await practiceQuery.fill('B_');await page.waitForTimeout(320);await practiceQuery.fill('A_');
    await page.waitForFunction(()=>document.querySelector('.practice-row')?.dataset.id==='A_SUM');await page.waitForTimeout(800);
    if(await practiceRows.count()!==1||await practiceRows.first().getAttribute('data-id')!=='A_SUM')throw Error('Late response replaced current practice results');
    await practiceQuery.fill('');await page.waitForFunction(()=>document.querySelectorAll('.practice-row').length===2);
    await page.evaluate(()=>{
      const input=document.getElementById('practice-query');window.composingPracticeInput=input;window.practiceCompositionEnds=0;
      input.addEventListener('compositionend',()=>window.practiceCompositionEnds++);
      input.focus();input.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true}));input.value='A_';
      input.dispatchEvent(new InputEvent('input',{bubbles:true,isComposing:true,inputType:'insertCompositionText',data:'A_'}));
    });
    await page.waitForFunction(()=>document.querySelector('.practice-row')?.dataset.id==='A_SUM'&&document.querySelectorAll('.practice-row').length===1);
    if(!await page.evaluate(()=>document.getElementById('practice-query')===window.composingPracticeInput&&window.practiceCompositionEnds===0))throw Error('IME live search replaced input or required compositionend');
    await page.evaluate(()=>{
      const input=window.composingPracticeInput;input.value='C_';
      input.dispatchEvent(new InputEvent('input',{bubbles:true,isComposing:true,inputType:'insertCompositionText',data:'C_'}));
    });
    await page.waitForFunction(()=>document.querySelector('.practice-row')?.dataset.id==='C_SUM');
    if(!await page.evaluate(()=>document.getElementById('practice-query')===window.composingPracticeInput&&window.practiceCompositionEnds===0))throw Error('IME continuation required Enter or lost original input');
    await page.evaluate(()=>window.composingPracticeInput.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true,data:'C_'})));
    await page.screenshot({path:path.resolve(__dirname,'../../server/build/practice-live-search.png')});
    if(errors.length)throw Error(errors.join('; '));
    console.log('Chrome practice search PASS: typing, debounce, filter retention, focus/caret, stale responses and composing input before Enter/compositionend; original input node preserved. HTTP mocked; IME event sequence simulated.');
  }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{console.error(e.stack);process.exit(1)});
