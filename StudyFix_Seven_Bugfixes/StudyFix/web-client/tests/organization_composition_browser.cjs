const {chromium}=require(process.env.STUDYFIX_BROWSER_TEST_MODULES+'/playwright'),assert=require('assert/strict'),fs=require('fs'),path=require('path');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'}),out=path.resolve(__dirname,'../../server/build/multi-exam/qa');fs.mkdirSync(out,{recursive:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.setDefaultTimeout(15000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto(process.argv[2]);await page.locator('#username').fill('teacher');await page.locator('#password').fill('Teacher12345!');await page.locator('.login-submit').click();await page.locator('[data-action="toggle-sidebar-group"][data-group="exam-organization"]').click();await page.locator('.nav [data-action="eo-nav"][data-view="create"]').click();
  await page.locator('#eo-f-title').fill('Kỳ thi ghép nhiều đề');await page.locator('#eo-f-code').fill('MULTI_BROWSER');await page.locator('#eo-f-subject').fill('Java');await page.locator('.eo-multiselect summary').click();await page.locator('[data-eo-class]').first().check();await page.locator('[data-action="eo-next"]').click();
  const sources=await page.evaluate(async()=>{const {organization:o}=await import('/features/exam-organization/state.js');return {first:o.sources.exams.find(e=>e.code==='EO_TEMPLATE').id,second:o.sources.exams.find(e=>e.code==='EO_MIX_SECOND').id};});
  await page.locator('[name="eo-template"]').first().waitFor();const count=await page.locator('[name="eo-template"]').count();
  await page.locator(`[name="eo-template"][value="${sources.first}"]`).check();await page.locator(`[name="eo-template"][value="${sources.second}"]`).check();assert.equal(await page.locator('[name="eo-template"]:checked').count(),2);
  await page.locator(`[data-eo-exam-count="${sources.first}"]`).fill('2');await page.locator(`[data-eo-exam-count="${sources.second}"]`).fill('3');
  const compose=async()=>{const result=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EO_COMPOSE');await page.locator('[data-action="eo-compose-exams"]').click();const data=await (await result).json();assert.equal(data.status,'OK');await page.waitForFunction(async()=>!(await import('/features/exam-organization/state.js')).organization.busy);return data.data.definition;};
  let definition=await compose();assert.equal(definition.items.length,5);assert.equal(new Set(definition.items.map(i=>i.exerciseId)).size,5);assert.equal(definition.totalPoints,10);assert.deepEqual(definition.compositionSources.map(s=>s.exerciseIds.length),[2,3]);
  const ids=definition.items.map(i=>i.exerciseId),firstId=definition.id;
  await page.screenshot({path:path.join(out,'multiple-sources.png'),fullPage:true});await page.reload();await page.locator('[data-action="eo-compose-exams"]').waitFor();
  assert.equal(await page.locator('[name="eo-template"]:checked').count(),2);assert.equal(await page.locator('[name="eo-template"]').count(),count);assert.equal(await page.locator(`[data-eo-exam-count="${sources.second}"]`).inputValue(),'3');
  const restored=await page.evaluate(async()=>{const {organization:o}=await import('/features/exam-organization/state.js');return o.definition.items.map(i=>i.exerciseId);});assert.deepEqual(restored,ids);
  definition=await compose();assert.notEqual(definition.id,firstId);assert.equal(definition.items.length,5);
  await page.locator(`[data-eo-exam-count="${sources.first}"]`).fill('1');await page.locator(`[data-eo-exam-count="${sources.first}"]`).blur();assert.ok(await page.locator('[data-action="eo-preview-exam"]').isDisabled());await page.locator('[data-action="eo-next"]').click();await page.getByRole('alert').filter({hasText:'Ghép đề ngẫu nhiên'}).waitFor();
  await page.locator(`[data-eo-exam-count="${sources.first}"]`).fill('2');definition=await compose();
  await page.locator('#eo-question-selection-mode').selectOption('MANUAL');
  await page.locator('[data-eo-question]').first().waitFor();
  assert.equal(await page.locator('[data-eo-exam-count]').count(),0);
  assert.ok(await page.locator('[data-action="eo-compose-exams"]').isDisabled());
  await page.locator('[data-eo-question="EO_A"]').first().check();await page.locator('[data-eo-question="EO_D"]').check();
  definition=await compose();assert.deepEqual(definition.items.map(i=>i.exerciseId),['EO_A','EO_D']);
  assert.equal(definition.compositionSources[0].mode,'MANUAL');
  await page.reload();await page.locator('[data-eo-question="EO_D"]:checked').waitFor();assert.equal(await page.locator('#eo-question-selection-mode').inputValue(),'MANUAL');assert.equal(await page.locator('[data-eo-question]:checked').count(),2);
  await page.locator('[data-action="eo-questions-none"]').click();assert.equal(await page.locator('[data-eo-question]:checked').count(),0);
  await page.locator(`[data-action="eo-source-all"][data-id="${sources.first}"]`).click();assert.equal(await page.locator('[data-eo-question]:checked').count(),2);
  await page.locator(`[data-action="eo-source-all"][data-id="${sources.first}"]`).click();assert.equal(await page.locator('[data-eo-question]:checked').count(),0);
  await page.locator('[data-action="eo-questions-all"]').click();assert.equal(await page.locator('[data-eo-question]:checked').count(),7);
  definition=await compose();assert.equal(definition.items.length,5);assert.equal(new Set(definition.items.map(i=>i.exerciseId)).size,5);
  await page.screenshot({path:path.join(out,'manual-question-selection.png'),fullPage:true});
  await page.locator('[data-action="eo-next"]').click();await page.locator('[data-eo-student]').first().waitFor();await page.locator('[data-action="eo-next"]').click();
  const visibility=page.locator('#eo-policy-publicTestVisibility');
  await visibility.waitFor();
  assert.deepEqual(await visibility.locator('option').evaluateAll(options=>options.map(o=>o.value)),['NONE','PASSED','FAILED']);
  assert.equal(await visibility.inputValue(),'NONE');
  for(const mode of ['PASSED','NONE','FAILED']){await visibility.selectOption(mode);assert.equal(await page.evaluate(async()=>{const {organization:o}=await import('/features/exam-organization/state.js');return o.draft.document.policy.publicTestVisibility;}),mode);}
  const saved=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EO_DRAFT_SAVE');await page.locator('[data-action="eo-save-draft"]').click();assert.equal((await (await saved).json()).data.document.policy.publicTestVisibility,'FAILED');
  await page.reload();await visibility.waitFor();assert.equal(await visibility.inputValue(),'FAILED');
  await page.screenshot({path:path.join(out,'public-test-visibility.png'),fullPage:true});
  const rendered=await page.evaluate(async()=>{const {renderSubmissionResult}=await import('/features/exercises/exercises.js');return renderSubmissionResult({status:'DONE',verdict:'WA',score:4,maxScore:10,totalTests:null,passedTests:null,testResults:[{test:2,passed:false,message:'PUBLIC_FAIL'}]});});assert.ok(rendered.includes('Test 2'));assert.ok(!rendered.includes('0/0'));assert.ok(rendered.includes('PUBLIC_FAIL'));
  await page.locator('[data-action="eo-next"]').click();
  const scheduled=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EO_SCHEDULE');await page.locator('[data-action="eo-schedule"]').click();const data=await (await scheduled).json();assert.equal(data.status,'OK');assert.equal(data.data.problemCount,5);assert.equal(data.data.totalPoints,10);assert.equal(data.data.compositionSources.length,2);
  assert.equal(data.data.policy.publicTestVisibility,'FAILED');assert.deepEqual(errors,[]);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));console.log('Multi-exam Chrome PASS: random/manual composition, individual/select-all choices, deduplication, draft reload and schedule.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
