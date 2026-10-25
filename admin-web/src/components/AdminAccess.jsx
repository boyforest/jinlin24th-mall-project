import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Button, Result, Spin } from 'antd'
import { getAdminContext } from '../api/auth'
import { cacheAdminContext, getToken } from '../utils/auth'
const Context = createContext(null)
export function AdminAccessProvider({ children }) {
  const [state, setState] = useState({ loading: true, context: null, error: null })
  const revision = useRef(0)
  const refresh = useCallback(async () => {
    const id = ++revision.current, token = getToken()
    try {
      const context = await getAdminContext()
      if (id === revision.current && token === getToken()) { cacheAdminContext(context); setState({ loading: false, context, error: null }) }
    } catch (error) {
      if (id === revision.current && token === getToken()) setState({ loading: false, context: null, error })
    }
  }, [])
  useEffect(() => {
    refresh()
    const visible = () => { if (!document.hidden) refresh() }
    window.addEventListener('focus', visible); document.addEventListener('visibilitychange', visible)
    return () => { ++revision.current; window.removeEventListener('focus', visible); document.removeEventListener('visibilitychange', visible) }
  }, [refresh])
  const can = permission => (state.context?.permissions || []).includes(permission)
  return <Context.Provider value={{ ...state, can, refresh }}>
    {state.loading ? <Spin fullscreen tip="正在验证权限" /> : state.error ? <Result status={state.error.status === 403 ? '403' : 'error'} title="无法取得后台授权" subTitle={state.error.message} extra={<Button onClick={refresh}>重新验证</Button>} /> : children}
  </Context.Provider>
}
export function useAdminAccess() { return useContext(Context) }
export function PermissionGate({ permission, children }) {
  const access = useAdminAccess()
  return access.can(permission) ? children : <Result status="403" title="无权限访问此页面" subTitle="请联系超级管理员分配业务权限。" />
}
