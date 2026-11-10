// Chrome layout and interaction verification with explicit catalog HTTP fixtures.
const fs=require("fs"),path=require("path"),http=require("http");
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,"playwright"));
(async()=>{
 const root=path.resolve(__dirname,"../static"),output=path.resolve(__dirname,"../../server/build/question-bank-qa");fs.mkdirSync(output,{recursive:true});
 const html=fs.readFileSync(path.join(root,"index.html"),"utf8").replace('<script type="module" src="/app.js"></script>',`<script type="module">
 import '/core/events.js';import {state} from '/core/state.js';import {registerPage} from '/core/router.js';import {render} from '/ui/render.js';import {loadPractice,renderPractice} from '/features/exercises/practice.js';
 Object.assign(state,{token:'fixture-token',identity:{userId:'teacher-fixture',role:'TEACHER',displayName:'Giảng viên'},room:null,page:'exercises',exerciseMode:'practice',booting:false});registerPage('exercises',renderPractice);await loadPractice();render();
 </script>`);
 const server=http.createServer((req,res)=>{if(req.url==="/"){res.setHeader("Content-Type","text/html; charset=utf-8");res.end(html);return;}const file=path.resolve(root,"."+new URL(req.url,"http://localhost").pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return;}res.setHeader("Content-Type",file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":"text/plain");res.end(fs.readFileSync(file));});await new Promise(r=>server.listen(0,"127.0.0.1",r));let browser;
 try{
  browser=await chromium.launch({headless:true,channel:"chrome"});const page=await browser.newPage({viewport:{width:1672,height:941}}),errors=[];page.on("pageerror",e=>errors.push(e.message));page.setDefaultTimeout(10000);page.on('dialog',dialog=>dialog.accept());
  const names=["Tính tổng mảng","Đảo ngược chuỗi","Số nguyên tố","Sắp xếp tăng dần","Đếm từ","Tổng số chẵn","Tìm phần tử lớn nhất","Kiểm tra palindrome","Dãy Fibonacci","Đếm số dương"];
  const items=Array.from({length:24},(_,i)=>({id:(i%3===0?"ARR":"STR")+String(i+1).padStart(3,"0"),title:names[i%names.length],topic:i%3===0?"Mảng":"Chuỗi",mode:i%4===3?"DEBUG_COLLAB":"BUILD_FROM_SCRATCH",difficulty:i%3===0?"Dễ":i%3===1?"Trung bình":"Khó",practiceStatus:i%6===0?"AC":i%6===1?"FAILED":"NEW",template:"public class Main {}",statement:"Đề bài kiểm thử giao diện",inputFormat:"n",outputFormat:"result",constraints:"n < 100",revision:1,publication:i===23?"DRAFT":"PUBLISHED",canManage:true,usageCount:i%4,tests:[{input:"1",expectedOutput:"1",weight:1,hidden:false}],publicTests:[{input:"1",expectedOutput:"1"}]}));
  // Explicit UI fixtures only: production never seeds these weeks or counts.
  const titles=["Nhập môn Java, cài đặt môi trường","Cấu trúc điều khiển","Mảng một chiều","Phương thức","Lớp và đối tượng","Kế thừa","Đa hình","Xử lý ngoại lệ"];
  let weeks=titles.map((title,i)=>({id:`week-${i+1}`,weekNo:i+1,title,description:i===2?"Khai báo, duyệt mảng, tìm kiếm, sắp xếp và các bài toán cơ bản với mảng.":title,isMine:true,exerciseIds:items.slice(0,i===2?10:6+i%3).map(e=>e.id)}));
  await page.route("**/api/assignments",async route=>{const b=route.request().postDataJSON();let data;
   if(b.type==="PRACTICE_LIST"||b.type==="BANK_LIST"){const visible=items.filter(e=>!e.archived&&!e.deleted&&(b.type==="BANK_LIST"||e.publication==="PUBLISHED"));const pool=b.weekId?visible.filter(e=>weeks.find(w=>w.id===b.weekId)?.exerciseIds.includes(e.id)):visible;const filtered=pool.filter(e=>(!b.topic||e.topic===b.topic)&&(!b.query||`${e.id} ${e.title}`.toLowerCase().includes(b.query.toLowerCase()))&&(!b.mode||e.mode===b.mode)&&(!b.difficulty||e.difficulty===b.difficulty)&&(!b.filterStatus||e.practiceStatus===b.filterStatus));const n=b.page||1;data={exercises:filtered.slice((n-1)*10,n*10),topics:["Mảng","Chuỗi"],stats:{published:pool.filter(e=>e.publication==="PUBLISHED").length,drafts:pool.filter(e=>e.publication==="DRAFT").length,used:pool.filter(e=>e.usageCount>0).length,total:pool.length,accepted:pool.filter(e=>e.practiceStatus==="AC").length,failed:pool.filter(e=>e.practiceStatus==="FAILED").length,fresh:pool.filter(e=>e.practiceStatus==="NEW").length},page:n,total:filtered.length,totalPages:Math.max(1,Math.ceil(filtered.length/10))};}
   else if(b.type==="PRACTICE_WEEK_LIST")data={weeks:weeks.map(w=>({...w,exerciseCount:w.exerciseIds.length}))};
   else if(b.type==="PRACTICE_WEEK_SAVE"){let w=weeks.find(w=>w.id===b.weekId);if(w)Object.assign(w,b);else{w={...b,id:`week-${b.weekNo}`,exerciseIds:[],isMine:true};weeks.push(w);}data={weekId:w.id};}
   else if(b.type==="PRACTICE_WEEK_ADD"){const w=weeks.find(w=>w.id===b.weekId);w.exerciseIds=[...new Set([...w.exerciseIds,...b.exerciseIds])];data={};}
   else if(b.type==="PRACTICE_WEEK_REMOVE"){const w=weeks.find(w=>w.id===b.weekId);w.exerciseIds=w.exerciseIds.filter(id=>!b.exerciseIds.includes(id));data={};}
   else if(b.type==="PRACTICE_WEEK_DELETE"){weeks=weeks.filter(w=>w.id!==b.weekId);data={};}
   else if(b.type==="BANK_GET")data={exercise:items.find(e=>e.id===b.exerciseId)};
   else if(b.type==="CATALOG_CREATE"){const e={...b,revision:1,canManage:true,usageCount:0,publicTests:b.tests.filter(t=>!t.hidden)};items.push(e);data={exercise:e};}
   else if(b.type==="BANK_UPDATE"){const old=items.find(e=>e.id===b.id);old.archived=true;const e={...old,...b,id:old.id+'_v2',displayCode:old.id,archived:false,revision:old.revision+1,publicTests:b.tests.filter(t=>!t.hidden)};items.push(e);data={exercise:e};}
   else if(b.type==="BANK_DELETE"){items.find(e=>e.id===b.exerciseId).deleted=true;data={};}
   else if(b.type==="PRACTICE_GET")data={exercise:items.find(e=>e.id===b.exerciseId)};
   else if(b.type==="PRACTICE_SUBMISSIONS")data={submissions:[],total:0,page:1,totalPages:1};else throw Error("Unexpected API: "+b.type);
   await route.fulfill({json:{status:"OK",data}});
  });
  await page.goto(`http://127.0.0.1:${server.address().port}`);await page.getByRole("heading",{name:"Tất cả bài tập",exact:true}).waitFor();
  if(await page.locator('.nav [data-page="exercises"]').count()!==1)throw Error("Bank navigation duplicated");if(await page.locator('.bank-subnav button').count()!==2||(await page.locator('.bank-subnav').textContent()).includes('Category'))throw Error("Expected exactly All/Week subnavigation");
  if(await page.locator('.bank-all-table th').count()!==8||(await page.locator('.bank-all-table thead').textContent()).includes('Loại bài'))throw Error('All bank must omit exercise-mode column');
  if(await page.locator('.template-topbar').count()!==1)throw Error('Duplicate header');
  await page.locator('.template-account summary').click();if(!await page.locator('.template-account-menu [data-action="logout"]').isVisible())throw Error('Account menu must preserve logout');await page.locator('.template-account summary').click();
  if(await page.locator('#practice-status,.practice-ac,.practice-failed').count())throw Error('Teacher management must not show personal progress');
  await page.locator('input[data-action="practice-select"]').first().check();
  if(!await page.locator('[data-action="practice-bank-assign"]').isEnabled())throw Error("Single question selection cannot enable assignment");
  if(await page.locator(".bank-category-panel,.bank-tabs").count())throw Error("Old two-column/Week layout remains");
  if(await page.locator(".bank-summary-card").count()!==4)throw Error("Expected four summary cards above the table");
  await page.screenshot({path:path.join(output,"teacher-bank-desktop.png"),fullPage:true});
  await page.locator('[data-action="practice-page"][data-page="2"]').click();await page.waitForFunction(()=>document.querySelector('.bank-management-row')?.dataset.id==="STR011");
  await page.locator("#practice-topic").selectOption("Mảng");await page.waitForFunction(()=>document.getElementById("practice-catalog-count").textContent==="8 câu hỏi");
  const input=page.locator("#practice-query");await input.pressSequentially("ARR001");await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===1);if(await input.inputValue()!=="ARR001")throw Error("Search lost input");
  await page.locator('[data-action="practice-bank-view"]').last().click();await page.locator('#bank-preview-close').waitFor();if(await page.locator('#work-code').count())throw Error('Teacher preview opened editor');await page.locator('#bank-preview-close').click();
  await page.locator('[data-action="practice-create"]').click();await page.locator("#catalog-create-form").waitFor();await page.locator("#catalog-close").click();
  await page.locator('[data-action="practice-create"]').click();
  for(const [name,value] of Object.entries({id:'BANK_UI_NEW',title:'UI created question',topic:'Arrays',statement:'Read n',inputFormat:'n',outputFormat:'n'}))await page.locator(`#catalog-create-form [name="${name}"]`).fill(value);
  await page.locator('#catalog-create-form [name="testInput"]').fill('1');await page.locator('#catalog-create-form [name="testExpected"]').fill('1');await page.locator('#catalog-create-form [type="submit"]').click();await page.waitForFunction(()=>document.querySelector('.bank-management-row')?.dataset.id==='BANK_UI_NEW');
  await page.locator('[data-action="practice-bank-edit"]').click();await page.locator('#catalog-create-form [name="title"]').fill('UI edited question');await page.locator('#catalog-create-form [name="publication"]').selectOption('DRAFT');await page.locator('#catalog-create-form [type="submit"]').click();await page.waitForFunction(()=>document.querySelector('.bank-management-row')?.dataset.id==='BANK_UI_NEW_v2');
  await page.locator('[data-action="practice-bank-delete"]').click();await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===0);
  await page.locator('[data-action="practice-view"][data-view="weeks"]').click();await page.locator('.week-item').first().waitFor();
  await page.locator('[data-action="practice-week-select"][data-id="week-3"]').click();await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===10);
  if(!await page.locator('.week-detail-heading').textContent().then(s=>s.includes('Mảng một chiều')))throw Error('Week header mismatch');
  if(await page.locator('.week-table th').count()!==7||!(await page.locator('.week-table thead').textContent()).includes('Dạng bài'))throw Error('Week must use topic as problem category');if((await page.locator('.week-table .bank-category').first().textContent())!==items[0].topic)throw Error('Week category must come from database topic, not exercise mode');await page.evaluate(()=>document.getElementById('toast-region')?.replaceChildren());await page.screenshot({path:path.join(output,'teacher-week-desktop.png'),fullPage:true});
  await page.locator('[data-action="practice-week-tab"][data-tab="info"]').click();await page.locator('.week-info').waitFor();
  await page.locator('[data-action="practice-week-tab"][data-tab="stats"]').click();if(await page.locator('.week-stat').count()!==4)throw Error('Week stats missing');
  await page.locator('[data-action="practice-week-tab"][data-tab="list"]').click();
  await page.locator('#practice-query').pressSequentially('ARR001');await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===1);
  await page.locator('#practice-query').fill('');await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===10);
  await page.locator('[data-action="practice-week-new"]').click();await page.locator('#week-editor [name="title"]').fill('Tuần thực hành bổ sung');await page.locator('#week-editor [name="description"]').fill('Nội dung kiểm thử lưu Week');await page.locator('#week-editor [type="submit"]').click();await page.waitForFunction(()=>document.querySelector('.week-detail-heading h3')?.textContent.includes('Tuần thực hành bổ sung'));
  await page.locator('[data-action="practice-week-add"]').click();await page.locator('[data-week-pick="ARR001"]').check();await page.locator('#week-picker-save').click();await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===1);
  await page.locator('[data-action="practice-week-remove"]').click();await page.waitForFunction(()=>document.querySelectorAll('.bank-management-row').length===0);
  await page.locator('[data-action="practice-week-manage"]').click();await page.locator('[data-week-edit="week-9"]').click();await page.locator('#week-editor [name="title"]').fill('Week đã sửa');await page.locator('#week-editor [type="submit"]').click();await page.waitForFunction(()=>document.querySelector('.week-detail-heading h3')?.textContent.includes('Week đã sửa'));
  await page.locator('[data-action="practice-week-manage"]').click();await page.locator('[data-week-delete="week-9"]').click();await page.waitForFunction(()=>document.querySelectorAll('.week-item').length===8);
  await page.locator('[data-action="practice-week-select"][data-id="week-3"]').click();
  await page.evaluate(()=>document.getElementById('toast-region')?.replaceChildren());await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.querySelector('.sidebar').getBoundingClientRect().right<1);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Week overflows mobile');await page.screenshot({path:path.join(output,'teacher-week-mobile.png'),fullPage:true});await page.setViewportSize({width:1672,height:941});
  await page.evaluate(async()=>{const {state}=await import('/core/state.js'),{loadPractice}=await import('/features/exercises/practice.js'),{render}=await import('/ui/render.js');state.identity={userId:'student-fixture',role:'STUDENT',displayName:'Sinh viên'};await loadPractice();render();});
  if(await page.locator('[data-action="practice-week-new"],[data-action="practice-week-manage"],[data-action="practice-week-add"],[data-action="practice-week-remove"]').count())throw Error('Student sees Week management');
  await page.locator('[data-action="practice-view"][data-view="all"]').click();await page.locator('.bank-summary-cards').waitFor();
  if(await page.locator('[data-action="practice-create"]').count())throw Error("Student can create questions");
  if(await page.locator('[data-action="practice-bank-view"],[data-action="practice-bank-edit"],[data-action="practice-bank-delete"]').count())throw Error('Student sees management actions');
  await page.locator('.practice-ac').first().waitFor();await page.locator('.practice-failed').first().waitFor();
  await page.locator('[data-action="practice-open"]').last().click();await page.locator('#work-code').waitFor();await page.getByRole('heading',{name:'Lịch sử nộp của bạn'}).waitFor();await page.locator('[data-action="practice-back"]').click();

  await page.screenshot({path:path.join(output,"student-bank-desktop.png"),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.querySelector('.sidebar').getBoundingClientRect().right<1);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error("Bank overflows mobile viewport");await page.screenshot({path:path.join(output,"student-bank-mobile.png"),fullPage:true});
  await page.setViewportSize({width:1672,height:941});
  await page.evaluate(async()=>{
   const {state}=await import('/core/state.js'),{registerPage}=await import('/core/router.js'),{render}=await import('/ui/render.js'),{renderTeacherAdmin}=await import('/features/admin/admin-view.js');
   const data={enabled:true,users:[{userId:'s1',username:'student',studentCode:'SV001',displayName:'Student',active:true,onlineConnections:1}],classes:[{classId:'c1',roomCode:'LAN-A',name:'Class A',active:true,memberCount:1}],members:[],connections:[],attendance:[],joinRequests:[],recentActivity:[],pages:{},stats:{classes:1,students:1,online:1,offline:0}};
   state.identity={userId:'teacher-fixture',role:'TEACHER',displayName:'Teacher'};state.page='admin';registerPage('admin',()=>renderTeacherAdmin(data,state));registerPage('admin-students',()=>renderTeacherAdmin(data,state));render();
  });
  if(await page.locator('.template-topbar').count()!==1)throw Error('Global template must have one header');await page.screenshot({path:path.join(output,'teacher-system-template.png'),fullPage:true});
  await page.evaluate(async()=>{const {state}=await import('/core/state.js'),{render}=await import('/ui/render.js');state.page='admin-students';render();});await page.screenshot({path:path.join(output,'teacher-students-template.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.querySelector('.sidebar').getBoundingClientRect().right<1);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Shared template overflows mobile');await page.screenshot({path:path.join(output,'teacher-students-mobile.png'),fullPage:true});await page.locator('[data-action="toggle-nav"]').click();await page.waitForFunction(()=>document.querySelector('.sidebar').getBoundingClientRect().left>=0);if(!await page.locator('.bank-subnav [data-view="weeks"]').isVisible())throw Error('Mobile navigation lost Week');
  if(errors.length)throw Error(errors.join("; "));console.log("Question bank Chrome PASS: Teacher/Student roles, All/Week navigation, Week CRUD and picker, scoped search, tabs, role controls, category, pagination, AC/failed colors, open/back, shared Admin shell/account menu and mobile navigation. Catalog HTTP fixtures, no real grading claim.");
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e.stack);process.exit(1);});
