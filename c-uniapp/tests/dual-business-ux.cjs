/* Contract fixtures live only in tests. Requests are intercepted on local origins;
 * no real login, order, payment or admin mutation is sent by this suite. */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.UX_BASE_URL || 'http://127.0.0.1:5174'
const adminOrigin = process.env.UX_ADMIN_BASE_URL || 'http://127.0.0.1:5175'
const screenshotDir = process.env.UX_SCREENSHOT_DIR
const store = { id: '9007199254740994', name: '契约测试咖啡店', address: '仅供本地回归的门店地址', openingHours: '09:00–18:00', timeZone: 'Asia/Shanghai', businessStatus: 'CLOSED', announcement: null, capabilities: { browse: true, addToCart: true, placeOrder: false, payOnline: false }, orderUnavailableReason: 'PAYMENT_NOT_READY' }
const sku = (id, productId, temperature, sugar, priceCent = 1800, available = true) => ({ id, productId, name: `${temperature}-${sugar}`, temperature, sugar, priceCent, available, unavailableReason: available ? null : 'SOLD_OUT' })
const menu = { storeId: store.id, version: 'menu-v1-opaque', categories: [{ id: '10', name: '咖啡', sort: 0 }, { id: '20', name: '手作', sort: 1 }], products: [
  { id: '101', categoryId: '10', name: '本地回归拿铁', imageUrl: null, description: '有温度甜度限制的饮品', sort: 0, skus: [sku('1001', '101', 'HOT', 'REGULAR'), sku('1002', '101', 'HOT', 'LESS'), sku('1003', '101', 'ICED', 'REGULAR', 2000), sku('1004', '101', 'ICED', 'NONE', 2200), sku('1005', '101', 'ICED', 'LESS', 2100, false)] },
  { id: '102', categoryId: '20', name: '只有热饮的咖啡', imageUrl: null, description: '仅有热无糖规格', sort: 1, skus: [sku('1006', '102', 'HOT', 'NONE', 1500)] },
  { id: '103', categoryId: '20', name: '全部售罄的饮品', imageUrl: null, description: '', sort: 2, skus: [sku('1007', '103', 'HOT', 'NONE', 1500, false)] },
] }
const coffeeRows = [
  { id: '9007199254741001', storeId: store.id, productId: '101', skuId: '1001', productName: '本地回归拿铁', skuName: '标准拿铁', imageUrl: null, temperature: 'HOT', sugar: 'REGULAR', unitPriceCent: 1800, quantity: 2, checked: 1, available: true, unavailableReason: null, version: 3 },
  { id: '9007199254741002', storeId: store.id, productId: '103', skuId: '1007', productName: '已停售的咖啡', skuName: '原规格', imageUrl: null, temperature: 'HOT', sugar: 'NONE', unitPriceCent: 1500, quantity: 3, checked: 1, available: false, unavailableReason: 'PRODUCT_OFF_SHELF', version: 2 },
  { id: '9007199254741003', storeId: store.id, productId: '102', skuId: '1006', productName: '只有热饮的咖啡', skuName: '热无糖', imageUrl: null, temperature: 'HOT', sugar: 'NONE', unitPriceCent: 1500, quantity: 1, checked: 0, available: true, unavailableReason: null, version: 0 },
]
const goodsRows = [{ id: 1, userId: 1, productId: 1, skuId: 11, productName: '契约回归草本茶', skuName: '日常装', quantity: 2, checked: 1, price: 69.9, effectivePrice: 59.9, stock: 20 }]
function order(id = '9007199254741010', status = 'PENDING_PAY') {
  return { businessType: 'GOODS', id, orderNo: `回归-${id}`, storeId: null, storeName: '二十四养', fulfillmentType: 'DELIVERY', status, createdAt: '2026-10-25T11:00:00+08:00', itemCount: 7, summaryItems: [{ productId: '1', skuId: '11', productName: '订单历史名称', specName: '订单历史规格', imageUrl: null, unitPriceCent: 100, quantity: 2, lineTotalCent: 200 }], amounts: { totalAmountCent: 600, discountAmountCent: 0, freightAmountCent: 0, payableAmountCent: 600, paidAmountCent: null }, pickupCode: null, pickupDate: null }
}
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
let browser, passed = 0, scenarioIndex = 0
async function scenario(auth = true) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' })
  await context.addInitScript(authenticated => {
    localStorage.setItem('popup_login_shown', '1')
    if (authenticated) { localStorage.setItem('jl_user_token', 'account-A'); localStorage.setItem('jl_user_id', '1') }
  }, auth)
  const state = { requests: [], store: structuredClone(store), menu: structuredClone(menu), coffee: structuredClone(coffeeRows), goods: structuredClone(goodsRows), coffeeB: [], storeError: 0, menuError: 0, cartError: 0, conflict: false, lostPost: false, rejectSku: false, writeDelay: 0, readDelay: 0, writes: 0, listReads: 0, orders: [order()], orderRequests: [], orderDelay: 0, goodsPay: 0 }
  await context.route('http://127.0.0.1:7878/**', async route => {
    const request = route.request(), url = new URL(request.url()), p = url.pathname, method = request.method(), body = request.postDataJSON() || {}, token = request.headers().authorization?.replace('Bearer ', '')
    state.requests.push({ path: p, method, body, token, query: Object.fromEntries(url.searchParams) })
    const reply = (data, status = 200, code = 0, message = 'ok') => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code, message, data }) })
    if (p === '/user/coffee/store') return state.storeError ? reply(null, state.storeError, 90401, '咖啡目录未配置') : reply(state.store)
    if (p === '/user/coffee/menu') return state.menuError ? reply(null, state.menuError, 50000, '菜单加载失败') : reply(state.menu)
    const rows = token === 'account-B' ? state.coffeeB : state.coffee
    if (p === '/user/coffee/cart/list') {
      state.listReads++
      const snapshot = structuredClone(rows)
      await delay(state.readDelay)
      return state.cartError ? reply(null, state.cartError, state.cartError === 401 ? 40100 : 50000, '咖啡篮读取失败') : reply(snapshot)
    }
    if (p === '/user/coffee/cart' && method === 'POST') {
      state.writes++; await delay(state.writeDelay)
      if (state.rejectSku) { state.menu.products[0].skus[0].available = false; return reply(null, 409, 90403, '规格已售罄') }
      const selected = state.menu.products.flatMap(product => product.skus).find(sku => sku.id === body.skuId)
      let row = rows.find(row => row.skuId === body.skuId)
      if (row) { row.quantity += body.quantity; row.checked = body.checked ?? 1; row.version++ }
      else { row = { id: String(7000 + rows.length), storeId: store.id, productId: selected.productId, skuId: selected.id, productName: '新加测试饮品', skuName: selected.name, imageUrl: null, temperature: selected.temperature, sugar: selected.sugar, unitPriceCent: selected.priceCent, quantity: body.quantity, checked: body.checked ?? 1, available: true, unavailableReason: null, version: 0 }; rows.push(row) }
      if (state.lostPost) return route.abort('timedout')
      return reply(row)
    }
    if (p === '/user/coffee/cart/selection') { state.writes++; for (const row of rows) if (row.available && row.checked !== body.checked) { row.checked = body.checked; row.version++ } return reply(rows) }
    if (p.startsWith('/user/coffee/cart/')) {
      state.writes++; await delay(state.writeDelay)
      const id = p.split('/').pop(), row = rows.find(row => row.id === id)
      if (method === 'DELETE') { rows.splice(rows.findIndex(row => row.id === id), 1); return reply(true) }
      if (state.conflict || body.expectedVersion !== row.version) { state.conflict = false; row.quantity = 5; row.version++; return reply(null, 409, 90409, '版本冲突') }
      if (body.quantity !== undefined) row.quantity = body.quantity
      if (body.checked !== undefined) row.checked = body.checked
      row.version++; return reply(row)
    }
    if (p === '/user/cart/list') return reply(state.goods)
    if (p.startsWith('/user/cart/')) { const row = state.goods.find(row => row.id === Number(p.split('/').pop())); Object.assign(row, body); return reply(row) }
    if (p === '/user/business-order/list') {
      const query = Object.fromEntries(url.searchParams); state.orderRequests.push(query)
      // Match the real controller: empty optional filters are invalid, not defaults.
      if (['businessType', 'statusGroup', 'status'].some(key => key in query && !query[key])) return reply(null, 400, 40001, '订单筛选或分页参数错误')
      if (query.status === 'PENDING_PAY' || query.statusGroup === 'IN_PROGRESS') await delay(state.orderDelay)
      if (query.businessType === 'COFFEE') return reply({ records: [], total: 0, size: 20, current: Number(query.page), pages: 0 })
      const current = Number(query.page), filtered = state.orders.filter(row => !query.status || row.status === query.status)
      return reply({ records: filtered.slice((current - 1) * 20, current * 20), total: filtered.length, size: 20, current, pages: Math.ceil(filtered.length / 20) })
    }
    if (p === '/user/order/preview') return reply({ items: state.goods.map(row => ({ productId: 1, skuId: 11, productName: row.productName, skuName: row.skuName, quantity: row.quantity, stock: row.stock, price: row.effectivePrice, totalPrice: 119.8 })), totalAmount: 119.8, payableAmount: 119.8, payAmount: 119.8, freightAmount: 0, discountAmount: 0 })
    if (p.startsWith('/user/order/')) { if (p.endsWith('/pay')) state.goodsPay++; return reply({ id: 99, orderNo: '商品详情', status: 0, totalAmount: 6, payAmount: 6, items: [] }) }
    if (p.endsWith('/login')) return reply({ token: 'account-A', userId: 1 })
    if (p.endsWith('/me')) return reply({ id: 1, nickname: '本地回归用户' })
    return reply([])
  })
  const page = await context.newPage()
  const errors = []; page.on('pageerror', e => errors.push(e.message))
  return { context, page, state, errors }
}
async function go(page, route) { await page.goto(`${origin}/#${route}`) }
async function shot(page, name) {
  if (!screenshotDir) return
  fs.mkdirSync(screenshotDir, { recursive: true })
  await page.locator('.uni-toast').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  await page.screenshot({ path: path.join(screenshotDir, `${name}.png`), fullPage: page.url().startsWith(adminOrigin) })
}
async function coffeeCart(page) {
  await go(page, '/pages/cart/index'); await page.getByRole('tab', { name: /^咖啡/ }).click(); await page.locator('.coffee-cart-items .cart-row').first().waitFor()
}
async function openSpec(page, name = '本地回归拿铁') {
  await go(page, '/pages/coffee/index'); await page.locator('.coffee-card').filter({ hasText: name }).locator('.spec-button').click(); await page.locator('.spec-sheet').waitFor()
}
async function waitQuantity(page, value, selector = '.quantity') { await page.waitForFunction(({ selector, value }) => document.querySelector(selector)?.textContent.trim() === String(value), { selector, value }) }
async function check(name, fn, auth = true) {
  scenarioIndex++
  if (scenarioIndex < Number(process.env.UX_TEST_FROM || 1) || scenarioIndex > Number(process.env.UX_TEST_TO || 999)) return
  const data = await scenario(auth)
  data.page.setDefaultTimeout(10000)
  try { await fn(data); assert.deepEqual(data.errors, [], '浏览器脚本错误'); console.log(`PASS ${++passed}: ${name}`) } catch (e) { console.error('FAILED STATE', JSON.stringify({ requests: data.state.requests, writes: data.state.writes, body: (await data.page.locator('body').innerText()).slice(-2200) })); throw e } finally { await data.context.close() }
}
async function adminScenario(options = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(() => { localStorage.setItem('jinlin_admin_token', 'test-only'); localStorage.setItem('jinlin_admin_username', '回归管理员') })
  const state = { calls: [], store: { ...structuredClone(store) }, categories: [{ id: '10', name: '咖啡', sort: 0, status: 1 }], products: [{ ...structuredClone(menu.products[0]), status: 1, skus: menu.products[0].skus.map(sku => ({ ...sku, status: 1 })) }] }
  await context.route(`${adminOrigin}/admin/**`, async route => {
    const req = route.request(), u = new URL(req.url()), body = req.postDataJSON()
    state.calls.push({ path: u.pathname, method: req.method(), body })
    const reply = (data, status = 200, code = 0, message = 'ok') => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code, message, data }) })
    if (u.pathname === '/admin/auth/context') return reply({ adminId: '9007199254740999', username: '回归管理员', realName: null, roles: ['SUPER_ADMIN'], permissions: options.forbidden ? [] : options.readonly ? ['coffee:menu:read'] : ['coffee:menu:read', 'coffee:menu:write', 'coffee:store:write'], stores: options.empty && !state.created ? [] : [{ id: store.id, name: store.name }], defaultRoute: '/coffee/menu' })
    if (u.pathname === '/admin/coffee/context') {
      if (options.forbidden) return reply(null, 403, 40300, '需要平台管理员权限')
      return reply({ stores: options.empty && !state.created ? [] : [{ id: store.id, name: store.name }], permissions: options.readonly ? ['coffee:menu:read'] : ['coffee:menu:read', 'coffee:menu:write', 'coffee:store:write'] })
    }
    if (u.pathname.startsWith('/admin/coffee/store')) {
      if (req.method() === 'POST') { state.created = true; Object.assign(state.store, body); return reply(state.store) }
      if (req.method() === 'PUT') Object.assign(state.store, body)
      return reply(state.store)
    }
    if (u.pathname === '/admin/coffee/menu') return reply({ storeId: store.id, categories: state.categories, products: state.products })
    if (u.pathname.startsWith('/admin/coffee/product')) { const saved = { ...body, id: state.products[0].id }; state.products[0] = saved; return reply(saved) }
    if (u.pathname.startsWith('/admin/coffee/category')) { if (req.method() === 'POST') state.categories.push({ ...body, id: '20' }); else Object.assign(state.categories[0], body); return reply(state.categories.at(-1)) }
    return reply({})
  })
  const page = await context.newPage(); page.setDefaultTimeout(10000)
  await page.goto(`${adminOrigin}/coffee-menu`)
  return { context, page, state }
}
async function adminCheck(name, options, fn) {
  scenarioIndex++
  if (scenarioIndex < Number(process.env.UX_TEST_FROM || 1) || scenarioIndex > Number(process.env.UX_TEST_TO || 999)) return
  const data = await adminScenario(options)
  try { await fn(data); console.log(`PASS ${++passed}: ${name}`) } finally { await data.context.close() }
}
;(async () => {
  browser = await chromium.launch({ channel: process.env.UX_BROWSER_CHANNEL || 'chrome', headless: true })
  await check('首页双入口在窄屏首屏可见，保留真实节气墨字', async ({ page, state }) => {
    await go(page, '/pages/home/index'); await page.locator('.business-entries').waitFor()
    for (const [width, height] of [[360, 640], [375, 812], [390, 844], [430, 932]]) {
      await page.setViewportSize({ width, height }); const entries = await page.locator('.business-entries').boundingBox(), tabs = await page.locator('uni-tabbar').boundingBox()
      assert(entries.y + entries.height < tabs.y, `${width} 首屏可见`)
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    }
    assert.equal(state.requests.filter(r => r.path.includes('/cart')).length, 0)
    await page.setViewportSize({ width: 390, height: 844 }); await shot(page, 'dual-home')
    await page.locator('.coffee-entry').click(); await page.locator('.coffee-card').first().waitFor()
    assert.equal(await page.locator('uni-tabbar:visible').count(), 0); assert.equal(await page.locator('.business-nav uni-button').count(), 4)
  }, false)
  await check('公开菜单不携带用户身份，休息中仍按服务端能力允许加购', async ({ page, state }) => {
    await openSpec(page)
    assert.equal(state.store.businessStatus, 'CLOSED')
    assert(state.requests.filter(r => ['/user/coffee/store', '/user/coffee/menu'].includes(r.path)).every(r => !r.token))
    await page.locator('.sheet-foot uni-button').click(); await page.waitForSelector('.spec-sheet', { state: 'hidden' })
    assert.equal(state.coffee[0].quantity, 3)
    assert.equal(state.requests.find(r => r.method === 'POST' && r.path === '/user/coffee/cart').body.storeId, store.id)
  })
  await check('游客选规格登录返回保留分类、饮品和规格且不自动加购', async ({ page, state }) => {
    await go(page, '/pages/coffee/index'); await page.locator('.category-item').filter({ hasText: '手作' }).click()
    await page.locator('.coffee-card').filter({ hasText: '只有热饮' }).locator('.spec-button').click()
    assert.equal(await page.locator('.options').first().locator('uni-button').count(), 1)
    await page.locator('.sheet-foot uni-button').click(); await page.waitForURL('**/pages/login/index**')
    await page.evaluate(() => { window.uni.login = async () => ({ code: 'local-only' }) })
    await page.getByText('微信一键登录', { exact: true }).click(); await page.waitForURL('**/pages/coffee/index')
    assert((await page.locator('.category-item.active').innerText()).includes('手作'))
    assert((await page.locator('.sheet-title').innerText()).includes('只有热饮'))
    assert((await page.locator('.options .selected').last().innerText()).includes('无糖'))
    assert.equal(state.writes, 0)
    await shot(page, 'dual-coffee-spec')
  }, false)
  await check('有效 SKU 组合决定选项、停售不可选、切换价格不重建弹层', async ({ page }) => {
    await openSpec(page)
    assert.equal(await page.locator('.options').nth(1).locator('uni-button').count(), 2)
    await page.locator('.spec-sheet').evaluate(el => { el.dataset.stable = 'yes' })
    await page.locator('.options').first().getByText('冰', { exact: true }).click()
    await page.locator('.options').nth(1).getByText('无糖', { exact: true }).click()
    assert((await page.locator('.sheet-foot .ink-price').innerText()).includes('22.00'))
    assert.equal(await page.locator('.spec-sheet').getAttribute('data-stable'), 'yes')
    assert.equal(await page.locator('.options').nth(1).locator('uni-button[disabled]').count(), 1)
  })
  await check('所有规格售罄时没有虚构起价或可用加购按钮', async ({ page }) => {
    await go(page, '/pages/coffee/index'); const card = page.locator('.coffee-card').filter({ hasText: '全部售罄' }); await card.waitFor()
    assert((await card.locator('.ink-price').innerText()).includes('已售罄'))
    assert.equal(await card.locator('.spec-button').getAttribute('disabled'), 'true')
  })
  await check('下单关闭时菜单、购物篮弹层、购物车不发起咖啡交易', async ({ page, state }) => {
    state.store.capabilities.placeOrder = false; state.store.capabilities.payOnline = false
    await go(page, '/pages/coffee/index'); await page.locator('.coffee-card').first().waitFor()
    await page.locator('.unavailable-button').dispatchEvent('click'); await page.locator('.basket-summary').click()
    await page.locator('.coffee-drawer .cart-row').first().waitFor(); await page.locator('.unavailable-button').dispatchEvent('click'); await shot(page, 'dual-coffee-basket-disabled')
    await page.locator('.drawer-title uni-button').click(); await page.locator('.business-nav uni-button').filter({ hasText: '购物车' }).click(); await page.locator('.coffee-cart-items .cart-row').first().waitFor()
    assert.equal(await page.locator('.settle-btn').getAttribute('disabled'), 'true'); await page.locator('.settle-btn').dispatchEvent('click')
    assert(state.requests.every(r => !/\/coffee\/(order|pay)|\/order\/(create|.*\/pay)/.test(r.path)))
    await shot(page, 'dual-coffee-cart-disabled')
  })
  await check('两个购物车的总数、勾选金额和结算快照隔离', async ({ page, state }) => {
    await coffeeCart(page)
    assert((await page.getByRole('tab', { name: /^咖啡/ }).innerText()).includes('6'))
    assert((await page.locator('.sum').innerText()).includes('36.00'))
    assert((await page.locator('.sum-label').innerText()).includes('2 杯'))
    await page.getByRole('tab', { name: /^商品/ }).click(); await page.locator('.cart-items .cart-row').waitFor()
    assert((await page.locator('.sum').innerText()).includes('119.80')); await shot(page, 'dual-goods-cart')
    await page.locator('.settle-btn').click(); await page.locator('.review-item').waitFor()
    const preview = state.requests.find(r => r.path === '/user/order/preview')
    assert.deepEqual(preview.body.items, [{ skuId: 11, quantity: 2 }]); assert.equal(state.coffee[0].quantity, 2)
  })
  await check('咖啡全选只改可售行，不可售数量保留但不计金额并可移出', async ({ page, state }) => {
    await coffeeCart(page); await page.locator('.select-all').click()
    await page.waitForFunction(() => document.querySelector('.sum')?.textContent.includes('51.00'))
    assert.equal(state.coffee[1].checked, 1); assert.equal(state.coffee[1].version, 2)
    const unavailable = page.locator('.cart-row').filter({ hasText: '已停售的咖啡' }); assert((await unavailable.innerText()).includes('饮品已下架'))
    assert.equal(await unavailable.locator('[aria-label="增加数量"]').getAttribute('disabled'), 'true')
    await unavailable.locator('.remove').click(); await unavailable.waitFor({ state: 'hidden' })
    assert.equal(state.coffee.length, 2); assert.equal(state.goods[0].quantity, 2)
  })
  await check('数量更新带 expectedVersion 和精确字符串 ID，连续点击串行', async ({ page, state }) => {
    state.writeDelay = 300; await coffeeCart(page)
    const plus = page.locator('[aria-label="增加数量"]').first(); await plus.click(); await plus.dispatchEvent('click'); await waitQuantity(page, 3)
    const writes = state.requests.filter(r => r.method === 'PUT' && r.path.startsWith('/user/coffee/cart/'))
    assert.equal(writes.length, 1); assert.equal(writes[0].path.split('/').pop(), coffeeRows[0].id); assert.equal(writes[0].body.expectedVersion, 3)
  })
  await check('版本冲突重新读取服务端新数量，禁止旧请求覆盖', async ({ page, state }) => {
    await coffeeCart(page); state.conflict = true; await page.locator('[aria-label="增加数量"]').first().click(); await waitQuantity(page, 5)
    await page.getByText('购物篮已发生变化，已重新同步，请确认后再操作', { exact: true }).waitFor(); assert.equal(state.writes, 1)
  })
  await check('POST 超时后只读回结果，不自动重放或提示假成功', async ({ page, state }) => {
    state.lostPost = true; await openSpec(page); await page.locator('.sheet-foot uni-button').click(); await page.waitForFunction(() => document.querySelector('.basket-title')?.textContent.includes('7'), null, { timeout: 22000 })
    await page.waitForTimeout(150); assert.equal(state.writes, 1); assert.equal(state.coffee[0].quantity, 3); assert(await page.locator('.spec-sheet').isVisible())
  })
  await check('服务端拒绝 SKU 后反馈并刷新目录', async ({ page, state }) => {
    state.rejectSku = true; await openSpec(page); await Promise.all([page.waitForResponse(response => new URL(response.url()).pathname === '/user/coffee/menu'), page.locator('.sheet-foot uni-button').click()])
    await page.waitForFunction(() => document.querySelector('.sheet-foot uni-button')?.hasAttribute('disabled'))
    assert(state.requests.filter(r => r.path === '/user/coffee/menu').length >= 2); assert.equal(state.writes, 1)
  })
  await check('账号切换清空两业务缓存，旧账号延迟响应不能落入新账号', async ({ page, state }) => {
    await coffeeCart(page); state.readDelay = 500
    await page.locator('.refresh-control').click(); await page.waitForTimeout(50)
    await page.evaluate(async () => { const storage = await import('/src/utils/storage.ts'); storage.setToken('account-B'); storage.setUserId('2'); const { useCoffeeCartStore } = await import('/src/stores/coffeeCart.ts'); await useCoffeeCartStore().refresh() })
    await page.waitForTimeout(550)
    assert.equal(await page.locator('.coffee-cart-items .cart-row').count(), 0)
    assert((await page.getByRole('tab', { name: /^商品/ }).innerText()).includes('0'))
    assert((await page.getByRole('tab', { name: /^咖啡/ }).innerText()).includes('0'))
    assert((await page.locator('body').innerText()).includes('咖啡篮还空着'))
  })
  await check('过期登录不打断公开浏览，两业务账号数据立即清理', async ({ page, state }) => {
    state.cartError = 401; await go(page, '/pages/coffee/index'); await page.locator('.coffee-card').first().waitFor()
    await page.waitForFunction(() => !localStorage.getItem('jl_user_token'))
    assert(!page.url().includes('/login')); assert((await page.locator('.basket-title').innerText()).includes('0'))
    await page.locator('.business-nav uni-button').filter({ hasText: '购物车' }).click(); await page.getByText('登录后查看购物车', { exact: true }).waitFor()
  })
  await check('未配置、菜单加载失败、真正空菜单分别展示且可重试', async ({ page, state }) => {
    state.storeError = 404; await go(page, '/pages/coffee/index'); await page.getByText('门店筹备中', { exact: true }).waitFor(); assert.equal(await page.locator('.coffee-card').count(), 0)
    state.storeError = 0; state.menuError = 503; await page.locator('.state-action').click(); await page.getByText('菜单暂时未能加载', { exact: true }).waitFor()
    state.menuError = 0; state.menu.products = []; await page.locator('.state-action').click(); await page.getByText('菜单正在准备', { exact: true }).waitFor()
  }, false)
  await check('咖啡篮读取失败不能伪装空状态或零金额', async ({ page, state }) => {
    state.cartError = 503; await go(page, '/pages/cart/index'); await page.getByRole('tab', { name: /^咖啡/ }).click(); await page.getByText('咖啡篮暂时未能同步', { exact: true }).waitFor()
    assert.equal(await page.locator('.settle-bar').count(), 0); assert.equal(await page.getByText('咖啡篮还空着', { exact: true }).count(), 0)
    state.cartError = 0; await page.locator('.coffee-cart-items .state-action').click(); await page.locator('.coffee-cart-items .cart-row').first().waitFor()
  })
  await check('统一订单服务端分页、历史摘要、应付实付语义和精确 ID 跳转', async ({ page, state }) => {
    state.orders = Array.from({ length: 21 }, (_, i) => order(String(9000 + i)))
    state.orders[0].id = '9007199254741010'; state.orders[1].amounts.paidAmountCent = 0
    await go(page, '/pages/orders/index'); await page.locator('.order-card').first().waitFor(); assert.equal(await page.locator('.order-card').count(), 20)
    assert((await page.locator('.order-card').first().innerText()).includes('共 7 件')); assert((await page.locator('.order-card').first().innerText()).includes('订单历史名称'))
    assert((await page.locator('.amount').first().innerText()).includes('应付')); assert((await page.locator('.amount').nth(1).innerText()).includes('实付')); assert((await page.locator('.amount').nth(1).innerText()).includes('0.00'))
    await page.locator('.load-more').click(); await page.waitForFunction(() => document.querySelectorAll('.order-card').length === 21)
    assert.equal(state.orderRequests[1].page, '2'); assert.equal(state.requests.filter(r => /^\/user\/order\//.test(r.path)).length, 0)
    const detailRequest = page.waitForResponse(response => new URL(response.url()).pathname === '/user/order/9007199254741010')
    await page.locator('.order-card').first().click(); await page.waitForURL('**/pages/order-detail/index?id=9007199254741010')
    await detailRequest; assert(state.requests.some(r => r.path === '/user/order/9007199254741010'))
  })
  await check('全部业务含关闭售后分组，具体商品含取消退款，切业务重置筛选', async ({ page, state }) => {
    await go(page, '/pages/orders/index'); await page.locator('.order-card').waitFor()
    assert((await page.locator('.tabs').innerText()).includes('已关闭')); assert((await page.locator('.tabs').innerText()).includes('售后'))
    await page.getByRole('tab', { name: '商品订单', exact: true }).click(); await page.locator('.order-card').waitFor()
    assert((await page.locator('.tabs').innerText()).includes('已取消')); assert((await page.locator('.tabs').innerText()).includes('退款中'))
    await page.locator('.tab').filter({ hasText: '已取消' }).click(); await page.getByText('暂无此状态的订单', { exact: true }).waitFor()
    await page.getByRole('tab', { name: '点单订单', exact: true }).click(); await page.getByText('暂时还没有点单订单', { exact: true }).waitFor()
    assert.equal(await page.locator('.tab.active').innerText(), '全部'); assert((await page.locator('.tabs').innerText()).includes('待接单'))
    assert(!state.orderRequests.at(-1).status); assert.equal(state.orderRequests.at(-1).businessType, 'COFFEE'); await shot(page, 'dual-orders-coffee-empty')
  })
  await check('旧筛选和旧业务响应不能覆盖新订单分类', async ({ page, state }) => {
    state.orderDelay = 400; await go(page, '/pages/orders/index'); await page.locator('.order-card').waitFor()
    await page.locator('.tab').filter({ hasText: '进行中' }).click(); await page.getByRole('tab', { name: '点单订单', exact: true }).click()
    await page.getByText('暂时还没有点单订单', { exact: true }).waitFor(); await page.waitForTimeout(500); assert.equal(await page.locator('.order-card').count(), 0)
    await page.getByRole('tab', { name: '商品订单', exact: true }).click(); await page.locator('.order-card').waitFor(); await shot(page, 'dual-orders-goods')
  })
  await check('窄屏浮栏与四项导航不重叠，模糊回退和减少动效可用', async ({ page }) => {
    await go(page, '/pages/coffee/index'); await page.locator('.coffee-card').first().waitFor()
    for (const width of [360, 375, 390, 430]) {
      await page.setViewportSize({ width, height: 844 }); const bar = await page.locator('.glass-action-bar').boundingBox(), nav = await page.locator('.business-nav').boundingBox()
      assert(bar.y + bar.height <= nav.y); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    }
    await page.addStyleTag({ content: '.glass-action-bar { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; background: #edf3e7 !important; }' })
    assert.equal(await page.locator('.glass-action-bar').evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(237, 243, 231)')
    await page.setViewportSize({ width: 390, height: 844 }); await shot(page, 'dual-coffee-menu-fallback')
  })
  await check('门店禁止浏览或加购时遵循服务端能力，不借营业中授权', async ({ page, state }) => {
    state.store.businessStatus = 'OPEN'; state.store.capabilities.addToCart = false
    await openSpec(page)
    assert.equal(await page.locator('.sheet-foot uni-button').getAttribute('disabled'), 'true')
    await page.locator('.sheet-foot uni-button').dispatchEvent('click'); assert.equal(state.writes, 0)
    await page.locator('.sheet-head uni-button').click(); state.store.capabilities.browse = false
    await page.reload(); await page.getByText('菜单暂时未能加载', { exact: true }).waitFor()
    assert.equal(state.requests.filter(request => request.path === '/user/coffee/menu').length, 1)
  })
  await check('目录停用后重启仍可读取已知门店的旧咖啡篮并移出', async ({ page, state }) => {
    state.storeError = 404; state.coffee.forEach(row => { row.available = false; row.unavailableReason = 'STORE_DISABLED' })
    await page.addInitScript(id => localStorage.setItem('jl_coffee_store_id', id), store.id)
    await coffeeCart(page)
    assert.equal(await page.locator('.coffee-cart-items .cart-row').count(), 3)
    assert((await page.locator('.sum').innerText()).includes('0.00'))
    assert.equal(await page.locator('[aria-label="增加数量"][disabled]').count(), 3)
    await page.locator('.remove').first().click(); await page.waitForFunction(() => document.querySelectorAll('.coffee-cart-items .cart-row').length === 2)
    assert.equal(state.coffee.length, 2)
  })
  await adminCheck('后台服务端拒绝权限时直达 URL 也无维护入口或请求', { forbidden: true }, async ({ page, state }) => {
    await page.getByText('无权限访问此页面', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '新增饮品' }).count(), 0)
    assert(state.calls.every(call => call.path === '/admin/auth/context'))
  })
  await adminCheck('后台只读权限可看停用菜单，不显示写操作', { readonly: true }, async ({ page, state }) => {
    await page.getByRole('heading', { name: '咖啡菜单', exact: true }).waitFor(); await page.getByText('本地回归拿铁', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '新增分类' }).count(), 0); assert(state.calls.every(call => call.method === 'GET'))
  })
  await adminCheck('授权管理员建立首店后不再显示新建店入口', { empty: true }, async ({ page, state }) => {
    await page.getByRole('button', { name: '建立首个门店' }).click(); await page.locator('#name').fill('真实测试门店'); await page.locator('#address').fill('测试地址'); await page.locator('#openingHours').fill('09:00–18:00')
    await page.getByRole('button', { name: '确 定' }).click(); await page.getByText('真实测试门店', { exact: true }).first().waitFor()
    assert.equal(await page.getByRole('button', { name: '建立首个门店' }).count(), 0)
    const body = state.calls.find(call => call.method === 'POST').body
    assert.equal(body.businessStatus, 'CLOSED'); assert(!('placeOrder' in body)); assert(!('payOnline' in body))
  })
  await adminCheck('后台编辑规格保存稳定 ID 与整数分、布尔可售，不发送删除', {}, async ({ page, state }) => {
    await page.getByText('本地回归拿铁', { exact: true }).waitFor()
    const productTable = page.locator('.ant-card').filter({ has: page.locator('.ant-card-head-title').filter({ hasText: '饮品与规格' }) })
    await productTable.getByRole('button', { name: '编辑', exact: true }).click(); await page.locator('#skus_0_priceCent').fill('1999')
    await page.getByRole('button', { name: '确 定' }).click(); await page.locator('.ant-modal').waitFor({ state: 'hidden' })
    const body = state.calls.find(call => call.method === 'PUT' && call.path.includes('/product/')).body
    assert.equal(body.skus[0].id, '1001'); assert.equal(body.skus[0].priceCent, 1999); assert.equal(typeof body.skus[0].available, 'boolean'); assert.equal(typeof body.status, 'number')
    assert(state.calls.every(call => call.method !== 'DELETE')); await shot(page, 'dual-admin-coffee-menu')
  })
  await adminCheck('咖啡售罄恢复快捷操作复用完整饮品保存并保留规格ID', {}, async ({ page, state }) => {
    const row = page.locator('.ant-table-row').filter({ hasText: '本地回归拿铁' })
    const sold = page.waitForResponse(response => response.request().method() === 'PUT' && new URL(response.url()).pathname.includes('/admin/coffee/product/'))
    await row.getByRole('button', { name: '售 罄', exact: true }).first().click()
    await sold
    await row.getByRole('button', { name: '恢复供应', exact: true }).first().waitFor()
    const saved = state.calls.find(call => call.method === 'PUT' && call.path.includes('/product/')).body
    assert.equal(saved.skus[0].available, false); assert.equal(saved.skus[0].id, '1001'); assert.equal(saved.skus[0].priceCent, 1800)
    assert.equal(saved.skus.length, 5); assert(!('stock' in saved))
    const restored = page.waitForResponse(response => response.request().method() === 'PUT' && new URL(response.url()).pathname.includes('/admin/coffee/product/'))
    await row.getByRole('button', { name: '恢复供应', exact: true }).first().click()
    await restored
    await row.getByRole('button', { name: '售 罄', exact: true }).first().waitFor()
    assert.equal(state.calls.filter(call => call.method === 'PUT').at(-1).body.skus[0].available, true)
  })
  console.log(`Dual business UX: ${passed} scenarios passed`)
})().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => { if (browser) await browser.close() })
