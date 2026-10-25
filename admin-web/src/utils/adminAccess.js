import { safeRedirectPath } from './safeRedirect.js'
export const routePermissions = {
  '/dashboard': 'dashboard:view', '/products': 'product:manage', '/skus': 'product:manage', '/categories': 'product:manage',
  '/orders': 'order:manage', '/users': 'customer:manage', '/distribution': 'distribution:manage', '/coupons': 'coupon:manage',
  '/marketing-activities': 'marketing:manage', '/warehouses': 'inventory:manage', '/inventory': 'inventory:manage', '/inventory-logs': 'inventory:manage',
  '/customers': 'customer:manage', '/follow-records': 'customer:manage', '/system/accounts': 'admin:accounts:manage',
  '/coffee/workbench': 'coffee:orders:read', '/coffee/menu': 'coffee:menu:read', '/coffee/orders': 'coffee:orders:read', '/coffee-menu': 'coffee:menu:read'
}
export function canVisit(context, path) {
  const permission = routePermissions[path.split(/[?#]/)[0]]
  return !!permission && (context?.permissions || []).includes(permission)
}
export function landingRoute(context, redirect) {
  const path = safeRedirectPath(redirect, '/unauthorized')
  if (canVisit(context, path)) return path
  if (canVisit(context, context?.defaultRoute || '')) return context.defaultRoute
  return Object.keys(routePermissions).find(path => path !== '/coffee-menu' && canVisit(context, path)) || '/unauthorized'
}
