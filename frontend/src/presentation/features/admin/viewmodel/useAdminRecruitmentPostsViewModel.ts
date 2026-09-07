import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { AdminRecruitmentPost, AdminRecruitmentPostPage } from '../../../../domain/entities/AdminRecruitmentPost'
import type { RecruitmentPostStatus } from '../../../../domain/entities/RecruitmentPost'
import type { ModerateRecruitmentPostResult } from '../../../../domain/repositories/AdminRepository'
import type {
  CloseRecruitmentPostByAdminUseCase,
  HideRecruitmentPostUseCase,
  ListAdminRecruitmentPostsUseCase,
  UnhideRecruitmentPostUseCase,
} from '../../../../domain/usecases/AdminRecruitmentPostUseCases'

type ListUseCase = Pick<ListAdminRecruitmentPostsUseCase, 'execute'>
type HideUseCase = Pick<HideRecruitmentPostUseCase, 'execute'>
type UnhideUseCase = Pick<UnhideRecruitmentPostUseCase, 'execute'>
type CloseUseCase = Pick<CloseRecruitmentPostByAdminUseCase, 'execute'>

export const adminRecruitmentPostsPageSize = 20
export const ADMIN_REASON_MAX_LENGTH = 200

export type AdminPostAction = 'hide' | 'close'
export type AdminStatusFilter = RecruitmentPostStatus | 'ALL'

export const adminRecruitmentPostsMessages = {
  loadFailed: '모집글 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  reasonRequired: '사유를 입력해 주세요.',
  reasonTooLong: `사유는 ${ADMIN_REASON_MAX_LENGTH}자 이하여야 합니다.`,
  hidden: (title: string) => `"${title}" 모집글을 숨겼습니다. 작성 기업 외에는 보이지 않습니다.`,
  unhidden: (title: string) => `"${title}" 모집글을 다시 공개했습니다.`,
  closed: (title: string) => `"${title}" 모집글을 마감했습니다.`,
  alreadyHidden: '이미 숨긴 모집글입니다. 목록을 새로고침했습니다.',
  notHidden: '숨기지 않은 모집글입니다. 목록을 새로고침했습니다.',
  notOpen: '이미 종료된 모집글입니다. 목록을 새로고침했습니다.',
  notFound: '삭제된 모집글입니다. 목록을 새로고침했습니다.',
  actionFailed: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type ListState =
  | { status: 'loading'; page: AdminRecruitmentPostPage | null }
  | { status: 'ready'; page: AdminRecruitmentPostPage }
  | { status: 'error'; page: AdminRecruitmentPostPage | null }

/** 상태 필터·페이지 이동과 숨김·해제·강제 마감을 다루는 운영 모집글 목록 ViewModel입니다. 사유는 조치 확인 단계에서 받습니다. */
export function useAdminRecruitmentPostsViewModel(
  listUseCase: ListUseCase = appContainer.resolve('listAdminRecruitmentPostsUseCase'),
  hideUseCase: HideUseCase = appContainer.resolve('hideRecruitmentPostUseCase'),
  unhideUseCase: UnhideUseCase = appContainer.resolve('unhideRecruitmentPostUseCase'),
  closeUseCase: CloseUseCase = appContainer.resolve('closeRecruitmentPostByAdminUseCase'),
) {
  const [state, setState] = useState<ListState>({ status: 'loading', page: null })
  const [statusFilter, setStatusFilter] = useState<AdminStatusFilter>('ALL')
  const [pageIndex, setPageIndex] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<{ post: AdminRecruitmentPost; action: AdminPostAction } | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)
  const [actingPostId, setActingPostId] = useState<number | null>(null)
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', page: current.page }))
    try {
      const page = await listUseCase.execute(
        { status: statusFilter === 'ALL' ? undefined : statusFilter, page: pageIndex, size: adminRecruitmentPostsPageSize },
        controller.signal,
      )
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'ready', page })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState((current) => ({ status: 'error', page: current.page }))
    }
  }, [listUseCase, pageIndex, statusFilter])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  function changeStatusFilter(next: AdminStatusFilter) {
    setNotice(null)
    setPageIndex(0)
    setStatusFilter(next)
  }

  function goToPage(nextPage: number) {
    setNotice(null)
    setPageIndex(Math.max(0, nextPage))
  }

  /** 숨김·마감은 사유가 필요하므로 먼저 확인 단계를 엽니다. */
  function beginAction(post: AdminRecruitmentPost, action: AdminPostAction) {
    setNotice(null)
    setReason('')
    setReasonError(null)
    setPendingAction({ post, action })
  }

  function cancelAction() {
    setPendingAction(null)
    setReason('')
    setReasonError(null)
  }

  async function confirmAction() {
    if (!pendingAction || actingPostId !== null) return
    const trimmed = reason.trim()
    if (!trimmed) return setReasonError(adminRecruitmentPostsMessages.reasonRequired)
    if (trimmed.length > ADMIN_REASON_MAX_LENGTH) return setReasonError(adminRecruitmentPostsMessages.reasonTooLong)
    setReasonError(null)
    const { post, action } = pendingAction
    await run(post, () => action === 'hide' ? hideUseCase.execute(post.id, trimmed) : closeUseCase.execute(post.id, trimmed), (title) =>
      action === 'hide' ? adminRecruitmentPostsMessages.hidden(title) : adminRecruitmentPostsMessages.closed(title))
  }

  async function unhide(post: AdminRecruitmentPost) {
    await run(post, () => unhideUseCase.execute(post.id), adminRecruitmentPostsMessages.unhidden)
  }

  async function run(
    post: AdminRecruitmentPost,
    action: () => Promise<ModerateRecruitmentPostResult>,
    successMessage: (title: string) => string,
  ) {
    if (actingPostId !== null) return
    setActingPostId(post.id)
    setNotice(null)
    try {
      const result = await action()
      if (!isMounted.current) return
      if (result.outcome === 'done') {
        setPendingAction(null)
        setReason('')
        setState((current) => current.page
          ? { status: 'ready', page: { ...current.page, items: current.page.items.map((item) => item.id === post.id ? result.post : item) } }
          : current)
        setNotice(successMessage(post.title))
        return
      }
      setPendingAction(null)
      setNotice({
        'already-hidden': adminRecruitmentPostsMessages.alreadyHidden,
        'not-hidden': adminRecruitmentPostsMessages.notHidden,
        'not-open': adminRecruitmentPostsMessages.notOpen,
        'not-found': adminRecruitmentPostsMessages.notFound,
      }[result.outcome])
      await load()
    } catch {
      if (!isMounted.current) return
      setNotice(adminRecruitmentPostsMessages.actionFailed)
    } finally {
      if (isMounted.current) setActingPostId(null)
    }
  }

  const page = state.page
  return {
    actingPostId,
    beginAction,
    cancelAction,
    canGoNext: page ? (page.page + 1) * page.size < page.totalCount : false,
    canGoPrevious: pageIndex > 0,
    changeStatusFilter,
    confirmAction,
    error: state.status === 'error' ? adminRecruitmentPostsMessages.loadFailed : null,
    goToPage,
    isLoading: state.status === 'loading',
    notice,
    pageIndex,
    pendingAction,
    posts: page?.items ?? [],
    reason,
    reasonError,
    reload: load,
    setReason,
    statusFilter,
    totalCount: page?.totalCount ?? 0,
    totalPages: page ? Math.max(1, Math.ceil(page.totalCount / page.size)) : 1,
    unhide,
  }
}
