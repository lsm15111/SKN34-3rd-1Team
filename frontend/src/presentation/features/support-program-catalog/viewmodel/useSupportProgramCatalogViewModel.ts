import { useCallback } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { SupportProgramCatalog, SupportProgramCatalogFilters } from '../../../../domain/entities/SupportProgramCatalog'
import type { BrowseSupportProgramsUseCase } from '../../../../domain/usecases/BrowseSupportProgramsUseCase'
import { useKeyedQuery } from '../../../shared/data/useKeyedQuery'
import {
  defaultCatalogApplicantTypes, defaultCatalogCategories, defaultCatalogFounderAges,
  defaultCatalogRegions, defaultCatalogStartupStages, mergeCatalogFilterOptions,
} from './catalogFilterOptions'

/** 필터 검색 목록의 캐시 이름공간입니다. 공고는 서버가 주기적으로 갱신하므로 짧게만 재사용합니다. */
export const supportProgramCatalogCacheNamespace = 'support-program-catalog'

/**
 * 조건별 공고 목록입니다. 조건이 바뀌어도 직전 결과를 돌려주고(`isRefreshing`), 뒤로 가기·재방문은 캐시를 먼저 보여 줍니다.
 * 필터 선택지는 응답 전에도 기본 목록으로 쓸 수 있고, 응답이 오면 서버의 추가 분류를 뒤에 붙입니다.
 */
export function useSupportProgramCatalogViewModel(filters: SupportProgramCatalogFilters,
  useCase: Pick<BrowseSupportProgramsUseCase, 'execute'> = appContainer.resolve('browseSupportProgramsUseCase')) {
  const key = JSON.stringify(filters)
  const fetch = useCallback((signal: AbortSignal) => useCase.execute(JSON.parse(key) as SupportProgramCatalogFilters, signal), [key, useCase])
  const query = useKeyedQuery<SupportProgramCatalog>({ key, fetch, timeoutMs: 10_000, cache: supportProgramCatalogCacheNamespace })
  const { data } = query
  return {
    phase: query.phase,
    data,
    isRefreshing: query.isRefreshing,
    isStale: query.isStale,
    regions: mergeCatalogFilterOptions(defaultCatalogRegions, data?.regions),
    categories: mergeCatalogFilterOptions(defaultCatalogCategories, data?.categories),
    startupStages: mergeCatalogFilterOptions(defaultCatalogStartupStages, data?.startupStages),
    applicantTypes: mergeCatalogFilterOptions(defaultCatalogApplicantTypes, data?.applicantTypes),
    founderAges: mergeCatalogFilterOptions(defaultCatalogFounderAges, data?.founderAges),
    retry: query.retry,
  }
}
