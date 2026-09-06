import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentPostPage } from '../../../../domain/entities/RecruitmentPost'
import type { SupportProgramIdentity } from '../../../../domain/repositories/SupportProgramRepository'
import type { ListRecruitmentPostsUseCase } from '../../../../domain/usecases/RecruitmentPostUseCases'

type RecruitmentPostsUseCase = Pick<ListRecruitmentPostsUseCase, 'execute'>

export const recruitmentPostsPageSize = 20

type ListState =
  | { status: 'loading'; page: RecruitmentPostPage | null }
  | { status: 'ready'; page: RecruitmentPostPage }
  | { status: 'error'; page: RecruitmentPostPage | null }

/** URL의 공고 필터·페이지를 읽어 모집 중인 글을 조회합니다. 필터·페이지는 URL이 소유해 새로고침·공유가 됩니다. */
export function useRecruitmentPostListViewModel(
  listRecruitmentPostsUseCase: RecruitmentPostsUseCase = appContainer.resolve('listRecruitmentPostsUseCase'),
) {
  const [searchParams, setSearchParams] = useSearchParams()
  const program = readProgramFilter(searchParams)
  const pageIndex = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0)
  const [state, setState] = useState<ListState>({ status: 'loading', page: null })
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)
  const programKey = program ? `${program.sourceCode}:${program.sourceProgramId}` : ''

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', page: current.page }))
    try {
      const page = await listRecruitmentPostsUseCase.execute(
        { program: program ?? undefined, page: pageIndex, size: recruitmentPostsPageSize },
        controller.signal,
      )
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'ready', page })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState((current) => ({ status: 'error', page: current.page }))
    }
    // program 객체는 매 렌더 새로 만들어지므로 문자열 키로 비교합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listRecruitmentPostsUseCase, pageIndex, programKey])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(Math.max(0, nextPage)))
    setSearchParams(next)
  }

  function clearProgramFilter() {
    const next = new URLSearchParams(searchParams)
    next.delete('sourceCode')
    next.delete('sourceProgramId')
    next.delete('page')
    setSearchParams(next)
  }

  const page = state.page
  return {
    canGoNext: page ? (page.page + 1) * page.size < page.totalCount : false,
    canGoPrevious: pageIndex > 0,
    clearProgramFilter,
    goToPage,
    isLoading: state.status === 'loading',
    loadFailed: state.status === 'error',
    pageIndex,
    posts: page?.items ?? [],
    programFilter: program,
    reload: load,
    totalCount: page?.totalCount ?? 0,
  }
}

function readProgramFilter(searchParams: URLSearchParams): SupportProgramIdentity | null {
  const sourceCode = searchParams.get('sourceCode')?.trim()
  const sourceProgramId = searchParams.get('sourceProgramId')?.trim()
  if (!sourceCode || !sourceProgramId) return null
  return { sourceCode, sourceProgramId }
}
