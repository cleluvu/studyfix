// Real StudyFix backend and Chromium. Native keyboard clipboard; tab/blur signals are also dispatched deterministically.
const path=require('path'),fs=require('fs');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
  const base=process.argv[2],contestId=process.argv[3],output=path.resolve('build/exam-browser-qa');fs.mkdirSync(output,{recursive:true});
  const browser=await chromium.launch({headless:true,channel:'chrome'}),errors=[];
  try{
    const teacherContext=await browser.newContext({viewport:{width:1440,height:1000}}),studentContext=await browser.newContext({viewport:{width:1440,height:1000},permissions:['clipboard-read','clipboard-write']});
    const teacher=await teacherContext.newPage(),student=await studentContext.newPage();
    for(const page of [teacher,student]){page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(12000);}
    async function enter(page,username,password){
      await page.goto(base);await page.locator('#username').fill(username);await page.locator('#password').fill(password);await page.locator('#login-form [type="submit"]').click();
      await page.locator('.nav [data-page="overview"]').first().click();await page.locator('[data-action="join-room"][data-room-id="EXAM"]').click();await page.locator('.meeting-shell').waitFor();
      await page.locator('.meeting-brand').click();await page.locator('.nav [data-page="contests"]').click();await page.locator(`[data-action="select-contest"][data-contest-id="${contestId}"]`).click();
    }
    await enter(teacher,'teacher','Teacher123!');
    await teacher.locator('[data-action="exam-toggle"][data-enabled="false"]').click();
    await enter(student,'student1','Student123!');await student.locator('[data-action="open-contest-problem"]').first().click();
    const code=student.locator('#contest-code');await code.fill('');await code.focus();await student.evaluate(()=>navigator.clipboard.writeText('NORMAL_PASTE_ALLOWED'));
    await code.press('Control+V');await student.waitForFunction(()=>document.getElementById('contest-code').value.includes('NORMAL_PASTE_ALLOWED'));
    await teacher.locator('#exam-tab-limit').fill('1');await teacher.locator('#exam-paste-limit').fill('1');await teacher.locator('[data-action="exam-toggle"][data-enabled="true"]').click();
    await student.locator('#exam-student-status:not([hidden])').waitFor();
    await code.fill('class Main {}');await student.evaluate(()=>navigator.clipboard.writeText('CLIPBOARD_MUST_NOT_LEAK'));
    await code.focus();await code.press('Control+V');if(await code.inputValue()!=='class Main {}')throw Error('Ctrl+V inserted clipboard in exam mode');
    await teacher.waitForFunction(()=>Array.from(document.querySelectorAll('.exam-monitor tbody tr')).some(row=>row.textContent.includes('student1')&&Number(row.cells[3]?.textContent)>=1));
    // Right-click paste reaches the same browser paste event; no clipboard contents are read by the app.
    await student.waitForTimeout(650);await code.evaluate(el=>el.dispatchEvent(new ClipboardEvent('paste',{bubbles:true,cancelable:true})));
    const beforeTyping=await code.inputValue();await code.press('End');await code.pressSequentially('x');await code.press('Control+Z');
    if(await code.inputValue()!==beforeTyping)throw Error('Normal typing/undo was changed by exam guards');
    if((await code.inputValue()).includes('CLIPBOARD_MUST_NOT_LEAK'))throw Error('Clipboard appeared after undo');
    await student.waitForTimeout(650);await code.press('Shift+Insert');if((await code.inputValue()).includes('CLIPBOARD_MUST_NOT_LEAK'))throw Error('Shift+Insert was not blocked');
    await student.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});window.dispatchEvent(new Event('blur'));document.dispatchEvent(new Event('visibilitychange'));});await student.waitForTimeout(250);
    await teacher.waitForFunction(()=>Array.from(document.querySelectorAll('.exam-monitor tbody tr')).some(row=>row.textContent.includes('student1')&&Number(row.cells[2]?.textContent)>=1));
    await student.bringToFront();await student.evaluate(()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'));window.dispatchEvent(new Event('focus'));});
    await teacher.waitForFunction(()=>Array.from(document.querySelectorAll('.exam-monitor tbody tr')).some(row=>row.textContent.includes('student1')&&row.cells[1]?.textContent.includes('Đang thi')));
    await teacher.screenshot({path:path.join(output,'teacher-exam-monitor.png'),fullPage:true});await student.screenshot({path:path.join(output,'student-exam-status.png'),fullPage:true});
    await teacher.setViewportSize({width:390,height:844});
    await teacher.waitForFunction(()=>document.querySelector('.sidebar').getBoundingClientRect().right<1);
    if(await teacher.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Exam monitor overflows mobile viewport');
    await teacher.screenshot({path:path.join(output,'teacher-exam-mobile.png'),fullPage:true});await teacher.setViewportSize({width:1440,height:1000});
    await teacher.locator('[data-action="exam-history"]').first().click();await teacher.getByRole('heading',{name:'Lịch sử tín hiệu kiểm tra'}).waitFor();
    if((await teacher.locator('#active-modal').textContent()).includes('CLIPBOARD_MUST_NOT_LEAK'))throw Error('Clipboard content leaked to Teacher');await teacher.locator('#active-modal [data-action="close-modal"]').click();
    // Transient network loss retains paste policy and queues event metadata for retry.
    await studentContext.setOffline(true);await student.waitForTimeout(650);await code.focus();await code.press('Control+V');
    if((await code.inputValue()).includes('CLIPBOARD_MUST_NOT_LEAK'))throw Error('Offline mode allowed paste');
    await studentContext.setOffline(false);await student.waitForTimeout(1000);
    await student.reload();await student.locator(`[data-action="select-contest"][data-contest-id="${contestId}"]`).click();await student.locator('[data-action="open-contest-problem"]').first().click();await student.locator('#exam-student-status:not([hidden])').waitFor();
    await teacher.locator('[data-action="exam-toggle"][data-enabled="false"]').click();await student.waitForFunction(()=>document.getElementById('exam-student-status')?.hidden===true);
    await code.fill('');await code.focus();await code.press('Control+V');await student.waitForFunction(()=>document.getElementById('contest-code').value.includes('CLIPBOARD_MUST_NOT_LEAK'));
    const uploadResponse=student.waitForResponse(r=>r.url().endsWith("/api/assignments")&&r.request().postDataJSON()?.type==="CONTEST_SUBMIT");
    await student.locator("#contest-code-file").setInputFiles({name:"Main.java",mimeType:"text/plain",buffer:Buffer.from("public class Main { public static void main(String[] args) {} }")});
    const receipt=await uploadResponse;if(!receipt.ok()||(await receipt.json()).status!=="OK")throw Error("File upload did not submit through contest judge");
    // Teacher creates a random pool using the actual form; Student only receives a stable subset.
    await teacher.locator('[data-action="open-contest-create"]').click();
    await teacher.locator('[name="title"]').fill("Random UI test");
    await teacher.locator('[name="distributionMode"]').selectOption("RANDOM");
    await teacher.locator("#contest-pool-draw-count").fill("3");await teacher.locator('[data-action="contest-pick-random"]').click();
    await teacher.waitForFunction(()=>document.querySelectorAll('[data-contest-exercise]:checked').length===3);
    await teacher.locator('[name="problemsPerStudent"]').fill("2");
    const createdResponse=teacher.waitForResponse(r=>r.url().endsWith("/api/assignments")&&r.request().postDataJSON()?.type==="CONTEST_CREATE");
    await teacher.locator('#contest-form [type="submit"]').click();
    const created=await (await createdResponse).json();if(created.status!=="OK")throw Error("Random contest creation failed: "+JSON.stringify(created));
    const randomId=created.data.contestId;
    await student.locator('.nav [data-page="contests"]').click();await student.locator(`[data-action="select-contest"][data-contest-id="${randomId}"]`).click();
    await student.waitForFunction(()=>document.querySelectorAll('[data-action="open-contest-problem"]').length===2);
    if(await student.locator('[data-action="open-contest-problem"]').count()!==2)throw Error("Student did not receive exactly two random problems");
    const ids=await student.locator('[data-action="open-contest-problem"]').evaluateAll(nodes=>nodes.map(n=>n.dataset.exerciseId));
    await student.reload();await student.locator(`[data-action="select-contest"][data-contest-id="${randomId}"]`).click();
    await student.waitForFunction(()=>document.querySelectorAll('[data-action="open-contest-problem"]').length===2);
    const reloaded=await student.locator('[data-action="open-contest-problem"]').evaluateAll(nodes=>nodes.map(n=>n.dataset.exerciseId));
    if(JSON.stringify(ids)!==JSON.stringify(reloaded))throw Error("Refresh rerolled student problems");
    await teacher.locator(`[data-action="select-contest"][data-contest-id="${randomId}"]`).click();
    await teacher.waitForFunction(()=>document.querySelectorAll('[data-action="open-contest-problem"]').length===3);
    if(await teacher.locator('[data-action="open-contest-problem"]').count()!==3)throw Error("Teacher cannot see full pool");
    await teacher.screenshot({path:path.join(output,"teacher-random-contest.png"),fullPage:true});
    await student.screenshot({path:path.join(output,"student-random-contest.png"),fullPage:true});
    await teacher.locator('[data-action="open-contest-create"]').click();await teacher.locator('[name="title"]').fill("Targeted UI test");await teacher.locator('[name="distributionMode"]').selectOption("ALL");
    await teacher.locator("#contest-pool-draw-count").fill("3");await teacher.locator('[data-action="contest-pick-random"]').click();await teacher.waitForFunction(()=>document.querySelectorAll('[data-contest-exercise]:checked').length===3);
    const targetResponse=teacher.waitForResponse(r=>r.url().endsWith("/api/assignments")&&r.request().postDataJSON()?.type==="CONTEST_CREATE");await teacher.locator('#contest-form [type="submit"]').click();const targetId=(await (await targetResponse).json()).data.contestId;
    await teacher.locator(`[data-action="contest-assign-open"][data-contest-id="${targetId}"]`).click();
    await teacher.locator('#active-modal label.check-row').filter({hasText:"student1"}).locator('input[data-exam-recipient]').check();
    await teacher.locator("#contest-assign-count").fill("1");await teacher.locator('[data-action="contest-assign-random"]').click();
    if(await teacher.locator('#active-modal [data-exam-assigned-problem]:checked').count()!==1||await teacher.locator('#active-modal [data-exam-recipient]:checked').count()!==1)throw Error("Random choice did not preserve recipients and exact problem selection");
    await teacher.screenshot({path:path.join(output,"teacher-assign-contest.png"),fullPage:true});
    const assigned=teacher.waitForResponse(r=>r.url().endsWith("/api/assignments")&&r.request().postDataJSON()?.type==="CONTEST_DISTRIBUTE");await teacher.locator('[data-action="contest-assign-save"]').click();if((await (await assigned).json()).status!=="OK")throw Error("Targeted assignment failed");
    await student.locator('.nav [data-page="contests"]').click();await student.locator(`[data-action="select-contest"][data-contest-id="${targetId}"]`).click();await student.waitForFunction(()=>document.querySelectorAll('[data-action="open-contest-problem"]').length===1);
    console.log("Targeted contest browser PASS: random checkbox selection, recipient picker, exact chosen subset and Student view.");
    console.log("Random contest browser PASS: Teacher form, exact Student subset, reload stability and Teacher pool/allocations.");
    if(errors.length)throw Error(errors.join('; '));
    console.log('Exam browser PASS: normal paste, server toggle, native Ctrl+V/Shift+Insert blocked, paste event, undo, Teacher realtime/history, blur/focus, offline guard/reconnect and refresh. Native OS right-click menu and physical LAN not claimed.');
    await teacherContext.close();await studentContext.close();
  }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exit(1)});
