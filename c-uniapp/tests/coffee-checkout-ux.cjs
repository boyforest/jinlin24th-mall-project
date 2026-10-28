// 本地契约夹具，不连接真实微信支付。平台 success 只模拟回调，不替代服务端到账事实。
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.UX_BASE_URL || 'http://127.0.0.1:5194'
const api = 'http://127.0.0.1:7878'
const store = { id: '1', name: '本地测试咖啡店', address: '测试路一号', openingHours: '09:00—18:00', timeZone: 'Asia/Shanghai', businessStatus: 'OPEN', announcement: null, capabilities: { browse: true, addToCart: true, placeOrder: true, payOnline: true }, orderUnavailableReason: null }
const sku = { id: '11', productId: '2', name: '大杯', temperature: 'HOT', sugar: 'LESS', priceCent: 1800, available: true, unavailableReason: null }
const menu = { storeId: '1', version: 'menu-v1', categories: [{ id: '3', name: '咖啡', sort: 0 }], products: [{ id: '2', categoryId: '3', name: '测试桂花拿铁', imageUrl: null, description: '测试饮品', sort: 0, skus: [sku] }] }
const rows = [{ id: '21', storeId: '1', productId: '2', skuId: '11', productName: '旧篮内名字', skuName: '大杯', imageUrl: null, temperature: 'HOT', sugar: 'LESS', unitPriceCent: 1600, quantity: 2, checked: 1, available: true, unavailableReason: null, version: 4 }, { id: '22', storeId: '1', productId: '2', skuId: '11', productName: '未勾选条目', skuName: '大杯', imageUrl: null, temperature: 'HOT', sugar: 'LESS', unitPriceCent: 1800, quantity: 1, checked: 0, available: true, unavailableReason: null, version: 0 }]
function order() { return { id: '9007199254741001', orderNo: 'a'.repeat(32), storeId: '1', storeName: store.name, pickupCode: '0102', pickupDate: '2026-10-28', fulfillmentStatus: 'WAIT_PAYMENT', paymentStatus: 'UNPAID', refundStatus: 'NONE', refundReason: null, remark: null, createdAt: '2026-10-28T10:00:00+08:00', paidAt: null, itemCount: 2, items: [{ productId: '2', skuId: '11', productName: '下单快照桂花拿铁', specName: '大杯', imageUrl: null, temperature: 'HOT', sugar: 'LESS', unitPriceCent: 1800, quantity: 2, lineTotalCent: 3600 }], amounts: { totalAmountCent: 3600, payableAmountCent: 3600, paidAmountCent: null, refundedAmountCent: 0 }, notificationStatus: 'NONE' } }
let browser, passed = 0
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function fixture(auth = true) {
 const context = await browser.newContext({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai' })
 await context.addInitScript(auth => {
  localStorage.setItem('popup_login_shown','1')
  if (auth) { localStorage.setItem('jl_user_token','test-user-A'); localStorage.setItem('jl_user_id','10') }
 }, auth)
 const state = { store: structuredClone(store), menu: structuredClone(menu), rows: structuredClone(rows), order: order(), creates: [], pays: 0, cancels: 0, reads: 0, updates: [], createTimeout: false, createConflict: false, createDelay: 0, detailError: false, requests: [] }
 await context.route(`${api}/**`,async route => {
  const req=route.request(),url=new URL(req.url()),p=url.pathname,body=req.postDataJSON() || {}
  state.requests.push(p)
  const reply=(data,status=200,code=0,message='ok')=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code,message,data})})
  if(p==='/user/coffee/store')return reply(state.store)
  if(p==='/user/coffee/menu')return reply(state.menu)
  if(p==='/user/coffee/cart/list')return reply(state.rows)
  if(p.startsWith('/user/coffee/cart/')) { state.updates.push(body); const row=state.rows.find(row=>row.id===p.split('/').pop()); if(row && row.version===body.expectedVersion){row.checked=body.checked;row.version++} return reply(row || true) }
  if(p==='/user/coffee/orders' && req.method()==='POST') {
   state.creates.push(body); await delay(state.createDelay)
   if(state.createConflict)return reply(null,409,92411,'菜单已变化，请重新核对')
   if(state.createTimeout){state.createTimeout=false;return route.abort('timedout')}
   return reply(state.order)
  }
  if(p.endsWith('/payment')) {state.pays++;return reply({ appId:'test-app',timeStamp:'1',nonceStr:'test',packageValue:'prepay_id=test',signType:'RSA',paySign:'test' })}
  if(p.endsWith('/cancel')) {state.cancels++;state.order.fulfillmentStatus='CANCELLED';return reply(state.order)}
  if(p.startsWith('/user/coffee/orders/')){state.reads++;return state.detailError?reply(null,500,50000,'暂时加载失败'):reply(state.order)}
  if(p==='/user/business-order/list')return reply({records:[{...state.order,businessType:'COFFEE',status:'PENDING_PAY',fulfillmentType:'PICKUP',summaryItems:state.order.items}],total:1,current:1,size:20,pages:1})
  if(p==='/user/cart/list')return reply([])
  return reply({})
 })
 const page=await context.newPage();page.setDefaultTimeout(10000)
 const errors=[];page.on('pageerror',e=>errors.push(e.message))
 return {context,page,state,errors}
}
const go=(page,route)=>page.goto(`${origin}/#${route}`)
async function checkout(page){await go(page,'/pages/coffee-checkout/index');await page.locator('.review-item').first().waitFor()}
async function detail(page){await go(page,'/pages/coffee-order/index?id=9007199254741001');await page.locator('.status-title').waitFor()}
async function payment(page,result='success'){
 await page.evaluate(result=>{window.__payCalls=0;uni.requestPayment=opts=>{window.__payCalls++; if(result==='success')opts.success({errMsg:'requestPayment:ok'});else opts.fail({errMsg:'requestPayment:fail cancel'})}},result)
}
async function check(name,fn,auth=true){const f=await fixture(auth);try{await fn(f);assert.deepEqual(f.errors,[]);console.log(`PASS ${++passed}: ${name}`)}catch(e){console.error('FAILED',name,(await f.page.locator('body').innerText()).slice(-1600));throw e}finally{await f.context.close()}}
async function screenshot(page,name){if(!process.env.UX_SCREENSHOT_DIR)return;await page.evaluate(()=>Promise.all(document.getAnimations().filter(a=>a.effect?.getComputedTiming().iterations!==Infinity).map(a=>a.finished.catch(()=>{}))));fs.mkdirSync(process.env.UX_SCREENSHOT_DIR,{recursive:true});await page.screenshot({path:path.join(process.env.UX_SCREENSHOT_DIR,name+'.png'),fullPage:true})}
;(async()=>{
 browser=await chromium.launch({channel:process.env.UX_BROWSER_CHANNEL||'chrome',headless:true})
 await check('开关关闭时菜单和购物车保持禁用',async({page,state})=>{
  state.store.capabilities.placeOrder=false;state.store.capabilities.payOnline=false
  await go(page,'/pages/coffee/index');await page.locator('.coffee-card').waitFor();assert.equal(await page.locator('.unavailable-button').getAttribute('disabled'),'true')
  await page.locator('.unavailable-button').dispatchEvent('click');assert.equal(state.creates.length,0)
  await go(page,'/pages/coffee-checkout/index');await page.getByText('暂时无法结算',{exact:true}).waitFor();assert.equal(state.creates.length,0)
 })
 await check('仅勾选条目参与核对，使用最新菜单价格和完整规格',async({page})=>{
  await checkout(page);assert.equal(await page.locator('.review-item').count(),1);assert((await page.locator('body').innerText()).includes('热 · 少糖'));assert((await page.locator('.footer-price').innerText()).includes('36.00'))
  await page.locator('textarea').fill('请备吸管');await screenshot(page,'coffee-checkout')
  for(const width of [360,390,430]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))}
 })
 await check('售罄勾选项阻止提交',async({page,state})=>{
  state.rows[0].available=false;await go(page,'/pages/coffee-checkout/index');await page.getByText('暂时无法结算',{exact:true}).waitFor();assert.equal(state.creates.length,0)
 })
 await check('一次建单，支付回调success不伪造到账和取餐凭证',async({page,state})=>{
  await checkout(page);await payment(page);await page.locator('.submit-order').dblclick();await page.waitForURL(/coffee-order/);await page.locator('.status-title').waitFor()
  assert.equal(state.creates.length,1);assert.deepEqual(state.creates[0].items,[{skuId:'11',quantity:2}]);assert.equal(state.creates[0].menuVersion,'menu-v1');assert.equal(state.pays,1);assert.equal(await page.locator('.pickup-code').count(),0);assert((await page.locator('.status-title').innerText()).includes('等待付款'));assert.deepEqual(state.updates,[{checked:0,expectedVersion:4}])
 })
 await check('支付取消保留原订单，从详情继续支付不重复建单',async({page,state})=>{
  await checkout(page);await payment(page,'cancel');await page.locator('.submit-order').click();await page.waitForURL(/coffee-order/);await page.locator('.pay-order:not([disabled])').waitFor();await payment(page)
  await page.locator('.pay-order').click();await page.waitForFunction(()=>window.__payCalls===1);assert.equal(state.creates.length,1);assert.equal(state.pays,2)
 })
 await check('创建超时后刷新页面仍使用原请求键和冻结明细',async({page,state})=>{
  state.createTimeout=true;await checkout(page);await page.locator('textarea').fill('原备注');await page.locator('.submit-order').click();await page.getByText('查询原提交结果',{exact:true}).waitFor({timeout:22000});const first=structuredClone(state.creates[0])
  await page.reload();await page.getByText('查询原提交结果',{exact:true}).waitFor();assert.equal(await page.locator('textarea').getAttribute('disabled'),'');await payment(page,'cancel');await page.locator('.submit-order').click();await page.waitForURL(/coffee-order/);assert.deepEqual(state.creates[1],first)
 })
 await check('菜单冲突重新核对后由用户明确提交新请求',async({page,state})=>{
  state.createConflict=true;await checkout(page);await page.locator('.submit-order').click();await page.getByText('暂时无法结算',{exact:true}).waitFor();const key=state.creates[0].requestKey
  state.createConflict=false;state.menu.version='menu-v2';await page.getByText('重新核对',{exact:true}).click();await page.locator('.submit-order').waitFor();await payment(page,'cancel');await page.locator('.submit-order').click();await page.waitForURL(/coffee-order/);assert.notEqual(state.creates[1].requestKey,key);assert.equal(state.creates[1].menuVersion,'menu-v2')
 })
 await check('已建单金额变化只进入详情核对，不自动付款',async({page,state})=>{
  state.order.amounts.payableAmountCent=4000;await checkout(page);await payment(page);await page.locator('.submit-order').click();await page.waitForURL(/coffee-order/);assert.equal(state.pays,0)
 })
 await check('取餐号与状态以服务端为准，退款状态不展示有效凭证',async({page,state})=>{
  state.order.paymentStatus='PAID';state.order.fulfillmentStatus='READY';state.order.amounts.paidAmountCent=3600
  await detail(page);assert.equal(await page.locator('.pickup-code').innerText(),'0102');await screenshot(page,'coffee-order-ready')
  state.order.refundStatus='PENDING';await page.getByText('刷新订单状态',{exact:true}).click();await page.getByText('退款处理中',{exact:true}).waitFor();assert.equal(await page.locator('.pickup-code').count(),0)
  state.order.refundStatus='SUCCESS';state.order.amounts.refundedAmountCent=3600;await page.getByText('刷新订单状态',{exact:true}).click();await page.getByText('已原路退款',{exact:true}).waitFor()
 })
 await check('未付款订单确认取消后停止付款入口',async({page,state})=>{
  await detail(page);await page.getByText('取消订单',{exact:true}).click();await page.locator('.uni-modal__btn_primary').click();await page.getByText('订单已取消',{exact:true}).waitFor();assert.equal(state.cancels,1);assert.equal(await page.locator('.pay-order').count(),0)
 })
 await check('详情失败显示重试，保留订单不伪装为空',async({page,state})=>{
  state.detailError=true;await go(page,'/pages/coffee-order/index?id=9007199254741001');await page.getByText('订单暂时未能同步',{exact:true}).waitFor();state.detailError=false;await page.getByText('重新加载',{exact:true}).click();await page.locator('.status-title').waitFor();assert.equal(await page.getByText('订单暂时未能同步',{exact:true}).count(),0)
 })
 await check('统一订单列表的咖啡订单可进入对应详情',async({page})=>{
  await go(page,'/pages/orders/index');await page.locator('.order-card').click();await page.waitForURL(/coffee-order.*9007199254741001/);await page.locator('.status-title').waitFor()
 })
 await check('账号切换时不展示旧快照或用新账号付款',async({page,state})=>{
  state.createDelay=500;await checkout(page);await payment(page);const sent=page.waitForRequest(r=>r.method()==='POST'&&r.url().endsWith('/user/coffee/orders'));await page.locator('.submit-order').click();await sent
  await page.evaluate(async()=>{const storage=await import('/src/utils/storage.ts');storage.setUserId('11');storage.setToken('test-user-B')})
  await page.getByText('登录状态已变化，请重新核对',{exact:true}).waitFor();await delay(650);assert.equal(state.pays,0);assert.equal(await page.locator('.review-item').count(),0)
 })
 await check('游客访问结算引导登录，不请求建单或支付',async({page,state})=>{
  await go(page,'/pages/coffee-checkout/index');await page.waitForURL(/pages\/login/);assert.equal(state.creates.length,0);assert.equal(state.pays,0)
 },false)
 await check('离开订单详情后停止轮询',async({page,state})=>{
  await detail(page);await page.getByText('查看全部订单',{exact:true}).click();await page.waitForURL(/pages\/orders/);const before=state.reads;await delay(5400);assert.equal(state.reads,before)
 })
 console.log(`Coffee checkout UX: ${passed} scenarios passed`)
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(browser)await browser.close()})
