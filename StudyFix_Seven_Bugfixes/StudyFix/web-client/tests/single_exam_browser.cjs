const {chromium}=require(process.env.STUDYFIX_BROWSER_TEST_MODULES+'/playwright'),assert=require('assert/strict'),fs=require('fs'),path=require('path');
(async()=>{const browser=await chromium.launch({headless:true,channel:'chrome'});try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 await page.goto(process.argv[2]);await page.locator('#username').fill('teacher');await page.locator('#password').fill('Teacher123!');await page.locator('.login-submit').click();await page.locator('[data-action="toggle-sidebar-group"][data-group="exam-bank"]').click();await page.locator('.nav [data-action="eb-list"]').click();
 assert.equal(await page.locator('[data-action="eb-versions"]').count(),0);await page.locator('#eb-search').fill('EXAM_A');await page.waitForFunction(()=>document.querySelectorAll('tbody .btn.link[data-action="eb-open"]').length===1);
 await page.locator('tbody .btn.link[data-action="eb-open"]').click();await page.locator('[data-action="eb-edit"]').waitFor();assert.equal(await page.locator('[data-action="eb-clone"]').count(),0);assert.equal(await page.getByText('Lịch sử phiên bản',{exact:true}).count(),0);
 const id=await page.evaluate(async()=>{const {eb}=await import('/features/exam-bank/state.js');return eb.exam.id;});await page.locator('[data-action="eb-edit"]').click();await page.locator('#eb-f-title').fill('Đề cập nhật trực tiếp');
 const save=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EXAMBANK_SAVE');await page.locator('[data-action="eb-save"]').click();const saved=(await (await save).json()).data.exam;assert.equal(saved.id,id);assert.equal(saved.status,'DRAFT');
 await page.reload();await page.locator('#eb-f-title').waitFor();assert.equal(await page.locator('#eb-f-title').inputValue(),'Đề cập nhật trực tiếp');
 await page.locator('[data-action="eb-step"][data-step="5"]').click();const approval=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EXAMBANK_APPROVE');await page.locator('[data-action="eb-finish"]').click();assert.equal((await (await approval).json()).data.exam.id,id);
 await page.locator('[data-action="eb-edit"]').waitFor();const out=path.resolve(__dirname,'../../server/build/single-exam/qa');fs.mkdirSync(out,{recursive:true});await page.screenshot({path:path.join(out,'updated-exam.png'),fullPage:true});
 const archived=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EXAMBANK_ARCHIVE');await page.locator('[data-action="eb-archive"]').click();assert.equal((await (await archived).json()).status,'OK');assert.deepEqual(errors,[]);console.log('Single exam Chrome PASS: no version navigation/history/clone, edit approved or archived exam in place, reload, approve and archive.');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});


