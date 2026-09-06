import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentPost } from '../../../../domain/entities/RecruitmentPost'
import type { ListMyRecruitmentPostsUseCase } from '../../../../domain/usecases/RecruitmentPostUseCases'

type MyPostsUseCase = Pick<ListMyRecruitmentPostsUseCase, 'execute'>

type MyPostsState =
  | { status: 'loading'; posts: RecruitmentPost[] }
  | { status: 'ready'; posts: RecruitmentPost[] }
  | { status: 'error'; posts: RecruitmentPost[] }

/** 내 기업이 쓴 모집글을 상태와 무관하게 모두 조회합니다. */
export function useMyRecruitmentPostsViewModel(
  listMyRecruitmentPostsUseCase: MyPostsUseCase = appContainer.resolve('listMyRecruitmentPostsUseCase'),
) {
  const [state, setState] = useState<MyPostsState>({ status: 'loading', posts: [] })
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', posts: current.posts }))
    try {
      const posts = await listMyRecruitmentPostsUseCase.execute(controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'ready', posts })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState((current) => ({ status: 'error', posts: current.posts }))
    }
  }, [listMyRecruitmentPostsUseCase])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  return {
    isLoading: state.status === 'loading',
    loadFailed: state.status === 'error',
    posts: state.posts,
    reload: load,
  }
}
