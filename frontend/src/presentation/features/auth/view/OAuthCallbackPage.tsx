import { Link } from 'react-router'

import { useOAuthCallbackViewModel } from '../viewmodel/useOAuthCallbackViewModel'
import { AuthBrandPanel } from './AuthBrandPanel'
import { authPageStyles } from './AuthPage.styles'

/** 제공처 동의 뒤 Core API가 돌려보내는 화면입니다. 성공하면 바로 이동하므로 실패 안내가 주된 내용입니다. */
export function OAuthCallbackPage() {
  const { state, message, loginPath, signupPath } = useOAuthCallbackViewModel()

  return (
    <main className={authPageStyles.page}>
      <AuthBrandPanel />

      <section className={authPageStyles.formPanel}>
        <div className={authPageStyles.card} role="region" aria-label="소셜 로그인 결과">
          <div className={authPageStyles.cardHeader}>
            <p className={authPageStyles.cardEyebrow}>소셜 로그인</p>
            <h1 className={authPageStyles.cardTitle}>{state.phase === 'failed' ? '로그인을 마치지 못했습니다' : '잠시만 기다려 주세요'}</h1>
            <p className={authPageStyles.cardDescription} role={state.phase === 'failed' ? 'alert' : 'status'}>{message}</p>
          </div>

          {state.phase === 'failed' ? (
            <>
              <Link className={authPageStyles.submitButton} to={loginPath}>로그인으로 돌아가기</Link>
              <p className={authPageStyles.cardFooter}>
                <Link className={authPageStyles.helperLink} to={signupPath}>이메일로 계정 만들기</Link>
              </p>
            </>
          ) : null}
        </div>
      </section>
    </main>
  )
}
