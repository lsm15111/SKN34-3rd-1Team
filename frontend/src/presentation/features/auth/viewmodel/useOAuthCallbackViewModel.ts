import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import { type OAuthLoginErrorCode, toOAuthLoginErrorCode } from '../../../../domain/entities/OAuthProvider'
import type { CompleteOAuthLogInUseCase } from '../../../../domain/usecases/OAuthLogInUseCases'
import { readReturnPath } from '../../../shared/auth/returnPath'
import { selectIsAuthenticated, signedIn } from '../../../shared/auth/state/authSlice'
import { publicPaths } from '../../../shared/routes/appPaths'

export const oauthCallbackMessages: Record<OAuthLoginErrorCode, string> = {
  PROVIDER_NOT_CONFIGURED: '이 소셜 로그인은 아직 열려 있지 않습니다. 이메일로 로그인해 주세요.',
  PROVIDER_DENIED: '소셜 로그인을 취소했습니다. 다시 시도하거나 이메일로 로그인해 주세요.',
  STATE_MISMATCH: '로그인 요청이 만료됐거나 다른 창에서 시작됐습니다. 처음부터 다시 시도해 주세요.',
  EMAIL_REQUIRED: '이메일 제공에 동의해야 가입할 수 있습니다. 동의 항목에서 이메일을 허용한 뒤 다시 시도해 주세요.',
  EMAIL_NOT_VERIFIED: '같은 이메일로 가입된 계정이 있습니다. 소셜 계정의 이메일이 인증되지 않아 자동으로 연결하지 않았으니 비밀번호로 로그인해 주세요.',
  PROVIDER_UNAVAILABLE: '소셜 로그인 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  ACCOUNT_SUSPENDED: '정지된 계정입니다. 운영자에게 문의해 주세요.',
  RATE_LIMITED: '로그인 시도가 많아 잠시 막혔습니다. 잠시 후 다시 시도해 주세요.',
  FAILED: '소셜 로그인을 마치지 못했습니다. 다시 시도해 주세요.',
} as const

export type OAuthCallbackState =
  | { phase: 'completing' }
  | { phase: 'failed'; code: OAuthLoginErrorCode }

/**
 * 제공처에서 돌아온 콜백 화면의 ViewModel입니다. Core가 `?error=`를 붙였으면 이유를 보여 주고, 아니면 세션 쿠키로
 * 계정을 읽어 Store에 올린 뒤 `?next=`로 이동합니다. 이미 로그인 상태면 바로 이동합니다.
 */
export function useOAuthCallbackViewModel(
  completeUseCase: Pick<CompleteOAuthLogInUseCase, 'execute'> = appContainer.resolve('completeOAuthLogInUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const isAuthenticated = useAppSelector(selectIsAuthenticated)
  const errorCode = toOAuthLoginErrorCode(new URLSearchParams(location.search).get('error'))
  const next = readReturnPath(location.search)
  const [state, setState] = useState<OAuthCallbackState>(errorCode === null ? { phase: 'completing' } : { phase: 'failed', code: errorCode })

  useEffect(() => {
    if (errorCode !== null) return
    if (isAuthenticated) {
      navigate(next, { replace: true })
      return
    }
    const controller = new AbortController()
    void Promise.resolve()
      .then(() => completeUseCase.execute(controller.signal))
      .then((account) => {
        if (controller.signal.aborted) return
        if (account === null) {
          setState({ phase: 'failed', code: 'FAILED' })
          return
        }
        dispatchToStore(signedIn(account))
        navigate(next, { replace: true })
      })
      .catch(() => { if (!controller.signal.aborted) setState({ phase: 'failed', code: 'PROVIDER_UNAVAILABLE' }) })
    return () => controller.abort()
    // 콜백은 한 번만 처리합니다. 이동 뒤 상태가 바뀌어도 다시 읽지 않습니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [errorCode, isAuthenticated])

  return {
    state,
    message: state.phase === 'failed' ? oauthCallbackMessages[state.code] : '소셜 계정으로 로그인하는 중입니다…',
    loginPath: publicPaths.login,
    signupPath: publicPaths.signup,
  }
}
