import { createContext, useContext } from 'react'

import { QueryCache } from './queryCache'

/** Provider 없이 쓰는 훅(단위 테스트 등)은 모듈 하나짜리 캐시를 씁니다. 앱은 `QueryCacheProvider`가 렌더마다 새 캐시를 만듭니다. */
export const QueryCacheContext = createContext<QueryCache>(new QueryCache())

export function useQueryCache(): QueryCache {
  return useContext(QueryCacheContext)
}
