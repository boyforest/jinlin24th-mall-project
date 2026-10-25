import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { getCoffeeContext } from '../api/coffee'
import { getToken } from '../utils/auth'
const CoffeeAccess = createContext(null)
export function CoffeeAccessProvider({ children }) {
  const [state, setState] = useState({ loading: true, context: null, error: null })
  const revision = useRef(0)
  const refresh = useCallback(async () => {
    const requestId = ++revision.current, token = getToken()
    setState({ loading: true, context: null, error: null })
    try {
      const context = await getCoffeeContext()
      if (requestId === revision.current && token === getToken()) setState({ loading: false, context, error: null })
    } catch (error) {
      if (requestId === revision.current && token === getToken()) setState({ loading: false, context: null, error })
    }
  }, [])
  useEffect(() => { refresh(); return () => { ++revision.current } }, [refresh])
  const permissions = state.context?.permissions || []
  return <CoffeeAccess.Provider value={{ ...state, refresh, canRead: permissions.includes('coffee:menu:read'), canWrite: permissions.includes('coffee:menu:write') }}>{children}</CoffeeAccess.Provider>
}
export function useCoffeeAccess() { return useContext(CoffeeAccess) }
