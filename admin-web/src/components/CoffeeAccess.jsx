import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { getCoffeeContext, getCoffeeStore } from '../api/coffee'
import { useAdminAccess } from './AdminAccess'
const CoffeeAccess = createContext(null)
export function CoffeeAccessProvider({ children }) {
  const admin = useAdminAccess()
  const [state, setState] = useState({ loading: true, context: null, error: null })
  const [storeId, selectStore] = useState(null), [store, setStore] = useState(null)
  const [connection, setConnection] = useState({ ok: null, lastSuccess: null })
  const revision = useRef(0)
  const refresh = useCallback(async () => {
    const id = ++revision.current
    try {
      const context = await getCoffeeContext()
      // 两处服务端上下文共同约束权限与可见门店。
      const stores = context.stores.filter(s => admin.context.stores.some(a => a.id === s.id))
      if (id !== revision.current) return
      setState({ loading: false, context: { stores, permissions: context.permissions.filter(p => admin.can(p)) }, error: null })
      selectStore(current => stores.some(s => s.id === current) ? current : stores[0]?.id || null)
    } catch (error) { if (id === revision.current) setState({ loading: false, context: null, error }) }
  }, [admin.context])
  useEffect(() => { refresh(); return () => { ++revision.current } }, [refresh])
  const refreshStore = useCallback(async () => {
    if (!storeId) { setStore(null); return }
    const next = await getCoffeeStore(storeId); setStore(next); return next
  }, [storeId])
  useEffect(() => { let active = true; setStore(null); if (storeId) getCoffeeStore(storeId).then(s => { if (active) setStore(s) }).catch(() => { if (active) setConnection(c => ({ ...c, ok: false })) }); return () => { active = false } }, [storeId])
  const can = p => (state.context?.permissions || []).includes(p)
  return <CoffeeAccess.Provider value={{ ...state, refresh, refreshAdmin: admin.refresh, storeId, selectStore, store, refreshStore, connection, setConnection, can, canRead: can('coffee:menu:read'), canWrite: can('coffee:menu:write'), canStore: can('coffee:store:write'), canCreateStore: admin.context.roles.includes('SUPER_ADMIN') && can('coffee:store:write') }}>{children}</CoffeeAccess.Provider>
}
export function useCoffeeAccess() { return useContext(CoffeeAccess) }
