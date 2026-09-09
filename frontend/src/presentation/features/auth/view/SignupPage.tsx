import { Link } from 'react-router'

import { useSignupViewModel } from '../viewmodel/useSignupViewModel'
import { AuthBrandPanel } from './AuthBrandPanel'
import { SocialLoginButtons } from './SocialLoginButtons'
import { authPageStyles } from './AuthPage.styles'

/**
 * 회원가입 화면입니다. 로그인 화면과 같은 껍데기를 쓰고 이메일과 비밀번호만 받습니다.
 * 기업 정보는 가입 뒤 프로필 단계에서 받고, 약관 동의는 가입 버튼 아래 안내로 갈음해 가입 시각을 서버가 기록합니다.
 */
export function SignupPage() {
  const {
    email,
    password,
    passwordConfirmation,
    error,
    isSubmitting,
    updateEmail,
    updatePassword,
    updatePasswordConfirmation,
    submit,
  } = useSignupViewModel()

  return (
    <main className={authPageStyles.page}>
      <AuthBrandPanel />

      <section className={authPageStyles.formPanel}>
        <form className={authPageStyles.card} onSubmit={submit} aria-label="회원가입" noValidate>
          <div className={authPageStyles.cardHeader}>
            <p className={authPageStyles.cardEyebrow}>회원가입</p>
            <h1 className={authPageStyles.cardTitle}>기업 계정 만들기</h1>
            <p className={authPageStyles.cardDescription}>
              이메일과 비밀번호만으로 시작합니다. 기업 정보는 가입 뒤 프로필에서 등록합니다.
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
                aria-describedby={error?.field === 'email' ? 'signup-error' : undefined}
                placeholder="manager@company.co.kr"
                value={email}
                onChange={(event) => updateEmail(event.target.value)}
              />
            </label>

            <div className={authPageStyles.field}>
              <label htmlFor="signup-password">비밀번호</label>
              <input
                className={authPageStyles.fieldControl}
                id="signup-password"
                type="password"
                name="password"
                autoComplete="new-password"
                required
                minLength={8}
                maxLength={72}
                aria-invalid={error?.field === 'password'}
                aria-describedby={error?.field === 'password' ? 'signup-password-hint signup-error' : 'signup-password-hint'}
                placeholder="비밀번호 입력"
                value={password}
                onChange={(event) => updatePassword(event.target.value)}
              />
              <span id="signup-password-hint" className={authPageStyles.fieldHint}>8자 이상 72자 이하로 입력합니다.</span>
            </div>

            <label className={authPageStyles.field}>
              <span>비밀번호 확인</span>
              <input
                className={authPageStyles.fieldControl}
                type="password"
                name="passwordConfirmation"
                autoComplete="new-password"
                required
                aria-invalid={error?.field === 'passwordConfirmation'}
                aria-describedby={error?.field === 'passwordConfirmation' ? 'signup-error' : undefined}
                placeholder="비밀번호 다시 입력"
                value={passwordConfirmation}
                onChange={(event) => updatePasswordConfirmation(event.target.value)}
              />
            </label>
          </div>

          {error ? <p id="signup-error" className={authPageStyles.fieldError} role="alert">{error.message}</p> : null}
          <button className={authPageStyles.submitButton} type="submit" disabled={isSubmitting}>
            {isSubmitting ? '가입 중…' : '가입하고 시작하기'}
          </button>
          <p className={authPageStyles.fieldHint}>가입하면 이용약관과 개인정보 처리방침에 동의한 것으로 봅니다.</p>

          <SocialLoginButtons intent="signup" />

          <div className={authPageStyles.divider}>
            <span className={authPageStyles.dividerLine} aria-hidden="true" />
            <span className={authPageStyles.dividerText}>이미 계정이 있나요?</span>
            <span className={authPageStyles.dividerLine} aria-hidden="true" />
          </div>

          <Link className={authPageStyles.secondaryButton} to="/login">
            로그인
          </Link>

          <p className={authPageStyles.cardFooter}>
            <Link className={authPageStyles.helperLink} to="/">가입 없이 지원사업 검색</Link>
          </p>
        </form>
      </section>
    </main>
  )
}
