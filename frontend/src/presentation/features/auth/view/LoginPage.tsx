import { Link } from 'react-router'

import { useLoginViewModel } from '../viewmodel/useLoginViewModel'
import { AuthBrandPanel } from './AuthBrandPanel'
import { SocialLoginButtons } from './SocialLoginButtons'
import { authPageStyles } from './AuthPage.styles'

/** 로그인 화면입니다. 공용 헤더의 로그인 버튼이 이 화면으로 옵니다. */
export function LoginPage() {
  const {
    email,
    password,
    rememberMe,
    error,
    isSubmitting,
    updateEmail,
    updatePassword,
    toggleRememberMe,
    submit,
  } = useLoginViewModel()

  return (
    <main className={authPageStyles.page}>
      <AuthBrandPanel />

      <section className={authPageStyles.formPanel}>
        <form className={authPageStyles.card} onSubmit={submit} aria-label="로그인" noValidate>
          <div className={authPageStyles.cardHeader}>
            <p className={authPageStyles.cardEyebrow}>로그인</p>
            <h1 className={authPageStyles.cardTitle}>다시 오셨군요</h1>
            <p className={authPageStyles.cardDescription}>
              담당자 이메일로 로그인하면 저장한 공고와 모집 현황을 이어서 볼 수 있습니다.
            </p>
          </div>

          <div className={authPageStyles.fields}>
            <label className={authPageStyles.field}>
              <span>이메일</span>
              <input
                className={authPageStyles.fieldControl}
                type="email"
                name="email"
                autoComplete="email"
                required
                aria-invalid={error?.field === 'email'}
                aria-describedby={error ? 'login-error' : undefined}
                placeholder="manager@company.co.kr"
                value={email}
                onChange={(event) => updateEmail(event.target.value)}
              />
            </label>

            <label className={authPageStyles.field}>
              <span>비밀번호</span>
              <input
                className={authPageStyles.fieldControl}
                type="password"
                name="password"
                autoComplete="current-password"
                required
                aria-invalid={error?.field === 'password'}
                aria-describedby={error ? 'login-error' : undefined}
                placeholder="비밀번호 입력"
                value={password}
                onChange={(event) => updatePassword(event.target.value)}
              />
            </label>

            <div className={authPageStyles.optionsRow}>
              <label className={authPageStyles.checkboxLabel}>
                <input
                  className={authPageStyles.checkbox}
                  type="checkbox"
                  name="rememberMe"
                  checked={rememberMe}
                  onChange={toggleRememberMe}
                />
                로그인 상태 유지
              </label>
              {/* 비밀번호 재설정 화면은 아직 없으므로 링크로 만들지 않습니다. */}
              <span className={authPageStyles.helperPending} aria-disabled="true">
                비밀번호 재설정 · 준비 중
              </span>
            </div>
          </div>

          {error ? <p id="login-error" className={authPageStyles.fieldError} role="alert">{error.message}</p> : null}
          <button className={authPageStyles.submitButton} type="submit" disabled={isSubmitting}>
            {isSubmitting ? '로그인 중…' : '로그인'}
          </button>

          <SocialLoginButtons intent="login" />

          <div className={authPageStyles.divider}>
            <span className={authPageStyles.dividerLine} aria-hidden="true" />
            <span className={authPageStyles.dividerText}>아직 계정이 없나요?</span>
            <span className={authPageStyles.dividerLine} aria-hidden="true" />
          </div>

          <Link className={authPageStyles.secondaryButton} to="/signup">
            기업 계정 만들기
          </Link>

          <p className={authPageStyles.cardFooter}>
            <Link className={authPageStyles.helperLink} to="/">로그인 없이 지원사업 검색</Link>
          </p>
        </form>
      </section>
    </main>
  )
}
