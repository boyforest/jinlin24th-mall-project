/* Local contract fixtures, no production marketing data is changed. */
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.UX_BASE_URL || 'http://127.0.0.1:5184', screenshots = process.env.UX_SCREENSHOT_DIR
let browser, passed = 0
const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="500" height="420"><rect width="500" height="420" fill="#dfe8d2"/><circle cx="250" cy="210" r="110" fill="#f8f3e7"/><path d="M200 250 Q250 100 320 180 Q270 270 200 250" fill="#79945c"/></svg>'
const record = (i, position='home_recommend') => ({ id:i,title:`推荐养护 ${i}`,subtitle:'顺时温养 · 日常的一份陪伴',imageUrl:'http://127.0.0.1:7878/fixture.svg',position,linkType:'page',linkValue:'/pages/coffee/index',status:1,sort:i })
async function scenario(count,fail='',popup=false) {
  const context = await browser.newContext({viewport:{width:390,height:1500},timezoneId:'Asia/Shanghai'})
  await context.addInitScript(show => { if(!show) localStorage.setItem('popup_login_shown','1') },popup)
  const calls=[]
  const rows=Array.from({length:count},(_,i)=>record(i+1))
  // Disabled and expired records must be excluded by the existing public API rules.
  const all=[...rows,{...record(90),status:0},{...record(91),endTime:'2020-01-01T00:00:00+08:00'},{...record(92),startTime:'2099-01-01T00:00:00+08:00'}]
  await context.route('http://127.0.0.1:7878/**',route => {
    const u=new URL(route.request().url()),pos=u.searchParams.get('position'); calls.push({path:u.pathname,position:pos})
    if(u.pathname.endsWith('.svg'))return route.fulfill({contentType:'image/svg+xml',body:svg})
    const reply=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status===200?0:status,message:status===200?'ok':'位置读取失败夹具',data})})
    if(pos===fail)return reply(null,503)
    if(pos==='home_recommend')return reply(all.filter(r=>r.status===1&&(!r.startTime||new Date(r.startTime)<new Date())&&(!r.endTime||new Date(r.endTime)>new Date())).sort((a,b)=>b.sort-a.sort||b.id-a.id))
    if(pos==='home_banner')return reply([{...record(101,'home_banner'),title:'当季唯一主推'},{...record(102,'home_banner'),title:'后台保留的第二张主推'}])
    if(pos==='home_notice')return reply([{...record(103,'home_notice'),title:'原有首页公告'}])
    if(pos==='login_popup')return reply([{...record(104,'login_popup'),title:'原有登录弹窗'}])
    if(u.pathname==='/user/coffee/store')return reply(null,404)
    return reply([])
  })
  const page=await context.newPage(), errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000)
  await page.goto(`${origin}/#/pages/home/index`);await page.locator('.business-entries').waitFor();await page.waitForTimeout(250)
  return {context,page,calls,errors}
}
async function shot(page,name){if(screenshots){fs.mkdirSync(screenshots,{recursive:true});await page.screenshot({path:path.join(screenshots,`${name}.png`),fullPage:true})}}
;(async()=>{
  browser=await chromium.launch({channel:'chrome',headless:true})
  for(const count of [0,1,2,3,5,6,7]){
    const {context,page,calls,errors}=await scenario(count)
    try{
      const n=Math.min(count,6);assert.equal(await page.locator('.recommend-item').count(),n)
      assert.equal(await page.locator('.recommend-heading').count(),n?1:0)
      assert.equal(await page.locator('.activity-card').count(),1);assert.equal(await page.getByText('后台保留的第二张主推',{exact:true}).count(),0)
      if(n){assert.equal((await page.locator('.recommend-title').first().innerText()).trim(),`推荐养护 ${count}`);const first=await page.locator('.recommend-item').first().boundingBox(),last=await page.locator('.recommend-item').last().boundingBox();assert(Math.abs(first.width-last.width)<1);if(n%2)assert(Math.abs(first.x-last.x)<1);assert.equal(await page.locator('.recommend-item').getByText(/¥/).count(),0)}
      assert(calls.some(c=>c.position==='home_recommend'));assert.deepEqual(errors,[])
      if([3,6].includes(count))await shot(page,`home-recommend-${count}-fixture`)
      if(count===1){await page.locator('.recommend-item').click();await page.waitForURL('**/pages/coffee/index')}
      console.log(`PASS ${++passed}: 推荐${count}项、有效期排序、单张主推与单双列布局`)
    }finally{await context.close()}
  }
  for(const fail of ['home_banner','home_recommend','home_notice','login_popup']){
    const {context,page,errors}=await scenario(3,fail,true)
    try{
      assert.equal(await page.locator('.business-entry').count(),2)
      assert.equal(await page.locator('.activity-card').count(),fail==='home_banner'?0:1)
      assert.equal(await page.locator('.recommend-item').count(),fail==='home_recommend'?0:3)
      assert.equal(await page.locator('.notice-strip').count(),fail==='home_notice'?0:1)
      assert.equal(await page.locator('.popup-title').count(),fail==='login_popup'?0:1)
      if(fail!=='login_popup')await page.locator('.popup-close').click()
      assert.deepEqual(errors,[]);console.log(`PASS ${++passed}: ${fail}失败独立收起，公告弹窗及业务入口保留`)
    }finally{await context.close()}
  }
  {const {context,page}=await scenario(1);try{await page.evaluate(()=>{window.uni.navigateTo({url:'/pages/goods/index'})});await page.waitForURL('**/pages/goods/index');await page.goBack();await page.locator('.recommend-item').waitFor();console.log(`PASS ${++passed}: 首页商品入口与返回保留运营内容`)}finally{await context.close()}}
  console.log(`Home marketing UX: ${passed} scenarios passed`)
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{await browser?.close()})
