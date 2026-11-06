const path=require("path"),fs=require("fs");
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,"playwright"));
(async()=>{
 const browser=await chromium.launch({headless:true,channel:"chrome"});
 try{
  const tc=await browser.newContext({viewport:{width:1440,height:1000}}),sc=await browser.newContext({viewport:{width:1440,height:1000},permissions:["clipboard-read","clipboard-write"]});
  const teacher=await tc.newPage(),student=await sc.newPage(),errors=[];
  for(const p of [teacher,student]){p.setDefaultTimeout(15000);p.on("pageerror",e=>errors.push(e.message));}
  async function enter(p,user,password){await p.goto(process.argv[2]);await p.locator("#username").fill(user);await p.locator("#password").fill(password);await p.locator('#login-form [type="submit"]').click();await p.locator('.nav [data-page="overview"]').first().click();await p.locator('[data-action="join-room"][data-room-id="EXAM"]').click();await p.locator(".meeting-shell").waitFor();}
  await enter(teacher,"teacher","Teacher123!");if(await teacher.locator(".exam-monitor").count())throw Error("Work tab must not show monitoring");await teacher.locator('[data-action="classroom-view"][data-view="monitor"]').click();await enter(student,"student1","Student123!");
  const code=student.locator("#classroom-branch");await code.focus();await student.evaluate(()=>navigator.clipboard.writeText("BRANCH_CLIPBOARD"));await code.press("Control+V");await student.waitForFunction(()=>document.getElementById("classroom-branch").value.includes("BRANCH_CLIPBOARD"));
  await teacher.locator('[data-action="exam-toggle"][data-enabled="true"]').click();await student.locator("#exam-student-status:not([hidden])").waitFor();
  await code.focus();const before=await code.inputValue();await code.press("Control+V");if(await code.inputValue()!==before)throw Error("Branch paste was not blocked");
  await code.press("End");await code.press("q");await code.press("Control+Z");if(await code.inputValue()!==before)throw Error("Branch typing/Undo changed");
  await teacher.waitForFunction(()=>Array.from(document.querySelectorAll(".exam-monitor tbody tr")).some(r=>r.textContent.includes("student1")&&Number(r.cells[3]?.textContent)>0));
  await student.evaluate(()=>{Object.defineProperty(document,"hidden",{configurable:true,value:true});document.dispatchEvent(new Event("visibilitychange"));});
  await teacher.waitForFunction(()=>Array.from(document.querySelectorAll(".exam-monitor tbody tr")).some(r=>r.textContent.includes("student1")&&Number(r.cells[2]?.textContent)>0));
  await student.bringToFront();await student.evaluate(()=>{delete document.hidden;document.dispatchEvent(new Event("visibilitychange"));window.dispatchEvent(new Event("focus"));});
  await teacher.locator('[data-action="classroom-view"][data-view="grid"]').first().click();
  if(await teacher.locator(".exam-monitor").count())throw Error("Work tab still renders monitoring");
  await teacher.locator(".meeting-grid-panel:not([hidden])").waitFor();
  await teacher.locator('[data-action="classroom-view"][data-view="monitor"]').click();
  await teacher.waitForFunction(()=>Array.from(document.querySelectorAll(".exam-monitor tbody tr")).some(r=>r.textContent.includes("student1")&&Number(r.cells[3]?.textContent)>0));
  fs.mkdirSync("build/exam-browser-qa",{recursive:true});await teacher.screenshot({path:"build/exam-browser-qa/teacher-branch-monitor.png",fullPage:true});await student.screenshot({path:"build/exam-browser-qa/student-branch-monitor.png",fullPage:true});
  await teacher.locator('[data-action="exam-toggle"][data-enabled="false"]').click();await student.waitForFunction(()=>document.getElementById("exam-student-status")?.hidden===true);await code.focus();await code.press("Control+V");await student.waitForFunction(()=>document.getElementById("classroom-branch").value.split("BRANCH_CLIPBOARD").length>2);
  if(errors.length)throw Error(errors.join("; "));console.log("Branch browser PASS: normal paste, Teacher toggle, native clipboard block, typing/Undo, realtime counters, leave/return and disable.");
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack);process.exit(1);});
