import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'

import { appContainer } from '../../../../app/appContainer'
import { oauthProviderLabels, type OAuthProvider } from '../../../../domain/entities/OAuthProvider'
import type { GetOAuthProvidersUseCase, StartOAuthLogInUseCase } from '../../../../domain/usecases/OAuthLogInUseCases'
import { readReturnPath } from '../../../shared/auth/returnPath'

export type SocialLoginIntent = 'login' | 'signup'

export type SocialLoginButton = {
  provider: OAuthProvider
  label: string
  /** 브라우저를 통째로 보내는 링크입니다. fetch로 열면 제공처 동의 화면이 뜨지 않습니다. */
  href: string
}

/**
 * 로그인·회원가입 화면의 소셜 버튼 ViewModel입니다. Core API에 설정된 제공처만 버튼으로 만들고,
 * 로그인 뒤 돌아갈 경로(`?next=`)를 시작 주소에 실어 보냅니다. 설정 조회에 실패하면 버튼을 그리지 않습니다.
 */
export function useSocialLoginViewModel(
  intent: SocialLoginIntent,
  getProvidersUseCase: Pick<GetOAuthProvidersUseCase, 'execute'> = appContainer.resolve('getOAuthProvidersUseCase'),
  startUseCase: Pick<StartOAuthLogInUseCase, 'execute'> = appContainer.resolve('startOAuthLogInUseCase'),
) {
  const location = useLocation()
  const [providers, setProviders] = useState<OAuthProvider[]>([])

  useEffect(() => {
    const controller = new AbortController()
    void Promise.resolve()
      .then(() => getProvidersUseCase.execute(controller.signal))
      .then((available) => { if (!controller.signal.aborted) setProviders(available) })
      .catch(() => { if (!controller.signal.aborted) setProviders([]) })
    return () => controller.abort()
  }, [getProvidersUseCase])

  const next = readReturnPath(location.search)
  return {
    buttons: providers.map((provider): SocialLoginButton => ({
      provider,
      label: `${oauthProviderLabels[provider]}로 ${intent === 'signup' ? '시작하기' : '계속하기'}`,
      href: startUseCase.execute(provider, next),
    })),
    dividerText: intent === 'signup' ? '또는 소셜 계정으로 바로 시작' : '또는 소셜 계정으로',
    // 소셜로 처음 들어오면 계정이 함께 만들어지므로 약관 안내를 같이 보여 줍니다.
    hint: intent === 'signup'
      ? '소셜 계정의 이메일로 가입되며 비밀번호는 만들지 않습니다.'
      : '같은 이메일의 계정이 있으면 그 계정으로 로그인됩니다.',
  }
}
