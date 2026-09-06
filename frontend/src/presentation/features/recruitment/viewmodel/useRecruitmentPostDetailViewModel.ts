import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentPost } from '../../../../domain/entities/RecruitmentPost'
import type {
  CloseRecruitmentPostUseCase,
  GetRecruitmentPostUseCase,
} from '../../../../domain/usecases/RecruitmentPostUseCases'

type PostUseCase = Pick<GetRecruitmentPostUseCase, 'execute'>
type CloseUseCase = Pick<CloseRecruitmentPostUseCase, 'execute'>

export const recruitmentDetailMessages = {
  closed: '모집을 마감했습니다. 목록에는 더 이상 표시되지 않습니다.',
  closeNotOwner: '작성 기업만 모집을 마감할 수 있습니다.',
  closeNotOpen: '이미 종료된 모집입니다.',
  closeFailed: '모집 마감을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type DetailState =
  | { status: 'loading'; post: null }
  | { status: 'ready'; post: RecruitmentPost }
  | { status: 'not-found'; post: null }
  | { status: 'failed'; post: null }

/** 모집글 하나를 조회하고, 작성 기업의 조기 마감을 처리합니다. */
export function useRecruitmentPostDetailViewModel(
  postId: number,
  getRecruitmentPostUseCase: PostUseCase = appContainer.resolve('getRecruitmentPostUseCase'),
  closeRecruitmentPostUseCase: CloseUseCase = appContainer.resolve('closeRecruitmentPostUseCase'),
) {
  const [state, setState] = useState<DetailState>({ status: 'loading', post: null })
  const [notice, setNotice] = useState<string | null>(null)
  const [isClosing, setIsClosing] = useState(false)
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState({ status: 'loading', post: null })
    try {
      const post = await getRecruitmentPostUseCase.execute(postId, controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      setState(post ? { status: 'ready', post } : { status: 'not-found', post: null })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'failed', post: null })
    }
  }, [getRecruitmentPostUseCase, postId])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  async function closeEarly() {
    if (isClosing || state.status !== 'ready') return
    setIsClosing(true)
    setNotice(null)
    try {
      const result = await closeRecruitmentPostUseCase.execute(postId)
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'closed':
          setState({ status: 'ready', post: result.post })
          setNotice(recruitmentDetailMessages.closed)
          return
        case 'not-owner':
          setNotice(recruitmentDetailMessages.closeNotOwner)
          return
        case 'not-open':
          setNotice(recruitmentDetailMessages.closeNotOpen)
          return
        case 'not-found':
          setState({ status: 'not-found', post: null })
          return
      }
    } catch {
      if (!isMounted.current) return
      setNotice(recruitmentDetailMessages.closeFailed)
    } finally {
      if (isMounted.current) setIsClosing(false)
    }
  }

  return {
    closeEarly,
    isClosing,
    notice,
    reload: load,
    state,
  }
}
