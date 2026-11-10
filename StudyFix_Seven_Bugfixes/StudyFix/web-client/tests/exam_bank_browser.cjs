// Real browser + isolated HTTP/SQLite fixture. Usage: node tests/exam_bank_browser.cjs http://127.0.0.1:PORT
const fs=require('fs'),path=require('path');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
 const base=process.argv[2];if(!/^http:\/\/127\.0\.0\.1:\d+$/.test(base))throw Error('Isolated local fixture required');
 const output=path.resolve(__dirname,'../../server/build/exam-bank-qa');fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true,channel:'chrome'});const errors=[],checks=[];
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(12000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 const check=(value,message)=>{if(!value)throw Error(message);checks.push(message);};
 const shot=async name=>{await page.screenshot({path:path.join(output,name+'.png'),fullPage:true});check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),name+' no page overflow');};
 try{
  await page.goto(base);await page.locator('#username').fill('teacher');await page.locator('#password').fill('Teacher123!');await page.locator('.login-submit').click();
  await page.locator('.nav [data-action="eb-list"]').last().click();await page.locator('#eb-search').waitFor();await shot('01-list');
  await page.locator('#eb-search').fill('EXAM_A');await page.waitForTimeout(350);check((await page.locator('.exam-bank tbody tr').count())===1,'live exam search updates without Enter');await page.locator('#eb-search').fill('');await page.waitForTimeout(300);
  await page.locator('.nav [data-action="eb-new"]').click();await page.locator('#eb-f-title').fill('Bộ đề từ trình duyệt');await page.locator('#eb-f-code').fill('BROWSER_'+Date.now());await shot('02-information');
  await page.locator('[data-action="eb-next"]').click();await page.locator('[data-action="eb-source-all"]').click();await page.locator('#eb-question-search').fill('EB_B');check(await page.locator('[data-eb-question]').count()===1,'question source filters while typing');await page.locator('[data-eb-question]').check();await page.locator('#eb-question-search').fill('ARR001');await page.locator('[data-eb-question]').check();await shot('03-sources');
  await page.locator('[data-action="eb-next"]').click();await page.locator('[data-action="eb-distribute"]').click();check(await page.locator('#eb-score-total').innerText()==='100','points distributed to total');await shot('04-structure');
  await page.locator('[data-action="eb-next"]').click();await shot('05-grading');await page.locator('[data-eb-config="inherit"]').uncheck();await page.locator('[data-eb-config="checker"]').selectOption('TOKEN');await page.locator('[data-eb-config="showScore"]').uncheck();
  await page.locator('[data-action="eb-next"]').click();await shot('06-review');await page.locator('[data-action="eb-preview"]').click();await page.locator('#exam-bank-preview .preview').waitFor();await shot('08-preview');await page.locator('[data-action="eb-preview-question"]').nth(1).click();check((await page.locator('.statement h2').innerText()).includes('Câu 2.'),'preview switches questions');await page.keyboard.press('Escape');await page.locator('[data-action="eb-finish"]').click();await page.locator('.detail-banner').waitFor();await shot('07-detail');
  check((await page.locator('.detail-banner').innerText()).includes('Đã duyệt'),'browser saves and approves real definition');
  const approveDetail=await page.evaluate(async()=>{const {eb}=await import('/features/exam-bank/state.js');return {id:eb.exam.id,code:eb.exam.code};});
  await page.reload();await page.locator('.detail-banner').waitFor();check((await page.locator('.detail-banner').innerText()).includes(approveDetail.code),'detail restored after hard reload');
  await page.evaluate(async()=>{const {command}=await import('/core/api.js');await command('JOIN_ROOM',{roomId:'EXAM_BANK_TEST'});await new Promise(r=>setTimeout(r,300));const {loadCurrentRoom}=await import('/core/polling.js');await loadCurrentRoom(false);});
  await page.locator('[data-action="eb-schedule"]').click();await page.locator('#eb-schedule-title').waitFor();check(await page.locator('#eb-schedule-title').inputValue()==='Bộ đề từ trình duyệt','schedule modal renders all fields');await page.locator('[data-action="eb-schedule-confirm"]').click();await page.locator('.contest-card').first().waitFor();check(await page.locator('.contest-card').filter({hasText:'Bộ đề từ trình duyệt'}).count()>=1,'exam version creates actual classroom contest');
  await page.locator('.nav [data-action="eb-list"]').last().click();await page.locator('#eb-search').fill(approveDetail.code);await page.waitForTimeout(350);await page.locator('[data-eb-select]').check();
  const downloaded=page.waitForEvent('download');await page.locator('[data-action="eb-export-selected"]').click();const download=await downloaded;const zip=path.join(output,'roundtrip.zip');await download.saveAs(zip);check(fs.statSync(zip).size>0,'exports real ZIP package');
  await page.locator('.nav [data-action="eb-io"]').click();await page.locator('#eb-import-update').check();await page.locator('#eb-import-file').setInputFiles(zip);await page.locator('[data-action="eb-import-commit"]').waitFor();await shot('09-import-export');await page.locator('[data-action="eb-import-commit"]').click();await page.locator('#eb-search').waitFor();
  await page.locator('.nav [data-action="eb-versions"]').click();await page.locator('#eb-search').fill(approveDetail.code);await page.waitForTimeout(350);check(await page.locator('.exam-bank tbody tr').count()===2,'ZIP round trip creates draft version without overwrite');
  await page.setViewportSize({width:390,height:844});await shot('mobile');await page.setViewportSize({width:1440,height:1000});
  // Separate student browser context cannot see or use Teacher bank navigation.
  const student=await browser.newPage();await student.goto(base);await student.locator('#username').fill('student1');await student.locator('#password').fill('Student123!');await student.locator('.login-submit').click();await student.locator('.nav [data-page="overview"]').waitFor();check(await student.locator('.nav [data-action^="eb-"]').count()===0,'student sidebar excludes bank management');await student.close();
  check(errors.length===0,'no browser runtime errors');fs.writeFileSync(path.join(output,'report.json'),JSON.stringify({checks,errors},null,2));console.log('PASS: '+checks.length+' browser checks. '+output);
 }catch(e){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
