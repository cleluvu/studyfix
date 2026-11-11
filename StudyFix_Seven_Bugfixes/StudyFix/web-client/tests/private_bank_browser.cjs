const {chromium}=require(process.env.STUDYFIX_BROWSER_TEST_MODULES+'/playwright');
const path=require('path'),fs=require('fs'),assert=require('assert/strict');
(async()=>{
 const base=process.argv[2],zip=process.argv[3],out=path.resolve(__dirname,'../../server/build-bank-scopes/qa');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(15000);
  await page.goto(base);await page.locator('#username').fill('teacher');await page.locator('#password').fill('Teacher123!');await page.locator('.login-submit').click();
  await page.locator('[data-action="toggle-sidebar-group"][data-group="exercises"]').click();await page.locator('.bank-subnav [data-view="private"]').click();await page.getByRole('heading',{name:'Kì thi - Thực hành',exact:true}).waitFor();
  assert.match(await page.locator('.bank-all-table').innerText(),/SCOPE_PRIVATE/);
  assert.equal(await page.locator('[data-action="practice-create"]').count(),0);
  await page.getByRole('button',{name:'import file',exact:true}).click();await page.locator('#oj-import-file').setInputFiles(zip);
  await page.locator('#oj-import-commit:not(:disabled)').waitFor();
  const committed=page.waitForResponse(r=>r.url().endsWith('/api/oj/import/commit'));
  await page.locator('#oj-import-commit').click();const response=await committed;assert.equal(response.request().postDataJSON().bankScope,'EXAM_PRIVATE');assert.equal(response.status(),200);
  await page.getByText('PRIVATE_IMPORT001',{exact:true}).first().waitFor();await page.screenshot({path:path.join(out,'private-bank.png'),fullPage:true});
  await page.locator('.bank-page-tabs [data-view="all"]').click();await page.getByRole('heading',{name:'Luyện tập',exact:true}).waitFor();assert.doesNotMatch(await page.locator('.bank-all-table').innerText(),/SCOPE_PRIVATE|PRIVATE_IMPORT001/);
  await page.locator('.bank-page-tabs [data-view="private"]').click();await page.locator('.bank-page-tabs [data-view="create"]').click();await page.locator('[data-action="builder-new"]').click();
  await page.locator('[data-builder-field="bankScope"]').waitFor();assert.equal(await page.locator('[data-builder-field="bankScope"]').inputValue(),'EXAM_PRIVATE');assert.equal(await page.locator('#builder-dialog').count(),0);
  await page.locator('[data-builder-field="title"]').fill('Bài thi riêng tư kiểm thử');
  await page.locator('[data-action="builder-save"]').click();await page.waitForFunction(()=>document.getElementById('builder-save-status')?.textContent.startsWith('Đã lưu'));
  await page.screenshot({path:path.join(out,'create-private.png'),fullPage:true});
  await page.reload();await page.locator('[data-builder-field="bankScope"]').waitFor();assert.equal(await page.locator('[data-builder-field="bankScope"]').inputValue(),'EXAM_PRIVATE');
  await page.locator('[data-builder-field="bankScope"]').selectOption('PRACTICE');await page.locator('[data-action="builder-save"]').click();await page.waitForFunction(()=>document.getElementById('builder-save-status')?.textContent.startsWith('Đã lưu'));
  await page.locator('[data-action="builder-close"]').click();await page.getByRole('heading',{name:'Luyện tập',exact:true}).waitFor();
  await page.locator('.bank-page-tabs [data-view="create"]').click();await page.locator('[data-action="builder-directory"]').click();
  await page.locator('.pb-draft-row').filter({hasText:'SCOPE_PRIVATE'}).last().locator('[data-action="builder-open"]').click();
  await page.locator('.pb-footer [data-action="builder-finish"]').waitFor();
  assert.equal(await page.locator('.pb-footer [data-action="builder-next"]').count(),0);
  await page.locator('.pb-footer [data-action="builder-finish"]').click();await page.getByRole('heading',{name:'Kì thi - Thực hành',exact:true}).waitFor();assert.match(await page.locator('.bank-all-table').innerText(),/SCOPE_PRIVATE/);
  await page.reload();await page.getByRole('heading',{name:'Kì thi - Thực hành',exact:true}).waitFor();
  await page.locator('.bank-page-tabs [data-view="create"]').click();await page.getByRole('heading',{name:'Bản nháp của tôi',exact:true}).waitFor();assert.equal(await page.locator('.pb-workspace').count(),0);
  const student=await browser.newPage();await student.goto(base);await student.locator('#username').fill('student1');await student.locator('#password').fill('Student123!');await student.locator('.login-submit').click();await student.locator('[data-action="toggle-sidebar-group"][data-group="exercises"]').click();await student.locator('.bank-subnav [data-view="all"]').click();await student.getByRole('heading',{name:'Luyện tập lập trình'}).waitFor();assert.equal(await student.locator('[data-view="private"],[data-view="create"]').count(),0);assert.doesNotMatch(await student.locator('.bank-all-table').innerText(),/SCOPE_PRIVATE|PRIVATE_IMPORT001/);
  assert.deepEqual(errors,[]);console.log('Private bank Chrome PASS: destinations, import, persistence, student navigation and visibility.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
