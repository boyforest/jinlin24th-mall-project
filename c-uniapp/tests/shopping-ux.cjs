/* Run against local dev:h5 with VITE_API_BASE_URL=http://127.0.0.1:7878.
 * PLAYWRIGHT_MODULE may point to an existing Playwright installation.
 * All API calls are mocked; no real order, login or payment is performed.
 */
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.UX_BASE_URL || 'http://127.0.0.1:5174'
const screenshotDir = process.env.UX_SCREENSHOT_DIR
const image = 'http://127.0.0.1:7878/preview-product.svg'
const product = { id: 1, name: '秋日温养 · 草本茶', subtitle: '草木入盏，慢养秋日', mainImage: image, status: 1,
  effects: '一盏温茶，陪伴秋日的日常滋养。', precautions: '请按包装说明冲泡。', detail: `<p>${'择一味草木，顺时而养。 '.repeat(150)}</p>` }
const initialCart = [
  { id: 1, userId: 1, productId: 1, skuId: 11, productName: product.name, skuName: '日常装 · 6 袋 / 盒', productMainImage: image, quantity: 2, checked: 1, price: 69.9, effectivePrice: 59.9, stock: 20 },
  { id: 2, userId: 1, productId: 1, skuId: 12, productName: product.name, skuName: '分享装 · 12 袋 / 盒', productMainImage: image, quantity: 1, checked: 0, price: 129, effectivePrice: 109, stock: 8 },
]
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600"><rect width="600" height="600" fill="#e6edde"/><ellipse cx="300" cy="510" rx="168" ry="22" fill="#cad6bb"/><path d="M170 120h260l15 385H155z" fill="#f7f2df" stroke="#d2cbb2" stroke-width="3"/><path d="M170 120h260l-9 30H179z" fill="#617c4b"/><rect x="193" y="208" width="214" height="192" rx="2" fill="#edf0df" stroke="#b6c3a4"/><text x="300" y="265" text-anchor="middle" font-family="serif" font-size="27" fill="#596b42">二十四养</text><text x="300" y="330" text-anchor="middle" font-family="serif" font-size="45" fill="#465b36">秋养茶</text><text x="300" y="375" text-anchor="middle" font-size="15" fill="#7b8966">SEASONAL HERBAL TEA</text><path d="M288 200q60-70 54-26t-54 26M288 200q-45-65-52-25t52 25" fill="#8a9e6f" opacity=".7"/></svg>`
let browser
let passed = 0
let scenarioIndex = 0
async function scenario(auth = true) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' })
  await context.addInitScript(authenticated => {
    localStorage.setItem('popup_login_shown', '1')
    if (authenticated) localStorage.setItem('jl_user_token', 'preview-only')
  }, auth)
  const state = { cart: structuredClone(initialCart), failCart: false, failOrders: false, failQuote: false, failMutation: false, partialSelect: false, expired: false, sold: false, emptyOrders: false, mutateDelay: 0, cartReads: 0, writes: 0, creates: 0, quotes: 0, payRequests: 0, createPayAmount: 119.8 }
  await context.route('http://127.0.0.1:7878/**', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method(), pathname = url.pathname
    const body = request.postDataJSON() || {}
    const reply = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 0 : status, message: status === 200 ? 'ok' : '网络暂时不可用，请重试', data }) })
    if (pathname === '/preview-product.svg') return route.fulfill({ contentType: 'image/svg+xml', body: svg })
    if (pathname === '/user/cart/list') { state.cartReads++; return reply(state.cart, state.expired ? 401 : state.failCart ? 503 : 200) }
    if (pathname.startsWith('/user/cart/') && method === 'PUT') {
      state.writes++
      await new Promise(resolve => setTimeout(resolve, state.mutateDelay))
      if (state.failMutation || (state.partialSelect && pathname.endsWith('/2'))) return reply(null, 503)
      const item = state.cart.find(item => item.id === Number(pathname.split('/').pop()))
      Object.assign(item, body)
      return reply(item)
    }
    if (pathname.startsWith('/user/cart/') && method === 'DELETE') {
      state.writes++; state.cart = state.cart.filter(item => item.id !== Number(pathname.split('/').pop())); return reply(true)
    }
    if (pathname === '/user/cart' && method === 'POST') { state.writes++; return reply(state.cart[0]) }
    if (pathname === '/user/product/category/list') return reply([{ id: 1, name: '节气茶饮' }, { id: 2, name: '草本滋养' }])
    if (pathname === '/user/product/list') return reply({ records: [product], total: 1 })
    if (pathname.endsWith('/skus')) return reply([
      { id: 11, productId: 1, skuName: initialCart[0].skuName, price: 69.9, effectivePrice: 69.9, stock: state.sold ? 0 : 20, status: 1 },
      { id: 12, productId: 1, skuName: initialCart[1].skuName, price: 129, effectivePrice: 129, stock: state.sold ? 0 : 8, status: 1 },
    ])
    if (pathname.startsWith('/user/product/')) return reply(product)
    if (pathname === '/user/order/preview') {
      state.quotes++
      if (state.failQuote) return reply(null, 503)
      const items = body.items.map(input => {
        const item = state.cart.find(row => row.skuId === input.skuId)
        return { ...input, productId: 1, productName: product.name, skuName: item.skuName, productImage: image, price: item.effectivePrice, totalPrice: (Math.round(item.effectivePrice * 100) * input.quantity) / 100, stock: item.stock }
      })
      const totalAmount = items.reduce((sum, item) => sum + Math.round(item.totalPrice * 100), 0) / 100
      return reply({ items, totalAmount, payAmount: totalAmount, freightAmount: 0, discountAmount: 0 })
    }
    if (pathname === '/user/order/create') {
      state.creates++; await new Promise(resolve => setTimeout(resolve, 500));
      return reply({ id: 99, orderNo: 'PREVIEW-99', totalAmount: state.createPayAmount, payAmount: state.createPayAmount, status: 0, items: [] })
    }
    if (pathname.endsWith('/pay')) { state.payRequests++; return reply(null, 503) }
    if (pathname === '/user/coffee/store') return reply(null, 404)
    if (pathname === '/user/business-order/list') {
      if (state.failOrders) return reply(null, 503)
      const status = url.searchParams.get('status') || 'PENDING_PAY'
      if (status === 'PENDING_PAY') await new Promise(resolve => setTimeout(resolve, 350))
      return reply({ records: state.emptyOrders ? [] : [{ businessType: 'GOODS', id: '99', orderNo: `订单-${status}`, storeId: null, storeName: '二十四养', fulfillmentType: 'DELIVERY', status, createdAt: '2026-10-24T12:00:00+08:00', itemCount: 1,
        summaryItems: [{ productId: '1', skuId: '11', productName: product.name, specName: '日常装', imageUrl: null, unitPriceCent: 6990, quantity: 1, lineTotalCent: 6990 }],
        amounts: { totalAmountCent: 6990, discountAmountCent: 0, freightAmountCent: 0, payableAmountCent: 0, paidAmountCent: null }, pickupCode: null, pickupDate: null }], total: state.emptyOrders ? 0 : 1, current: 1, size: 20, pages: state.emptyOrders ? 0 : 1 })
    }
    if (pathname.startsWith('/user/order/')) return reply({ id: 99, orderNo: 'PREVIEW-99', status: 0, payAmount: 119.8, totalAmount: 119.8, items: [] })
    if (pathname.endsWith('/login')) return reply({ token: 'preview-authenticated', userId: 1 })
    if (pathname.endsWith('/me')) return reply({ id: 1, nickname: '预览用户' })
    if (pathname.endsWith('/recommender')) return reply(null)
    return reply([])
  })
  const page = await context.newPage()
  await page.clock.setFixedTime(new Date('2026-10-24T12:00:00+08:00'))
  return { context, page, state }
}
async function go(page, route) { await page.goto(`${origin}/#${route}`) }
async function shot(page, name, fullPage = false) {
  if (!screenshotDir) return
  fs.mkdirSync(screenshotDir, { recursive: true })
  await page.screenshot({ path: path.join(screenshotDir, `${name}.png`), fullPage })
}
async function check(name, fn, auth = true) {
  ++scenarioIndex
  if (scenarioIndex < Number(process.env.UX_TEST_FROM || 1) || scenarioIndex > Number(process.env.UX_TEST_TO || 99)) return
  const s = await scenario(auth)
  try { await fn(s); console.log(`PASS ${++passed}: ${name}`) } finally { await s.context.close() }
}
async function openCheckout(page) {
  await go(page, '/pages/cart/index')
  await page.waitForSelector('.cart-row')
  await page.locator('.settle-btn:visible').click()
  await page.waitForSelector('.review-item')
}

;(async () => {
  browser = await chromium.launch({ channel: process.env.UX_BROWSER_CHANNEL || 'chrome', headless: true })
  await check('游客首页刷新无登录跳转和购物车请求', async ({ page, state }) => {
    await go(page, '/pages/goods/index'); await page.waitForSelector('.goods-card'); await page.reload(); await page.waitForSelector('.goods-card')
    assert(!page.url().includes('/login')); assert.equal(state.cartReads, 0)
  }, false)
  await check('加购引导登录后保留首页搜索和滚动位置', async ({ page }) => {
    await go(page, '/pages/goods/index'); await page.waitForSelector('.goods-card')
    await page.locator('.search-input input').fill('秋养')
    await page.evaluate(() => window.scrollTo(0, 420))
    await page.locator('.add-btn').click()
    await page.waitForURL('**/pages/login/index**')
    await page.evaluate(() => { window.uni.login = async () => ({ code: 'mock-weixin-code' }) })
    await page.locator('uni-button').filter({ hasText: '微信一键登录' }).click()
    await page.waitForURL('**/pages/goods/index')
    assert.equal(await page.locator('.search-input input').inputValue(), '秋养')
    assert(await page.evaluate(() => window.scrollY) > 0)
  }, false)
  await check('过期登录的首页购物车同步不强制跳走', async ({ page, state }) => {
    state.expired = true; await go(page, '/pages/goods/index'); await page.waitForSelector('.goods-card')
    await page.waitForTimeout(250); assert(!page.url().includes('/login'))
    assert.equal(await page.evaluate(() => localStorage.getItem('jl_user_token')), null)
  })
  await check('长商品详情购买栏固定且规格金额同步', async ({ page }) => {
    await go(page, '/pages/product-detail/index?id=1'); await page.waitForSelector('.purchase-bar'); await page.waitForTimeout(600)
    const first = await page.locator('.purchase-bar').boundingBox()
    assert(Math.abs(first.y + first.height - 844) < 2)
    await page.locator('.sku').nth(1).click(); assert((await page.locator('.purchase-bar').innerText()).includes('129.00'))
    await page.evaluate(() => window.scrollTo(0, 1500)); assert(Math.abs((await page.locator('.purchase-bar').boundingBox()).y - first.y) < 2)
    for (const width of [320, 375, 390, 430]) {
      await page.setViewportSize({ width, height: 844 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    }
    await page.setViewportSize({ width: 390, height: 844 }); await page.evaluate(() => window.scrollTo(0, 440)); await shot(page, 'shopping-product')
  })
  await check('售罄商品禁用加购和购买', async ({ page, state }) => {
    state.sold = true; await go(page, '/pages/product-detail/index?id=1'); await page.waitForSelector('.purchase-bar')
    assert.equal(await page.locator('.purchase-bar uni-button[disabled]').count(), 2)
    assert((await page.locator('.purchase-bar').innerText()).includes('暂时售罄'))
  })
  await check('购物车按勾选结算，连续点击不并发更新', async ({ page, state }) => {
    state.mutateDelay = 500; await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row')
    assert((await page.locator('.sum').innerText()).includes('119.80'))
    const plus = page.locator('[aria-label="增加数量"]').first()
    await plus.click(); await plus.dispatchEvent('click')
    assert((await page.locator('.settle-bar').innerText()).includes('正在更新'))
    assert.equal(await page.locator('.settle-btn').getAttribute('disabled'), 'true')
    await page.waitForTimeout(650); assert.equal(state.writes, 1); assert((await page.locator('.sum').innerText()).includes('179.70'))
    const bar = await page.locator('.settle-bar').boundingBox(), tabs = await page.locator('uni-tabbar').boundingBox()
    assert(bar.y + bar.height <= tabs.y + 2)
    await shot(page, 'shopping-cart')
  })
  await check('购物车更新失败保留重试入口', async ({ page, state }) => {
    state.failMutation = true; await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row')
    await page.locator('[aria-label="增加数量"]').first().click()
    await page.getByText('购物车暂时未能同步', { exact: true }).waitFor(); assert.equal(state.cart[0].quantity, 2)
    state.failMutation = false; await page.locator('.state-action').click(); await page.waitForSelector('.cart-row')
    assert.equal(await page.locator('.quantity').first().innerText(), '2')
  })
  await check('全选部分失败展示实际服务端结果并可恢复', async ({ page, state }) => {
    state.partialSelect = true; await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row'); await page.locator('.select-all').click()
    await page.getByText('购物车暂时未能同步', { exact: true }).waitFor(); assert.equal(state.cart[1].checked, 0)
    state.partialSelect = false; await page.locator('.state-action').click(); await page.waitForSelector('.cart-row'); await page.locator('.select-all').click()
    await page.waitForFunction(() => document.querySelector('.sum')?.textContent.includes('228.80'))
  })
  await check('两个购物车入口共享勾选数量与结算金额', async ({ page }) => {
    await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row')
    await page.locator('[aria-label="增加数量"]').first().click(); await page.waitForFunction(() => document.querySelector('.sum')?.textContent.includes('179.70'))
    await page.evaluate(() => window.uni.navigateTo({ url: '/pages/goods/index' })); await page.waitForSelector('.floating-cart:visible')
    await page.locator('.floating-cart').click(); await page.locator('.cart-drawer .cart-row').first().waitFor()
    assert.equal(await page.locator('.cart-drawer .quantity').first().innerText(), '3')
    assert((await page.locator('.basket-sub').innerText()).includes('179.70'))
  })
  await check('售罄购物车保留商品且阻止结算', async ({ page, state }) => {
    state.cart[0].stock = 0; await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row')
    assert.equal(await page.locator('.cart-row').count(), 2); assert.equal(await page.locator('.settle-btn').getAttribute('disabled'), 'true')
    assert((await page.locator('.stock-warning').innerText()).includes('暂时售罄'))
  })
  await check('结算展示服务端会员单价、商品信息与固定总额', async ({ page }) => {
    await openCheckout(page)
    const row = page.locator('.review-item'); assert((await row.innerText()).includes(product.name)); assert((await row.innerText()).includes('59.90')); assert((await row.innerText()).includes('6 袋'))
    assert.equal(await row.locator('uni-image').count(), 1)
    assert((await page.locator('.payable-price').innerText()).includes('119.80'))
    await page.waitForTimeout(600); const bar = await page.locator('.submit-bar').boundingBox(); assert(Math.abs(bar.y + bar.height - 844) < 2)
    await page.locator('.review-item .step').last().click(); await page.waitForFunction(() => document.querySelector('.payable-price')?.textContent.includes('179.70'))
    await page.locator('.review-item .step').first().click(); await page.waitForFunction(() => document.querySelector('.payable-price')?.textContent.includes('119.80'))
    await page.evaluate(() => window.scrollTo(0, 290));
    for (const width of [320, 375, 390, 430]) {
      await page.setViewportSize({ width, height: 844 })
      // uni-app rpx 布局在 resize 事件后异步更新，等待实际行宽适配再测量。
      await page.waitForFunction(() => { const row = document.querySelector('.review-item'), plus = document.querySelector('.review-item .step:last-child'); if (!row || !plus) return false; const r = row.getBoundingClientRect(), p = plus.getBoundingClientRect(); return p.right <= r.right + 1 })
      const content = await page.locator('.ink-page-content:visible').boundingBox()
      assert(content.x >= 0 && content.x + content.width <= width + 1)
      const row = await page.locator('.review-item').boundingBox()
      const plus = await page.locator('.review-item .step').last().boundingBox()
      assert(plus.x + plus.width <= row.x + row.width + 1)
    }
    await page.setViewportSize({ width: 390, height: 844 })
    await shot(page, 'shopping-checkout'); await shot(page, 'shopping-checkout-full', true)
  })
  await check('报价失败时无法提交，重试恢复', async ({ page, state }) => {
    state.failQuote = true; await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row'); await page.locator('.settle-btn').click()
    await page.locator('.quote-state uni-button').waitFor(); assert.equal(await page.locator('.submit-bar uni-button').getAttribute('disabled'), 'true')
    state.failQuote = false; await page.locator('.quote-state uni-button').click(); await page.waitForSelector('.review-item')
  })
  await check('创建后锁定按钮，支付失败不会再次创建订单', async ({ page, state }) => {
    await openCheckout(page)
    await page.locator('.input input').nth(0).fill('预览用户')
    await page.locator('.input input').nth(1).fill('13800138000')
    await page.locator('.textarea textarea').first().fill('仅用于本地验证的演示地址')
    await page.locator('.submit-bar uni-button').click(); await page.locator('.submit-bar uni-button').dispatchEvent('click')
    await page.waitForTimeout(850); assert.equal(state.creates, 1); assert.equal(await page.locator('.submit-bar uni-button').getAttribute('disabled'), 'true')
    await page.waitForURL('**/pages/order-detail/index?id=99')
  })
  await check('购物车请求失败与真正为空区分，重试恢复', async ({ page, state }) => {
    state.failCart = true; await go(page, '/pages/cart/index'); await page.getByText('购物车暂时未能同步', { exact: true }).waitFor()
    assert.equal(await page.getByText('篮中还没有养物', { exact: true }).count(), 0); await shot(page, 'shopping-cart-error')
    state.failCart = false; state.cart = []; await page.locator('.state-action').click(); await page.getByText('篮中还没有养物', { exact: true }).waitFor()
    assert.equal(await page.getByText('去逛逛', { exact: true }).count(), 1)
  })
  await check('订单错误重试、筛选空状态、零金额正确展示', async ({ page, state }) => {
    state.failOrders = true; await go(page, '/pages/orders/index'); await page.getByText('清单暂时未能加载', { exact: true }).waitFor(); await shot(page, 'shopping-orders-error')
    state.failOrders = false; await page.locator('.state-action').click(); await page.locator('.order-card').waitFor()
    assert((await page.locator('.amount').innerText()).includes('0.00'))
    await page.getByRole('tab', { name: '商品订单', exact: true }).click(); await page.locator('.order-card').waitFor()
    state.emptyOrders = true; await page.locator('.tab').filter({ hasText: '待发货' }).click(); await page.getByText('暂无此状态的订单', { exact: true }).waitFor()
    await page.locator('.state-action').click(); await page.getByText('还没有养物清单', { exact: true }).waitFor()
  })
  await check('快速切换订单筛选时旧响应不能覆盖新结果', async ({ page }) => {
    await go(page, '/pages/orders/index'); await page.locator('.order-card').waitFor()
    await page.getByRole('tab', { name: '商品订单', exact: true }).click(); await page.locator('.order-card').waitFor()
    await page.locator('.tab').filter({ hasText: '待付款' }).click(); await page.locator('.tab').filter({ hasText: '待发货' }).click()
    await page.waitForTimeout(500); assert.equal(await page.locator('.order-no').innerText(), '订单-PENDING_SHIP')
  })
  await check('报价期间库存减少时显示原因并禁止提交', async ({ page, state }) => {
    await go(page, '/pages/cart/index'); await page.waitForSelector('.cart-row')
    state.cart[0].stock = 1; await page.locator('.settle-btn').click(); await page.locator('.review-item').waitFor()
    assert((await page.locator('.stock-warning').innerText()).includes('仅余 1 件'))
    assert.equal(await page.locator('.submit-bar uni-button').getAttribute('disabled'), 'true')
    await page.locator('.review-item .step').first().click()
    await page.waitForFunction(() => !document.querySelector('.submit-bar uni-button')?.hasAttribute('disabled'))
    assert((await page.locator('.payable-price').innerText()).includes('59.90'))
  })
  await check('成交金额变化先告知并允许查看订单，不自动发起支付', async ({ page, state }) => {
    await openCheckout(page); state.createPayAmount = 129.8
    await page.locator('.input input').nth(0).fill('预览用户')
    await page.locator('.input input').nth(1).fill('13800138000')
    await page.locator('.textarea textarea').first().fill('仅用于本地验证的演示地址')
    await page.locator('.submit-bar uni-button').click()
    await page.getByText('订单金额已更新', { exact: true }).waitFor()
    assert((await page.locator('.uni-modal__bd').innerText()).includes('129.80')); assert.equal(state.payRequests, 0)
    await page.getByText('查看订单', { exact: true }).click()
    await page.waitForURL('**/pages/order-detail/index?id=99'); assert.equal(state.creates, 1); assert.equal(state.payRequests, 0)
  })
  console.log(`Shopping UX: ${passed} scenarios passed`)
})().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => { if (browser) await browser.close() })
