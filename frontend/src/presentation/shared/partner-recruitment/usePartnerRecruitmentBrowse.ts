import { useCallback, useEffect, useState } from 'react'

import { appContainer } from '../../../app/appContainer'
import type { PartnerRecruitment, PartnerRecruitmentSummary } from '../../../domain/entities/PartnerRecruitment'
import type { PartnerRecruitmentPage, PartnerRecruitmentQuery } from '../../../domain/entities/PartnerRecruitmentQuery'
import type { BrowsePartnerRecruitmentsUseCase, GetPartnerRecruitmentDetailUseCase } from '../../../domain/usecases/PartnerRecruitmentUseCases'
import { useKeyedQuery } from '../data/useKeyedQuery'

export type RecruitmentLoadState<Value> =
  | { phase: 'loading'; value: Value | null }
  | { phase: 'ready'; value: Value }
  | { phase: 'failed'; value: Value | null }

const REQUEST_TIMEOUT_MS = 10_000

/** 모집글 목록 캐시 이름공간입니다. 모집글을 올리거나 고치거나 마감하면 이 이름으로 비웁니다. */
export const partnerRecruitmentListCacheNamespace = 'partner-recruitments'

/**
 * 공개·내부 파트너 모집 목록이 함께 쓰는 조회 훅입니다. 조건이 바뀌면 이전 요청을 취소하고 다시 읽되 직전 결과를 유지하고(`isRefreshing`),
 * 뒤로 가기·재방문은 캐시를 먼저 보여 줍니다. 실패해도 마지막 결과를 유지한 채 실패로 표시합니다. 특정 페이지의 ViewModel이 아니므로 shared에 둡니다.
 */
export function usePartnerRecruitmentBrowse(
  query: PartnerRecruitmentQuery,
  useCase: Pick<BrowsePartnerRecruitmentsUseCase, 'execute'> = appContainer.resolve('browsePartnerRecruitmentsUseCase'),
) {
  const key = JSON.stringify(query)
  const fetch = useCallback((signal: AbortSignal) => useCase.execute(JSON.parse(key) as PartnerRecruitmentQuery, signal), [key, useCase])
  const result = useKeyedQuery<PartnerRecruitmentPage<PartnerRecruitmentSummary>>({
    key, fetch, timeoutMs: REQUEST_TIMEOUT_MS, cache: partnerRecruitmentListCacheNamespace,
  })
  return {
    phase: result.phase,
    page: result.data,
    isRefreshing: result.isRefreshing,
    retry: result.retry,
  }
}

/** 공개·내부 모집글 상세가 함께 쓰는 조회 훅입니다. 없는 글(null)과 실패를 구분합니다. */
export function usePartnerRecruitmentDetail(
  id: number | null,
  useCase: Pick<GetPartnerRecruitmentDetailUseCase, 'execute'> = appContainer.resolve('getPartnerRecruitmentDetailUseCase'),
) {
  const [state, setState] = useState<{ id: number | null; phase: 'loading' | 'ready' | 'missing' | 'failed'; recruitment: PartnerRecruitment | null }>({
    id, phase: id === null ? 'missing' : 'loading', recruitment: null,
  })

  useEffect(() => {
    if (id === null) {
      setState({ id, phase: 'missing', recruitment: null })
      return
    }
    const controller = new AbortController()
    let current = true
    setState({ id, phase: 'loading', recruitment: null })
    void Promise.resolve().then(() => useCase.execute(id, controller.signal))
      .then((recruitment) => {
        if (!current || controller.signal.aborted) return
        setState({ id, phase: recruitment === null ? 'missing' : 'ready', recruitment })
      })
      .catch(() => { if (current && !controller.signal.aborted) setState({ id, phase: 'failed', recruitment: null }) })
    return () => { current = false; controller.abort() }
  }, [id, useCase])

  return state.id === id ? state : { id, phase: 'loading' as const, recruitment: null }
}

/** `?recruitmentId=`가 하나뿐이고 양의 정수일 때만 상세를 찾습니다. 그 밖에는 다른 글로 대체하지 않습니다. */
export function readRecruitmentId(values: string[]): number | null {
  if (values.length !== 1 || !/^\d+$/.test(values[0]!)) return null
  const id = Number(values[0])
  return Number.isSafeInteger(id) && id > 0 ? id : null
}
