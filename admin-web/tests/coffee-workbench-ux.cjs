/* Contract fixtures only. No real credentials, transactions or external writes. */
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.UX_ADMIN_BASE_URL || 'http://127.0.0.1:5185'
const shots = process.env.UX_SCREENSHOT_DIR
const perms = ['admin:accounts:manage','dashboard:view','product:manage','order:manage','inventory:manage','customer:manage','coupon:manage','distribution:manage','marketing:manage','coffee:menu:read','coffee:menu:write','coffee:store:write','coffee:orders:read','coffee:orders:operate','coffee:orders:refund','coffee:ledger:read']
const store = { id: '9007199254740994', name: '契约夹具 · 医院咖啡店', address: '本地测试地址', openingHours: '08:00–18:00', businessStatus: 'OPEN', announcement: null }
const stages = ['WAIT_ACCEPT','WAIT_MAKE','MAKING','READY']
const actionMap = { WAIT_ACCEPT: ['ACCEPT','REJECT','REFUND'], WAIT_MAKE: ['START','REFUND'], MAKING: ['READY','REFUND'], READY: ['COMPLETE','REFUND'], COMPLETED: ['REFUND'], REJECTED: [] }
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
function order(index, status = 'WAIT_ACCEPT') {
  return { id: String(9007199254741000n + BigInt(index)), orderNo: `CF-TEST-${index}`, storeId: store.id, storeName: store.name, pickupCode: `A${String(index).padStart(3,'0')}`, pickupDate: index === 1 ? '2026-10-25' : today(), fulfillmentStatus: status, paymentStatus: 'PAID', refundStatus: 'NONE', refundReason: null, createdAt: '2026-10-25T09:00:00+08:00', paidAt: '2026-10-25T09:00:10+08:00', acceptedAt: null, makingAt: null, readyAt: null, completedAt: null, stageEnteredAt: new Date(Date.now()-900000).toISOString(), version: 0, remark: '一杯少糖，整单取走', itemCount: 3, items: [{ productId: '31', skuId: '311', productName: '桂花拿铁', specName: '大杯', temperature: 'HOT', sugar: 'LESS', quantity: 2, unitPriceCent: 1800, lineTotalCent: 3600, imageUrl: null }, { productId: '32', skuId: '321', productName: '美式咖啡', specName: '标准杯', temperature: 'ICED', sugar: 'NONE', quantity: 1, unitPriceCent: 1200, lineTotalCent: 1200, imageUrl: null }], amounts: { totalAmountCent: 4800, payableAmountCent: 4800, paidAmountCent: 4800, refundedAmountCent: 0 }, allowedActions: actionMap[status], notificationStatus: 'NONE', events: [{ action: 'PAID', operatorName: '可信支付渠道', createdAt: '2026-10-25T09:00:10+08:00', reason: null }] }
}
let browser, passed = 0, index = 0
async function fixture(role = 'COFFEE_ADMIN', logged = true) {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1050 }, timezoneId: 'Asia/Shanghai' })
  await context.addInitScript(logged => { if(logged && !sessionStorage.getItem('fixture-bootstrapped')) { localStorage.setItem('jinlin_admin_token','fixture-token'); sessionStorage.setItem('fixture-bootstrapped','1') }; localStorage.setItem('jinlin_admin_permissions', JSON.stringify(['admin:accounts:manage'])); }, logged)
  const state = { role, calls: [], rows: stages.map((s,i) => order(i+1,s)), accounts: [{ id: '9007199254741999', username: 'fixture-super', realName: '夹具超管', roles: ['SUPER_ADMIN'], storeId: null, storeName: null, status: 1, mustChangePwd: false, lastLoginTime: null }], error: 0, conflict: false, loseWrite: false, refundFailed: false, mustChange: false, noStore: false, contextError: 0, active: 0, maxActive: 0 }
  const permissions = () => state.role === 'SUPER_ADMIN' ? perms : state.role === 'GOODS_ADMIN' ? perms.filter(p => !p.startsWith('coffee:') && !['marketing:manage','admin:accounts:manage'].includes(p)) : state.role === 'COFFEE_ADMIN' ? perms.filter(p => p.startsWith('coffee:')) : []
  const authContext = () => ({ adminId: '9007199254741999', username: 'fixture-user', realName: '夹具店员', roles: [state.role], permissions: permissions(), stores: state.noStore || state.role === 'GOODS_ADMIN' ? [] : [{ id: store.id, name: store.name }], defaultRoute: state.role === 'COFFEE_ADMIN' ? '/coffee/workbench' : '/dashboard' })
  await context.route(`${origin}/admin/**`, async route => {
    const req = route.request(), url = new URL(req.url()), p = url.pathname, method = req.method(), body = req.postDataJSON() || {}, q = Object.fromEntries(url.searchParams)
    state.calls.push({ path: p, method, query: q, body: Object.fromEntries(Object.entries(body).filter(([key]) => !/password/i.test(key))) })
    const reply = (data, status = 200, message = 'ok') => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 0 : status, message, data }) })
    if (p === '/admin/login') return reply({ token: 'fixture-token', username: 'fixture-user', adminId: '9007199254741999', mustChangePwd: state.mustChange })
    if (p === '/admin/password') { state.mustChange = false; return reply({ token: 'fixture-changed-token', username: 'fixture-user', adminId: '9007199254741999', mustChangePwd: false }) }
    if (p === '/admin/auth/context') return state.contextError ? reply(null,state.contextError,'会话已失效') : reply(authContext())
    if (p === '/admin/logout') return reply(true)
    if (p === '/admin/coffee/context') return reply({ stores: authContext().stores, permissions: permissions().filter(p => p.startsWith('coffee:')) })
    if (p.startsWith('/admin/coffee/store/')) { if (method === 'PUT') Object.assign(store,body); return reply(store) }
    if (p === '/admin/coffee/workbench') {
      state.active++; state.maxActive = Math.max(state.maxActive,state.active)
      await new Promise(r => setTimeout(r,60)); state.active--
      if(state.error) return reply(null,state.error,'连接中断夹具')
      return reply({ storeId: store.id, serverTime: new Date().toISOString(), columns: stages.map(status => { const rows = state.rows.filter(o => o.fulfillmentStatus === status && o.refundStatus === 'NONE'); return { status, records: rows.slice(0,20), total: rows.length, current:1, size:20, pages:Math.ceil(rows.length/20) } }) })
    }
    if (p === '/admin/coffee/orders/list') {
      let rows = state.rows.filter(o => (!q.fulfillmentStatus || o.fulfillmentStatus === q.fulfillmentStatus) && (!q.refundStatus || o.refundStatus === q.refundStatus))
      return reply({ records: rows.slice((q.page-1)*q.size,q.page*q.size), total:rows.length, current:Number(q.page), size:Number(q.size), pages:Math.ceil(rows.length/q.size) })
    }
    if (p === '/admin/coffee/ledger') return reply({ storeId:store.id,...q,timeZone:'Asia/Shanghai',paidOrderCount:0,receivedAmountCent:0,refundedAmountCent:4800,netReceivedAmountCent:-4800 })
    if (p.startsWith('/admin/coffee/orders/')) {
      const [, , , , id, action] = p.split('/'), row = state.rows.find(o => o.id === id)
      if(!row) return reply(null,404,'订单不可见')
      if(method === 'GET') return reply(row)
      if(state.conflict) { state.conflict = false; row.version++; row.fulfillmentStatus = 'WAIT_MAKE'; row.allowedActions = actionMap.WAIT_MAKE; return reply(null,409,'另一位店员已经接单') }
      assert.equal(body.expectedVersion,row.version)
      if(['refund','reject'].includes(action)) {
        assert(body.requestKey); assert(body.reason); assert(!('amount' in body)); assert(!('amountCent' in body))
        row.refundStatus = state.refundFailed ? 'FAILED' : 'PENDING'; row.refundReason = body.reason; row.fulfillmentStatus = action === 'reject' ? 'REJECTED' : 'CANCELLED'; row.allowedActions = state.refundFailed ? ['REFUND'] : []
      } else { row.fulfillmentStatus = { accept:'WAIT_MAKE',start:'MAKING',ready:'READY',complete:'COMPLETED' }[action]; row.allowedActions = actionMap[row.fulfillmentStatus]; if(action === 'ready') row.notificationStatus = 'SKIPPED' }
      row.version++; row.events.push({ action:action.toUpperCase(),operatorName:'夹具店员',createdAt:new Date().toISOString(),reason:body.reason || null })
      if(state.loseWrite) { state.loseWrite = false; return route.abort('timedout') }
      return reply(row)
    }
    if (p === '/admin/accounts/roles') return reply(['SUPER_ADMIN','GOODS_ADMIN','COFFEE_ADMIN'].map(code => ({ code,name:{SUPER_ADMIN:'超级管理员',GOODS_ADMIN:'商品管理员',COFFEE_ADMIN:'咖啡管理员'}[code],description:`${code}固定权限说明`,permissions:[] })))
    if (p === '/admin/accounts/coffee-stores') return reply([{id:store.id,name:store.name}])
    if (p === '/admin/accounts/list') return reply({records:state.accounts,total:state.accounts.length,size:20,current:1,pages:1})
    if (p === '/admin/accounts' && method === 'POST') { const row = { id:'9007199254742000',username:body.username,realName:body.realName,roles:[body.roleCode],storeId:body.storeId || null,storeName:body.storeId ? store.name : null,status:1,lastLoginTime:null,mustChangePwd:true }; state.accounts.push(row); return reply(row) }
    if (p.startsWith('/admin/accounts/')) {
      const id = p.split('/')[3], row = state.accounts.find(a => a.id === id)
      if (p.endsWith('/status')) { if(row.roles.includes('SUPER_ADMIN') && body.status === 0) return reply(null,409,'不能停用最后一个启用超管'); row.status = body.status }
      else if(p.endsWith('/access')) { row.roles = [body.roleCode]; row.storeId = body.storeId || null }
      else if(p.endsWith('/reset-password')) row.mustChangePwd = true
      else row.realName = body.realName
      return reply(row)
    }
    if(p === '/admin/coffee/menu') return reply({ storeId:store.id,categories:[],products:[] })
    if(p.includes('/list')) return reply({ records:[],total:0,size:20,current:1,pages:0 })
    return reply({})
  })
  const page = await context.newPage(), errors = []; page.on('pageerror', e => errors.push(e.message)); page.setDefaultTimeout(10000)
  return { context,page,state,errors }
}
async function shot(page,name) { if(shots) { fs.mkdirSync(shots,{recursive:true}); await page.screenshot({ path:path.join(shots,`${name}.png`),fullPage:true }) } }
async function check(name,fn,role='COFFEE_ADMIN',logged=true) {
  index++; if(index < Number(process.env.UX_TEST_FROM || 1) || index > Number(process.env.UX_TEST_TO || 999)) return
  const f = await fixture(role,logged)
  try { await fn(f); assert.deepEqual(f.errors,[]); console.log(`PASS ${++passed}: ${name}`) }
  catch(e) { console.error('SCENARIO',name,'BODY',(await f.page.locator('body').innerText()).slice(-2500)); throw e }
  finally { await f.context.close() }
}
const card = page => page.locator('[data-order-id="9007199254741001"]')
async function board(page) { await page.goto(`${origin}/coffee/workbench`); await card(page).waitFor() }
const ok = page => page.locator('.ant-modal:visible').getByRole('button',{ name:'确 定' })
;(async () => {
  browser = await chromium.launch({channel:'chrome',headless:true})
  await check('咖啡深链接、刷新与伪造本地权限不能越权',async ({page,state}) => {
    await page.goto(`${origin}/system/accounts`); await page.getByText('无权限访问此页面',{exact:true}).waitFor(); assert(!state.calls.some(c => c.path.startsWith('/admin/accounts')))
    await page.reload(); await page.getByText('无权限访问此页面',{exact:true}).waitFor()
    await page.goto(`${origin}/products`); await page.getByText('无权限访问此页面',{exact:true}).waitFor(); assert(!state.calls.some(c => c.path.startsWith('/admin/product')))
  })
  await check('商品账号保留商品页面且不请求咖啡上下文、不能编辑首页运营',async ({page,state}) => {
    await page.goto(`${origin}/products`); await page.getByRole('heading',{name:'商品',exact:true}).waitFor(); assert(!state.calls.some(c => c.path.includes('/coffee/')))
    await page.goto(`${origin}/marketing-activities`); await page.getByText('无权限访问此页面',{exact:true}).waitFor()
    await page.goto(`${origin}/coffee/workbench`); await page.getByText('无权限访问此页面',{exact:true}).waitFor(); assert(!state.calls.some(c => c.path.includes('/coffee/')))
  },'GOODS_ADMIN')
  await check('未知角色不会默认升级成商品管理员',async ({page}) => { await page.goto(origin); await page.getByText('当前账号尚无业务授权').waitFor() },'UNKNOWN')
  await check('首次改密后读取真实上下文并落在咖啡工作台',async ({page,state}) => {
    state.mustChange=true; await page.goto(`${origin}/login?redirect=/system/accounts`); await page.locator('#username').fill('fixture-user'); await page.locator('#password').fill('fixture-password'); await page.getByRole('button',{name:'登 录'}).click()
    await page.getByText('请修改初始密码').waitFor(); assert(!state.calls.some(c=>c.path==='/admin/auth/context'))
    await page.locator('#oldPassword').fill('fixture-password'); await page.locator('#newPassword').fill('fixture-new-password'); await page.locator('#confirmPassword').fill('fixture-new-password'); await page.getByRole('button',{name:'确认修改'}).click(); await card(page).waitFor(); assert(page.url().endsWith('/coffee/workbench'))
  },'COFFEE_ADMIN',false)
  await check('无门店绑定不使用默认ID',async ({page,state}) => { state.noStore=true; await page.goto(`${origin}/coffee/workbench`); await page.getByText('当前账号没有授权门店，请联系超级管理员。').waitFor(); assert(!state.calls.some(c=>c.path==='/admin/coffee/workbench')) })
  await check('四列显示全规格、跨日、独立接单制作和真实详情',async ({page}) => {
    await board(page); assert.equal(await page.locator('.coffee-column:visible').count(),4); await card(page).getByText('大杯 · 热 · 少糖',{exact:true}).waitFor(); await card(page).getByText('标准杯 · 冰 · 无糖',{exact:true}).waitFor(); assert.equal(await card(page).getByRole('button',{name:'开始制作'}).count(),0)
    await shot(page,'coffee-workbench-wide-fixture'); await card(page).getByRole('button',{name:'详情与操作记录'}).click(); await page.getByText('可信支付渠道',{exact:false}).waitFor(); await page.locator('.ant-drawer-close').click()
    await page.setViewportSize({width:900,height:1100}); assert.equal(await page.locator('.coffee-column:visible').count(),1); await page.getByRole('tab',{name:/制作中/}).click(); await page.locator('[data-stage="MAKING"]:visible').waitFor(); await shot(page,'coffee-workbench-tablet-fixture')
  })
  await check('接单、开始制作、完成、确认取走逐步请求',async ({page,state}) => {
    await board(page); for(const [label,status] of [['确认接单','WAIT_MAKE'],['开始制作','MAKING'],['制作完成','READY'],['确认取走','COMPLETED']]) {
      await card(page).getByRole('button',{name:label,exact:true}).click(); if(status==='COMPLETED') await ok(page).click()
      await page.waitForFunction(({id,status}) => { const e=document.querySelector(`[data-order-id="${id}"]`); return status==='COMPLETED' ? !e : e?.closest('section').dataset.stage===status }, {id:state.rows[0].id,status})
    }
    assert.deepEqual(state.calls.filter(c=>c.method==='POST' && c.path.includes('/orders/')).map(c=>c.path.split('/').pop()),['accept','start','ready','complete'])
  })
  await check('多人409冲突重新读取而不覆盖',async ({page,state}) => { state.conflict=true; await board(page); await card(page).getByRole('button',{name:'确认接单',exact:true}).click(); await page.getByText(/订单已变化/).waitFor(); await card(page).getByRole('button',{name:'开始制作',exact:true}).waitFor(); assert.equal(state.calls.filter(c=>c.method==='POST').length,1) })
  await check('拒单核对号码、金额和原因，处理中不显示退款成功',async ({page,state}) => {
    await board(page); await card(page).getByRole('button',{name:'无法接单'}).click(); await page.locator('.ant-modal').getByText(/整单全额 ¥48.00/).waitFor(); await page.locator('#reasonType').click(); await page.locator('.ant-select-item-option-content').getByText('缺货',{exact:true}).click(); await ok(page).click(); await card(page).waitFor({state:'hidden'}); assert.equal(state.rows[0].refundStatus,'PENDING'); assert.equal(state.calls.filter(c=>c.method==='POST')[0].body.reason,'缺货')
  })
  await check('写入超时后查询结果不自动重放',async ({page,state}) => { state.loseWrite=true; await board(page); await card(page).getByRole('button',{name:'确认接单',exact:true}).click(); await card(page).getByRole('button',{name:'开始制作',exact:true}).waitFor(); assert.equal(state.calls.filter(c=>c.method==='POST').length,1); assert(state.calls.some(c=>c.path.endsWith(state.rows[0].id) && c.method==='GET')) })
  await check('分页所有列明确剩余并保留跨日查询口径',async ({page,state}) => {
    state.rows=[...Array.from({length:23},(_,i)=>order(i+1)),...state.rows.slice(1)]; await board(page); await page.getByRole('button',{name:'继续加载（剩余 3）'}).click(); await page.getByText('已显示 23 / 23',{exact:true}).waitFor(); const call=state.calls.find(c=>c.path.endsWith('/orders/list')); assert.equal(call.query.page,'2'); assert.equal(call.query.storeId,store.id); assert(!call.query.dateFrom); assert.equal(await page.locator('[data-stage="WAIT_ACCEPT"] article').count(),23); assert.equal(await page.getByText(/有 .* 笔新待接订单/).count(),0)
  })
  await check('断网保留原卡片、重连不把存量当新单且不重叠轮询',async ({page,state}) => {
    await board(page); state.error=503; await page.getByRole('button',{name:'刷新订单'}).click(); await page.getByText('连接中断，订单可能未更新',{exact:true}).waitFor(); assert.equal(await page.locator('article').count(),4)
    state.error=0; state.rows.push(order(8)); await page.getByRole('button',{name:/重\s*试/}).click(); await page.locator('[data-order-id="9007199254741008"]').waitFor(); assert.equal(await page.getByText(/有 .* 笔新待接订单/).count(),0)
    state.rows.push(order(9)); await page.getByRole('button',{name:'刷新订单'}).click(); await page.getByText('有 1 笔新待接订单，请及时处理').waitFor(); assert.equal(state.maxActive,1)
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')) })
    const hiddenReads = state.calls.filter(c=>c.path.endsWith('/workbench')).length
    await page.waitForTimeout(5200); assert.equal(state.calls.filter(c=>c.path.endsWith('/workbench')).length, hiddenReads)
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')) })
    await page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/workbench'))
    const before=state.calls.length; await page.getByRole('link',{name:'菜单管理',exact:true}).click(); await page.getByRole('heading',{name:'咖啡菜单',exact:true}).waitFor(); await page.waitForTimeout(5200); assert(!state.calls.slice(before).some(c=>c.path.endsWith('/workbench')))
  })
  await check('退款失败可查询、流水允许负净额且不可编辑退款金额',async ({page,state}) => {
    state.refundFailed=true; await page.goto(`${origin}/coffee/orders`); await page.getByText('¥-48.00',{exact:true}).waitFor(); await page.getByRole('button',{name:'整单全额退款',exact:true}).first().click(); await page.locator('#reason').fill('无法继续制作'); await shot(page,'coffee-orders-refund-fixture'); await ok(page).click(); await page.getByText('退款失败',{exact:true}).first().waitFor(); await page.getByRole('button',{name:'全部退款失败'}).click(); await page.waitForTimeout(100); const q=state.calls.filter(c=>c.path.endsWith('/orders/list')).at(-1).query; assert.equal(q.dateFrom,'1970-01-01'); assert.equal(q.refundStatus,'FAILED')
  })
  await check('超管账号页创建咖啡账号使用真实角色门店、密码不缓存',async ({page,state}) => {
    await page.goto(`${origin}/system/accounts`); await page.getByText('fixture-super',{exact:true}).waitFor(); await shot(page,'admin-accounts-fixture'); await page.getByRole('button',{name:'新建账号'}).click(); await page.locator('#username').fill('fixture-coffee'); await page.locator('#realName').fill('夹具新店员'); await page.locator('#temporaryPassword').fill('fixture-temporary-password'); await page.locator('#roleCode').click(); await page.getByText('咖啡管理员',{exact:true}).last().click(); await page.locator('#storeId').click(); await page.getByText(store.name,{exact:true}).last().click(); await ok(page).click(); await page.getByText('fixture-coffee',{exact:true}).waitFor(); assert.equal(state.accounts[1].storeId,store.id); assert.equal(await page.evaluate(()=>Object.values(localStorage).join('').includes('fixture-temporary-password')),false)
  },'SUPER_ADMIN')
  await check('最后超管停用被拒绝就地显示且保留启用状态',async ({page,state}) => {
    await page.goto(`${origin}/system/accounts`); await page.getByRole('button',{name:/停\s*用/}).click(); await ok(page).click(); await page.getByText('不能停用最后一个启用超管',{exact:true}).waitFor(); assert.equal(state.accounts[0].status,1)
  },'SUPER_ADMIN')
  await check('修改角色与重置密码按独立动作提交，关闭清除临时密码',async ({page,state}) => {
    await page.goto(`${origin}/system/accounts`); await page.getByRole('button',{name:/授\s*权/}).click(); await page.locator('#roleCode').click(); await page.getByText('商品管理员',{exact:true}).last().click(); await ok(page).click(); await page.locator('.ant-modal').waitFor({state:'hidden'}); assert.deepEqual(state.accounts[0].roles,['GOODS_ADMIN']); assert(!('storeId' in state.calls.find(c=>c.path.endsWith('/access')).body))
    await page.getByRole('button',{name:'重置密码',exact:true}).click(); await page.locator('#temporaryPassword').fill('fixture-temporary-password'); await page.locator('.ant-modal').getByRole('button',{name:'取 消'}).click(); await page.getByRole('button',{name:'重置密码',exact:true}).click(); assert.equal(await page.locator('#temporaryPassword').inputValue(),''); await page.locator('#temporaryPassword').fill('fixture-other-password'); await ok(page).click(); await page.locator('.ant-modal').waitFor({state:'hidden'}); assert(state.calls.some(c=>c.path.endsWith('/reset-password')))
  },'SUPER_ADMIN')
  await check('401撤销会话退出，403保持无权限提示',async ({page,state}) => {
    state.contextError=403; await page.goto(`${origin}/coffee/workbench`); await page.getByText('无法取得后台授权',{exact:true}).waitFor(); assert(!page.url().includes('/login'))
    state.contextError=401; await page.reload(); await page.waitForURL('**/login?redirect=**'); assert.equal(await page.evaluate(()=>localStorage.getItem('jinlin_admin_token')),null)
  })
  console.log(`Coffee workbench UX: ${passed} scenarios passed`)
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{await browser?.close()})
