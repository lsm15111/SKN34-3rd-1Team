import { type ReactNode, useEffect, useMemo } from 'react'

import { useAppSelector } from '../../../app/hooks'
import { selectCurrentAccount } from '../auth/state/authSlice'
import { QueryCache } from './queryCache'
import { QueryCacheContext } from './queryCacheContext'

/** 앱 한 번의 수명과 같은 조회 캐시입니다. 로그인 계정이 바뀌면(로그인·로그아웃) 다른 사람의 목록이 보이지 않도록 전부 비웁니다. */
export function QueryCacheProvider({ children }: { children: ReactNode }) {
  const cache = useMemo(() => new QueryCache(), [])
  const accountEmail = useAppSelector(selectCurrentAccount)?.email ?? null
  useEffect(() => {
    cache.invalidate()
  }, [cache, accountEmail])
  return <QueryCacheContext.Provider value={cache}>{children}</QueryCacheContext.Provider>
}
