import { Link, Navigate, useLocation } from 'react-router'

import { readReturnPath } from '../viewmodel/returnPath'
import { useAuthSessionViewModel } from '../viewmodel/useAuthSessionViewModel'
import { useLoginViewModel } from '../viewmodel/useLoginViewModel'
import { authPageStyles } from './AuthPage.styles'

export function LoginPage() {
  const session = useAuthSessionViewModel()
  const location = useLocation()

  if (session.isAuthenticated) {
    return <Navigate replace to={readReturnPath(location.state)} />
  }

  return <LoginForm />
}

function LoginForm() {
  const { errors, isSubmitting, registerField, submit, submitError } = useLoginViewModel()

  return (
    <main className={authPageStyles.page}>
      <div className={authPageStyles.shell}>
        <AuthIntroduction />
        <section className={authPageStyles.formPanel} aria-labelledby="login-title">
          <Link className={authPageStyles.backLink} to="/">← 검색으로 돌아가기</Link>
          <div className={authPageStyles.formHeader}>
            <h1 id="login-title" className={authPageStyles.formTitle}>다시 만나서 반가워요</h1>
            <p className={authPageStyles.formDescription}>
              GovBiz 계정으로 기업에 맞는 지원사업을 계속 찾아보세요.
            </p>
          </div>
          <form className={authPageStyles.form} noValidate onSubmit={submit}>
            <label className={authPageStyles.field} htmlFor="login-email">
              이메일
              <input
                id="login-email"
                className={authPageStyles.input}
                type="email"
                autoComplete="email"
                aria-invalid={errors.email ? true : undefined}
                {...registerField('email')}
              />
              {errors.email?.message
                ? <span className={authPageStyles.fieldError}>{errors.email.message}</span>
                : null}
            </label>
            <label className={authPageStyles.field} htmlFor="login-password">
              비밀번호
              <input
                id="login-password"
                className={authPageStyles.input}
                type="password"
                autoComplete="current-password"
                aria-invalid={errors.password ? true : undefined}
                {...registerField('password')}
              />
              {errors.password?.message
                ? <span className={authPageStyles.fieldError}>{errors.password.message}</span>
                : null}
            </label>
            {submitError ? <p className={authPageStyles.error} role="alert">{submitError}</p> : null}
            <button className={authPageStyles.submitButton} type="submit" disabled={isSubmitting}>
              {isSubmitting ? '로그인 중…' : '로그인'}
            </button>
            <p className={authPageStyles.switchLink}>
              아직 계정이 없나요? <Link className={authPageStyles.switchLinkAnchor} to="/signup">간편 회원가입</Link>
            </p>
          </form>
        </section>
      </div>
    </main>
  )
}

function AuthIntroduction() {
  return (
    <section className={authPageStyles.introduction}>
      <div className={authPageStyles.brand}>
        <span className={authPageStyles.brandMark}>G</span>
        <div>
          <strong className={authPageStyles.brandName}>GovBiz</strong>
          <span className={authPageStyles.brandSubtitle}>지원사업 탐색 도우미</span>
        </div>
      </div>
      <div className={authPageStyles.introductionCopy}>
        <p className={authPageStyles.eyebrow}>MY GOVBIZ</p>
        <h2 className={authPageStyles.introductionTitle}>내 기업에 맞는 기회를 더 빠르게</h2>
        <p className={authPageStyles.introductionDescription}>
          사업자 정보는 한 번만 등록하고, 필요한 지원사업을 계속 찾아보세요.
        </p>
      </div>
      <p className={authPageStyles.trustNote}>기업 정보는 지원사업 추천을 위한 기준으로 사용됩니다.</p>
    </section>
  )
}
