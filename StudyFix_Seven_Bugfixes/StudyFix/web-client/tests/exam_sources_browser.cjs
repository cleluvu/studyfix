const {chromium}=require(process.env.STUDYFIX_BROWSER_TEST_MODULES+'/playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'}),out=path.resolve(__dirname,'../../server/build/exam-sources/qa');fs.mkdirSync(out,{recursive:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.setDefaultTimeout(15000);page.on('pageerror',e=>errors.push(e.message));
  await page.goto(process.argv[2]);await page.locator('#username').fill('teacher');await page.locator('#password').fill('Teacher123!');await page.locator('.login-submit').click();
  await page.locator('[data-action="toggle-sidebar-group"][data-group="exam-bank"]').click();await page.locator('.nav [data-action="eb-list"]').click();await page.locator('[data-action="eb-new"]').click();await page.locator('#eb-f-title').fill('Random source browser');await page.locator('#eb-f-code').fill('RANDOM_BROWSER');await page.locator('[data-action="eb-next"]').click();
  assert.equal(await page.getByRole('button',{name:'Theo Week Luyện tập',exact:true}).count(),1);
  assert.equal(await page.locator('[data-action="eb-week-random"]').count(),1);
  await page.locator('[data-eb-week]').first().check();assert.equal(await page.locator('.selected-row').count(),6);
  await page.locator('[data-eb-week-count]').first().fill('3');await page.locator('[data-action="eb-week-random"]').first().click();assert.equal(await page.locator('.selected-row').count(),3);assert.equal(await page.locator('[data-eb-question]').count(),0);
  await page.screenshot({path:path.join(out,'week-inline-random.png'),fullPage:true});
  await page.locator('[data-action="eb-source-week-open"]').first().click();assert.equal(await page.locator('[data-eb-question]').count(),6);assert.equal(await page.locator('[data-eb-question]:checked').count(),3);
  let chosen=await page.locator('.selected-row strong').allTextContents();assert.ok(chosen.every(code=>code.startsWith('SOURCE_PUBLIC_')));assert.equal(new Set(chosen).size,3);
  await page.locator('#eb-random-count').fill('2');await page.locator('[data-action="eb-random"]').click();assert.equal(await page.locator('.selected-row').count(),5);chosen=await page.locator('.selected-row strong').allTextContents();assert.equal(new Set(chosen).size,5);
  await page.locator('#eb-random-count').fill('9');await page.locator('[data-action="eb-random"]').click();assert.equal(await page.locator('.selected-row').count(),5);await page.getByRole('alert').filter({hasText:'chỉ còn 1'}).waitFor();
  await page.screenshot({path:path.join(out,'week-random.png'),fullPage:true});
  await page.locator('[data-action="eb-source-private"]').click();assert.equal(await page.locator('[data-eb-question]').count(),4);assert.ok((await page.locator('[data-eb-question]').evaluateAll(es=>es.map(e=>e.dataset.ebQuestion))).every(id=>id.startsWith('SOURCE_PRIVATE_')));
  await page.locator('#eb-random-count').fill('2');await page.locator('[data-action="eb-random"]').click();assert.equal(await page.locator('.selected-row').count(),7);await page.screenshot({path:path.join(out,'private-random.png'),fullPage:true});
  await page.locator('[data-action="eb-source-all"]').click();await page.locator('#eb-question-search').fill('SOURCE_PUBLIC_8');assert.equal(await page.locator('[data-eb-question]').count(),1);await page.locator('#eb-random-count').fill('1');await page.locator('[data-action="eb-random"]').click();assert.equal(await page.locator('.selected-row').count(),8);
  await page.locator('[data-action="eb-next"]').click();assert.equal(await page.locator('#eb-score-total').innerText(),'8');assert.equal(await page.locator('.score-input').count(),8);await page.locator('.score-input').first().fill('20');await page.locator('.score-input').nth(1).fill('3');assert.equal(await page.locator('#eb-score-total').innerText(),'29');await page.screenshot({path:path.join(out,'equal-ten-structure.png'),fullPage:true});
  const saved=page.waitForResponse(r=>r.url().endsWith('/api/assignments')&&r.request().postDataJSON()?.type==='EXAMBANK_SAVE');await page.locator('[data-action="eb-save"]').click();const result=await (await saved).json();assert.equal(result.status,'OK');assert.equal(result.data.exam.items.length,8);assert.equal(result.data.exam.totalPoints,10);assert.equal(result.data.exam.scoringMode,'WEIGHTED_TEN');assert.equal(result.data.exam.items[0].points,20);assert.equal(result.data.exam.items[1].points,3);assert.equal(result.data.exam.items.reduce((n,i)=>n+i.points,0),29);assert.ok(result.data.exam.sourceKinds.includes('WEEK'));assert.ok(result.data.exam.sourceKinds.includes('PRIVATE'));
  assert.deepEqual(errors,[]);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));console.log('Exam sources Chrome PASS: Week expansion, exact counts, random without duplicates, private/public isolation, search and scoring.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
