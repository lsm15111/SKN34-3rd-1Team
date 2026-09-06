import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { AdminAccount, AdminAccountPage } from '../../../../domain/entities/AdminAccount'
import type { ListAdminAccountsUseCase } from '../../../../domain/usecases/ListAdminAccountsUseCase'
import type { RevokeAccountSessionsUseCase } from '../../../../domain/usecases/RevokeAccountSessionsUseCase'

type AdminAccountListUseCase = Pick<ListAdminAccountsUseCase, 'execute'>
type SessionRevokeUseCase = Pick<RevokeAccountSessionsUseCase, 'execute'>

export const adminAccountsPageSize = 20

export const adminAccountsMessages = {
  loadFailed: '회원 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  revoked: (email: string) => `${email} 계정의 모든 세션을 종료했습니다.`,
  revokeNotFound: '이미 삭제된 계정입니다. 목록을 다시 불러옵니다.',
  revokeFailed: '세션을 종료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type AdminAccountsState =
  | { status: 'loading'; page: AdminAccountPage | null }
  | { status: 'ready'; page: AdminAccountPage }
  | { status: 'error'; page: AdminAccountPage | null }

/** 이메일 검색·페이지 이동·세션 강제 종료를 다루는 운영 회원 목록 ViewModel입니다. */
export function useAdminAccountsViewModel(
  listAdminAccountsUseCase: AdminAccountListUseCase = appContainer.resolve('listAdminAccountsUseCase'),
  revokeAccountSessionsUseCase: SessionRevokeUseCase = appContainer.resolve('revokeAccountSessionsUseCase'),
) {
  const [state, setState] = useState<AdminAccountsState>({ status: 'loading', page: null })
  const [emailInput, setEmailInput] = useState('')
  const [appliedEmail, setAppliedEmail] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [revokingAccountId, setRevokingAccountId] = useState<number | null>(null)
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', page: current.page }))

    try {
      const page = await listAdminAccountsUseCase.execute(
        { email: appliedEmail, page: pageIndex, size: adminAccountsPageSize },
        controller.signal,
      )
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'ready', page })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState((current) => ({ status: 'error', page: current.page }))
    }
  }, [appliedEmail, listAdminAccountsUseCase, pageIndex])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  function search() {
    setNotice(null)
    setPageIndex(0)
    setAppliedEmail(emailInput.trim())
  }

  function goToPage(nextPage: number) {
    setNotice(null)
    setPageIndex(Math.max(0, nextPage))
  }

  async function revokeSessions(account: AdminAccount) {
    if (revokingAccountId !== null) return
    setRevokingAccountId(account.id)
    setNotice(null)
    try {
      const result = await revokeAccountSessionsUseCase.execute(account.id)
      if (!isMounted.current) return
      setNotice(result === 'revoked' ? adminAccountsMessages.revoked(account.email) : adminAccountsMessages.revokeNotFound)
      if (result === 'not-found') await load()
    } catch {
      if (!isMounted.current) return
      setNotice(adminAccountsMessages.revokeFailed)
    } finally {
      if (isMounted.current) setRevokingAccountId(null)
    }
  }

  const page = state.page
  const totalPages = page ? Math.max(1, Math.ceil(page.totalCount / page.size)) : 1

  return {
    accounts: page?.items ?? [],
    canGoNext: page ? (page.page + 1) * page.size < page.totalCount : false,
    canGoPrevious: pageIndex > 0,
    emailInput,
    error: state.status === 'error' ? adminAccountsMessages.loadFailed : null,
    goToPage,
    isLoading: state.status === 'loading',
    notice,
    pageIndex,
    reload: load,
    revokeSessions,
    revokingAccountId,
    search,
    setEmailInput,
    totalCount: page?.totalCount ?? 0,
    totalPages,
  }
}
