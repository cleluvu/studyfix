// Visual QA uses the isolated StudentPortalIntegrationTest --browser fixture.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(path.join(process.env.STUDYFIX_BROWSER_TEST_MODULES,'playwright'));
(async()=>{
 const root=path.resolve(__dirname,'../..'),f=JSON.parse(fs.readFileSync(path.join(root,'server/build-student-portal/fixture.json'))),out=path.join(root,'server/build-debug-lessons/student-refresh');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(token=>sessionStorage.setItem('studyfix.web.token',token),f.token);await page.goto(f.base);await page.locator('.sp-stats').waitFor();
  for(const name of ['overview','student-classes','student-ranking','exam-organization']){
   await page.evaluate(async name=>{await (await import('/core/polling.js')).navigate(name)},name);
   await page.locator('.sp-redesign').waitFor();
   if(name==='student-ranking')await page.evaluate(async()=>{
    const {portal}=await import('/features/student/portal.js');const {render}=await import('/ui/render.js');
    // Only fixture view data is enriched so the complete top-three layout can be inspected.
    portal.data.learning.ranking=[{rank:1,displayName:'Trần Minh Đức',accepted:86,points:8600},{rank:2,displayName:'Lê Thị Bích',accepted:82,points:8200},{rank:3,displayName:'Nguyễn Văn An',accepted:78,points:7800},{rank:4,displayName:'Nguyễn Minh Anh',accepted:75,points:7500,isMe:true},{rank:5,displayName:'Vũ Hoàng Mai',accepted:70,points:7000},{rank:6,displayName:'Đặng Quốc Bảo',accepted:68,points:6800}];portal.data.learning.myRank=portal.data.learning.ranking[3];portal.data.learning.rankTotal=32;render();
   });
   if(name==='exam-organization')await page.evaluate(async()=>{
    const {organization:o}=await import('/features/exam-organization/state.js');const {render}=await import('/ui/render.js');const now=Date.now();
    o.directory={page:1,totalPages:1,total:7,exams:Array.from({length:7},(_,i)=>({id:'QA_EXAM_'+i,code:'KT_JAVA_'+i,title:i===0?'Kiểm tra 15 phút – Buổi 3':i===1?'Thi giữa kỳ – Lập trình JAVA':'Lập trình JAVA '+(i-1),status:i===0?'SCHEDULED':i===1?'RUNNING':'FINISHED',startAt:now+(i===0?86400000:-3600000*i),endAt:now+(i===1?3360000:-3600000*(i-1)),classes:[{code:'JAVA-K23'}],access:'STUDENT'}))};render();
   });
   for(const width of [1440,1024,768,390]){
    await page.setViewportSize({width,height:1000});await page.waitForTimeout(180);
    if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)){await page.screenshot({path:path.join(out,"overflow.png"),fullPage:true});console.log(await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,elements:[...document.querySelectorAll("body *")].map(e=>({c:String(e.className),x:e.getBoundingClientRect().x,w:e.getBoundingClientRect().width,r:e.getBoundingClientRect().right})).filter(e=>e.r>innerWidth+1&&e.w>0).slice(0,12)})));throw Error(`${name} overflows at ${width}`);}
    if(width===1440||width===390)await page.screenshot({path:path.join(out,name+'-'+width+'.png'),fullPage:true});
   }
   console.log('Visual PASS:',name,'desktop/laptop/tablet/mobile');
  }
  assert.deepEqual(errors,[]);console.log('Student refresh Chrome PASS: four page layouts, actual routes, empty/populated displays and no horizontal page overflow.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});