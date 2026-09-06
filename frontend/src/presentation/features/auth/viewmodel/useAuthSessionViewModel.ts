import { useEffect } from 'react'

import { appContainer } from '../../../../app/appContainer'
import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import type { GetCurrentAccountUseCase } from '../../../../domain/usecases/GetCurrentAccountUseCase'
import type { LogOutUseCase } from '../../../../domain/usecases/LogOutUseCase'
import {
  selectAuthStatus,
  selectCurrentAccount,
  selectIsAdmin,
  selectIsAuthenticated,
  sessionRestored,
  signedOut,
} from '../state/authSlice'

type CurrentAccountUseCase = Pick<GetCurrentAccountUseCase, 'execute'>
type SignOutUseCase = Pick<LogOutUseCase, 'execute'>

/** 앱 진입 시 한 번, 브라우저에 저장된 세션 토큰으로 로그인 상태를 복원합니다. */
export function useRestoreAuthSession(
  getCurrentAccountUseCase: CurrentAccountUseCase = appContainer.resolve('getCurrentAccountUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const status = useAppSelector(selectAuthStatus)

  useEffect(() => {
    if (status !== 'unknown') return

    const controller = new AbortController()
    getCurrentAccountUseCase.execute(controller.signal)
      .then((account) => {
        if (controller.signal.aborted) return
        dispatchToStore(sessionRestored(account))
      })
      .catch(() => {
        // 복원 실패(네트워크·서버 오류)는 비로그인으로 시작하고, 저장된 토큰은 다음 시작에 다시 시도합니다.
        if (controller.signal.aborted) return
        dispatchToStore(sessionRestored(null))
      })

    return () => controller.abort()
  }, [dispatchToStore, getCurrentAccountUseCase, status])
}

/** 현재 로그인 상태를 읽고 로그아웃하는 화면 공통 ViewModel입니다. */
export function useAuthSessionViewModel(
  logOutUseCase: SignOutUseCase = appContainer.resolve('logOutUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const status = useAppSelector(selectAuthStatus)
  const account = useAppSelector(selectCurrentAccount)
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  const isAdmin = useAppSelector(selectIsAdmin)

  async function logOut() {
    try {
      await logOutUseCase.execute()
    } catch {
      // 서버 세션 삭제에 실패해도 브라우저 토큰은 이미 지워졌으므로 화면은 로그아웃 상태가 됩니다.
    } finally {
      dispatchToStore(signedOut())
    }
  }

  return {
    account,
    isAdmin,
    isAuthenticated,
    logOut,
    status,
  }
}
