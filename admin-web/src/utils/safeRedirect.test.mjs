import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { safeRedirectPath } from './safeRedirect.js'

/**
 * safeRedirectPath 单元测试（node --test，无需任何测试框架依赖）。
 * <p>
 * 重点覆盖 GHSA-wrjc-x8rr-h8h6 的开放重定向利用串。
 */

describe('safeRedirectPath - 必须拦截的开放重定向载荷', () => {
  it('拦截确认可利用的 /\evil.com（反斜杠被浏览器规范化为 //）', () => {
    // 先证明这个串在浏览器语义下确实是跨域的
    assert.equal(new URL('/\\evil.com', 'https://admin.jinlin24th.cn').href, 'https://evil.com/')
    assert.equal(safeRedirectPath('/\\evil.com'), '/dashboard')
  })

  it('拦截协议相对地址 //evil.com', () => {
    assert.equal(safeRedirectPath('//evil.com'), '/dashboard')
  })

  it('拦截带协议的绝对地址', () => {
    assert.equal(safeRedirectPath('https://evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('http://evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('javascript:alert(1)'), '/dashboard')
    assert.equal(safeRedirectPath('data:text/html,<script>alert(1)</script>'), '/dashboard')
    assert.equal(safeRedirectPath('vbscript:msgbox(1)'), '/dashboard')
  })

  it('拦截空值、纯空白与非字符串', () => {
    assert.equal(safeRedirectPath(''), '/dashboard')
    assert.equal(safeRedirectPath('   '), '/dashboard')
    assert.equal(safeRedirectPath('\t\n'), '/dashboard')
    assert.equal(safeRedirectPath(null), '/dashboard')
    assert.equal(safeRedirectPath(undefined), '/dashboard')
    assert.equal(safeRedirectPath(123), '/dashboard')
    assert.equal(safeRedirectPath({}), '/dashboard')
    assert.equal(safeRedirectPath(), '/dashboard')
  })

  it('拦截不以 / 开头的相对路径', () => {
    assert.equal(safeRedirectPath('dashboard'), '/dashboard')
    assert.equal(safeRedirectPath('evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('./dashboard'), '/dashboard')
    assert.equal(safeRedirectPath('\\evil.com'), '/dashboard')
  })

  it('拦截包含反斜杠的任意路径', () => {
    assert.equal(safeRedirectPath('/a\\b'), '/dashboard')
    assert.equal(safeRedirectPath('/dashboard\\'), '/dashboard')
    assert.equal(safeRedirectPath('/\\/evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('/%5Cevil.com'), '/dashboard')
  })

  it('拦截任意位置的 //', () => {
    assert.equal(safeRedirectPath('/a//b'), '/dashboard')
    assert.equal(safeRedirectPath('/dashboard//'), '/dashboard')
    assert.equal(safeRedirectPath('/order?next=//evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('/%2f%2fevil.com'), '/dashboard')
  })

  it('拦截控制字符与首尾空白', () => {
    assert.equal(safeRedirectPath('/dash\u0000board'), '/dashboard')
    assert.equal(safeRedirectPath('/dashboard\n'), '/dashboard')
    assert.equal(safeRedirectPath(' /dashboard'), '/dashboard')
    assert.equal(safeRedirectPath('/dashboard '), '/dashboard')
  })

  it('支持自定义 fallback', () => {
    assert.equal(safeRedirectPath('/\\evil.com', '/login'), '/login')
    assert.equal(safeRedirectPath('https://evil.com', '/login'), '/login')
  })

  it('fallback 本身也会被校验，污染 fallback 无法绕过', () => {
    assert.equal(safeRedirectPath('/\\evil.com', '//evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('/\\evil.com', 'https://evil.com'), '/dashboard')
    assert.equal(safeRedirectPath('/\\evil.com', '/a\\b'), '/dashboard')
    assert.equal(safeRedirectPath('/\\evil.com', ''), '/dashboard')
    assert.equal(safeRedirectPath('/\\evil.com'), '/dashboard')
  })
})

describe('safeRedirectPath - 必须放行的合法相对路径', () => {
  it('放行后台常用路径', () => {
    assert.equal(safeRedirectPath('/dashboard'), '/dashboard')
    assert.equal(safeRedirectPath('/order'), '/order')
    assert.equal(safeRedirectPath('/order?status=10'), '/order?status=10')
  })

  it('放行多级路径、查询串与锚点', () => {
    assert.equal(safeRedirectPath('/order/123'), '/order/123')
    assert.equal(safeRedirectPath('/order/123?page=2&size=20'), '/order/123?page=2&size=20')
    assert.equal(safeRedirectPath('/order?keyword=%E5%BC%A0%E4%B8%89'), '/order?keyword=%E5%BC%A0%E4%B8%89')
    assert.equal(safeRedirectPath('/order#detail'), '/order#detail')
    assert.equal(safeRedirectPath('/'), '/')
  })
})
