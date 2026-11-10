// Run TeacherDashboardIntegrationTest --browser first. Uses only the isolated fixture server.
const fs=require('node:fs');
const assert=require('node:assert/strict');
const path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_PACKAGE||'playwright');
async function main(){
 const root=path.resolve(__dirname,'..'),fixture=JSON.parse(fs.readFileSync(path.join(root,'build-teacher-dashboard/fixture.json'),'utf8'));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const passed=[],errors=[];let page;
 try{
  page=await browser.newPage({viewport:{width:1672,height:941}});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(fixture.base);
  await page.locator('#username').fill('dashboardteacher');await page.locator('#password').fill('Teacher12345!');await page.locator('#login-form [type="submit"]').click();
  await page.locator('.td-stats').waitFor();await page.evaluate(async()=>{window.__dashboardTest={state:(await import('/core/state.js')).state,dashboard:(await import('/features/admin/teacher-dashboard.js')).dashboard,organization:(await import('/features/exam-organization/state.js')).organization}});assert.equal(await page.locator('.td-stat').count(),5);passed.push('five live metric cards');
  await page.locator('#toast-region').evaluate(e=>e.innerHTML='');
  for(const width of [1672,1024,768,390]){
   await page.setViewportSize({width,height:941});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`horizontal overflow at ${width}`);
  }
  passed.push('desktop, laptop, tablet and phone without page overflow');
  await page.setViewportSize({width:1672,height:941});
  await page.screenshot({path:path.join(root,'build-teacher-dashboard/dashboard-desktop.png'),fullPage:true});
  await page.locator('[data-action="td-verdict"][data-verdict="AC"]').click();await page.locator('.td-filters').waitFor();assert.equal(await page.locator('.td-table tbody tr').count(),13);passed.push('AC drilldown matches aggregate');
  await page.locator('[data-td-filter="verdict"]').selectOption('');await page.waitForFunction(()=>document.querySelectorAll('.td-table tbody tr').length===20);
  await page.locator('[data-action="td-page"][data-page="2"]').click();await page.waitForFunction(()=>document.querySelectorAll('.td-table tbody tr').length===5);passed.push('submission pagination');
  await page.locator('[data-page="admin-settings"]').first().click();await page.locator('#td-settings-form').waitFor();await page.locator('#td-settings-form select').selectOption('7');await page.locator('#td-settings-form [name="compact"]').check();await page.locator('#td-settings-form [type="submit"]').click();await page.getByText('Đã lưu cài đặt.',{exact:true}).waitFor();passed.push('settings save through SQLite API');
  await page.locator('[data-action="td-notifications"]').click();await page.locator('[data-action="td-read"]').waitFor();await page.locator('[data-action="td-read"]').click();await page.waitForFunction(()=>document.querySelector('[data-action="td-notifications"]')?.getAttribute('aria-label')==='Thông báo');passed.push('notification read marker');
  await page.locator('[data-page="admin"]').first().click();await page.locator('.td-stats').waitFor();
  const initialClasses=Number(await page.locator('.td-stat strong').first().textContent());
  await page.locator('[data-action="td-create-class"]').click();await page.locator('#admin-dialog-form [name="name"]').fill('Lớp kiểm thử trình duyệt');await page.locator('#admin-dialog-form [name="roomCode"]').fill('BROWSER-'+Date.now().toString().slice(-7));await page.locator('#admin-dialog-form [type="submit"]').click();await page.waitForFunction(n=>Number(document.querySelector('.td-stat strong')?.textContent)===n,initialClasses+1);passed.push('create class quick action');
  await page.locator('.td-quick-grid [data-action="td-rooms"]').click();const activeRoom=await page.evaluate(async()=>{const {state}=await import('/core/state.js');return state.room?.roomId||'';});await page.locator('#active-modal [data-action="td-enter"]'+(activeRoom?'[data-id="'+activeRoom+'"]':'')).first().click();await page.waitForFunction(()=>window.__dashboardTest.state.page==='room'&&!window.__dashboardTest.dashboard.actionBusy);passed.push('open classroom quick action');
  await page.evaluate(async()=>{const {navigate}=await import('/core/polling.js');await navigate('admin');});
  await page.locator('[data-action="td-assign"]').click();const workRoom=await page.evaluate(async()=>{const {state}=await import('/core/state.js');return state.room?.roomId||'';});await page.locator('#active-modal [data-action="td-work"]'+(workRoom?'[data-id="'+workRoom+'"]':'')).first().click();await page.waitForFunction(()=>!!document.querySelector('#catalog-query')&&!window.__dashboardTest.dashboard.actionBusy);passed.push('assign exercises opens classroom catalog');
  await page.evaluate(async()=>{const {navigate}=await import('/core/polling.js');await navigate('admin');});
  await page.locator('[data-action="td-create-exam"]').click();await page.waitForFunction(()=>window.__dashboardTest.organization.view==='create'&&!window.__dashboardTest.dashboard.actionBusy);passed.push('create exam wizard quick action');
  await page.evaluate(async()=>{const {navigate}=await import('/core/polling.js');await navigate('admin');});
  await page.route('**/api/teacher/dashboard?**',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({ok:false,message:'Browser test: temporary failure'})}));
  await page.locator('[data-action="td-refresh"]').last().click();await page.getByRole('alert').filter({hasText:'temporary failure'}).waitFor();assert.equal(await page.locator('.td-stat').count(),5);await page.unroute('**/api/teacher/dashboard?**');await page.getByRole('button',{name:'Thử lại',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('.td-dashboard [role="alert"]'));passed.push('failed refresh preserves data and retry recovers');
  await page.setViewportSize({width:390,height:844});await page.locator('#toast-region').evaluate(e=>e.innerHTML='');await page.screenshot({path:path.join(root,'build-teacher-dashboard/dashboard-mobile.png'),fullPage:true});
  assert.deepEqual(errors,[]);passed.push('no uncaught browser errors');
  fs.writeFileSync(path.join(root,'build-teacher-dashboard/browser-results.json'),JSON.stringify({passed},null,2));console.log(passed.map(s=>'PASS '+s).join('\n'));
 }catch(e){console.error('DIAGNOSTIC',await page.evaluate(async()=>{const {state}=await import('/core/state.js');const {dashboard}=await import('/features/admin/teacher-dashboard.js');return {page:state.page,room:state.room?.roomId,mode:state.exerciseMode,actionBusy:dashboard.actionBusy,busy:dashboard.busy,headers:[...document.querySelectorAll('h2')].map(e=>e.innerText),modal:document.querySelector('#active-modal')?.innerText,toasts:document.querySelector('#toast-region')?.innerText,buttons:[...document.querySelectorAll('[data-action^="td-"]')].map(e=>({action:e.dataset.action,disabled:e.disabled}))}}));throw e;}finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
