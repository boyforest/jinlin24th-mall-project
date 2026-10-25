import type { CoffeeProduct, CoffeeSku } from '@/api/coffee'
import { API_BASE_URL } from '@/config/app'
export const temperatureText = { HOT: '热', ICED: '冰' }
export const sugarText = { REGULAR: '标准糖', LESS: '少糖', NONE: '无糖' }
export function cents(value: number): string {
  if (!Number.isSafeInteger(value) || value < 0) return '—'
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`
}
export function imageUrl(value?: string | null): string {
  if (!value) return ''
  if (value.startsWith('/uploads/')) return `${API_BASE_URL}${value}`
  return /^https:\/\//i.test(value) ? value : ''
}
export function fromPrice(product: CoffeeProduct): number | null {
  const prices = product.skus.filter(sku => sku.available).map(sku => sku.priceCent)
  return prices.length ? Math.min(...prices) : null
}
export function skuDescription(sku: Pick<CoffeeSku, 'temperature' | 'sugar'>): string {
  return `${temperatureText[sku.temperature]} · ${sugarText[sku.sugar]}`
}
export function unavailableText(reason: string | null): string {
  return ({ STORE_DISABLED: '门店目录暂未开放', CATEGORY_DISABLED: '分类已停用', PRODUCT_OFF_SHELF: '饮品已下架', SKU_DISABLED: '规格已停用', SOLD_OUT: '暂时售罄' } as Record<string, string>)[reason || ''] || '暂不可选购'
}
