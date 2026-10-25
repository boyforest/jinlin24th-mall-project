export function validCoffeeImage(value) {
  return !value || /^https:\/\/[^\s]+$/i.test(value) || /^\/uploads\/[^\s]+$/.test(value)
}
export function coffeeProductPayload(values, originalSkus = []) {
  const seen = new Set()
  const skus = (values.skus || []).map(sku => {
    const combination = `${sku.temperature}:${sku.sugar}`
    if (seen.has(combination)) throw new Error('温度与甜度组合不能重复')
    seen.add(combination)
    if (!['HOT', 'ICED'].includes(sku.temperature) || !['REGULAR', 'LESS', 'NONE'].includes(sku.sugar)) throw new Error('请选择有效温度与甜度')
    if (!Number.isSafeInteger(sku.priceCent) || sku.priceCent <= 0) throw new Error('规格价格须为大于 0 的整数分')
    if (sku.status !== 0 && sku.status !== 1) throw new Error('请选择规格状态')
    if (typeof sku.available !== 'boolean') throw new Error('请选择可售状态')
    const previous = originalSkus.find(row => row.id === sku.id) || originalSkus.find(row => row.temperature === sku.temperature && row.sugar === sku.sugar)
    return { ...(previous ? { id: previous.id } : {}), name: sku.name.trim(), temperature: sku.temperature, sugar: sku.sugar, priceCent: sku.priceCent, status: sku.status, available: sku.available }
  })
  if (!skus.length) throw new Error('请至少保留一个规格，可将其停用')
  if (!validCoffeeImage(values.imageUrl)) throw new Error('图片须为 HTTPS 地址或 /uploads/ 路径')
  return { categoryId: values.categoryId, name: values.name.trim(), imageUrl: values.imageUrl || null, description: values.description || '', sort: values.sort, status: values.status, skus }
}
