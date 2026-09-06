import { Link, Navigate } from 'react-router'

import { useAuthSessionViewModel } from '../viewmodel/useAuthSessionViewModel'
import { type BusinessLookupState, useSignupViewModel } from '../viewmodel/useSignupViewModel'
import { authPageStyles } from './AuthPage.styles'

export function SignupPage() {
  const session = useAuthSessionViewModel()

  if (session.isAuthenticated) {
    return <Navigate replace to="/" />
  }

  return <SignupForm />
}

function SignupForm() {
  const {
    errors,
    isLookingUp,
    isSubmitting,
    lookup,
    lookupBusiness,
    registerBusinessNumber,
    registerField,
    selectCompany,
    selectedCompany,
    submit,
    submitError,
  } = useSignupViewModel()
  const lookupMessage = createLookupMessage(lookup)

  return (
    <main className={authPageStyles.page}>
      <div className={authPageStyles.shell}>
        <section className={authPageStyles.introduction}>
          <div className={authPageStyles.brand}>
            <span className={authPageStyles.brandMark}>G</span>
            <div>
              <strong className={authPageStyles.brandName}>GovBiz</strong>
              <span className={authPageStyles.brandSubtitle}>지원사업 탐색 도우미</span>
            </div>
          </div>
          <div className={authPageStyles.introductionCopy}>
            <p className={authPageStyles.eyebrow}>SIMPLE START</p>
            <h2 className={authPageStyles.introductionTitle}>사업자 정보로 간단하게 시작하세요</h2>
            <p className={authPageStyles.introductionDescription}>
              사업자등록번호로 확인한 기업 정보와 이메일만으로 계정을 만듭니다. 소재지·업종 같은
              상세 정보는 가입 후에 채워도 됩니다.
            </p>
          </div>
          <p className={authPageStyles.trustNote}>
            기업 정보는 국세청 등록 여부를 확인한 뒤 저장하며 지원사업 추천 기준으로만 사용합니다.
          </p>
        </section>
        <section className={authPageStyles.formPanel} aria-labelledby="signup-title">
          <Link className={authPageStyles.backLink} to="/">← 검색으로 돌아가기</Link>
          <div className={authPageStyles.formHeader}>
            <h1 id="signup-title" className={authPageStyles.formTitle}>간편 회원가입</h1>
            <p className={authPageStyles.formDescription}>사업자 정보를 먼저 확인하고 계정을 만들어 주세요.</p>
          </div>
          <form className={authPageStyles.form} noValidate onSubmit={submit}>
            <label className={authPageStyles.field} htmlFor="business-number">
              사업자등록번호
              <span className={authPageStyles.lookupRow}>
                <input
                  id="business-number"
                  className={authPageStyles.input}
                  inputMode="numeric"
                  autoComplete="off"
                  placeholder="000-00-00000"
                  aria-invalid={errors.businessNumber || lookup.status === 'invalid-number' ? true : undefined}
                  {...registerBusinessNumber()}
                />
                <button
                  className={authPageStyles.lookupButton}
                  type="button"
                  onClick={() => void lookupBusiness()}
                  disabled={isLookingUp || isSubmitting}
                >
                  {isLookingUp ? '확인 중…' : '기업 정보 확인'}
                </button>
              </span>
              <span className={authPageStyles.fieldHint}>국세청에 등록된 사업자인지 확인합니다.</span>
              {errors.businessNumber?.message
                ? <span className={authPageStyles.fieldError}>{errors.businessNumber.message}</span>
                : null}
            </label>
            {lookupMessage ? (
              <p className={lookupMessage.isError ? authPageStyles.error : authPageStyles.notice} role="status">
                {lookupMessage.text}
              </p>
            ) : null}
            {lookup.status === 'found' ? (
              <fieldset className={authPageStyles.companyPanel}>
                <legend className="px-1 font-bold">확인된 기업</legend>
                {lookup.companies.map((company) => (
                  <label key={`${company.businessNumber}-${company.companyName}`} className={authPageStyles.companyRow}>
                    <span className="flex gap-2">
                      <input
                        type="radio"
                        name="company"
                        value={company.companyName}
                        checked={selectedCompany?.companyName === company.companyName}
                        onChange={() => selectCompany(company)}
                      />
                      <span>{company.companyName}</span>
                    </span>
                    <strong className={authPageStyles.companyValue}>
                      {company.businessStatus || '상태 정보 없음'}
                    </strong>
                  </label>
                ))}
              </fieldset>
            ) : null}
            <label className={authPageStyles.field} htmlFor="signup-email">
              이메일
              <input
                id="signup-email"
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
            <label className={authPageStyles.field} htmlFor="signup-password">
              비밀번호
              <input
                id="signup-password"
                className={authPageStyles.input}
                type="password"
                autoComplete="new-password"
                aria-invalid={errors.password ? true : undefined}
                {...registerField('password')}
              />
              <span className={authPageStyles.fieldHint}>8자 이상, 영문과 숫자를 포함합니다.</span>
              {errors.password?.message
                ? <span className={authPageStyles.fieldError}>{errors.password.message}</span>
                : null}
            </label>
            <label className={authPageStyles.field} htmlFor="signup-password-confirm">
              비밀번호 확인
              <input
                id="signup-password-confirm"
                className={authPageStyles.input}
                type="password"
                autoComplete="new-password"
                aria-invalid={errors.passwordConfirm ? true : undefined}
                {...registerField('passwordConfirm')}
              />
              {errors.passwordConfirm?.message
                ? <span className={authPageStyles.fieldError}>{errors.passwordConfirm.message}</span>
                : null}
            </label>
            <label className={authPageStyles.checkboxField} htmlFor="signup-terms">
              <input id="signup-terms" className="mt-1" type="checkbox" {...registerField('termsAgreed')} />
              <span>
                서비스 이용약관과 개인정보 처리방침에 동의합니다.
                {errors.termsAgreed?.message
                  ? <span className={authPageStyles.fieldError}>{errors.termsAgreed.message}</span>
                  : null}
              </span>
            </label>
            {submitError ? <p className={authPageStyles.error} role="alert">{submitError}</p> : null}
            <button className={authPageStyles.submitButton} type="submit" disabled={isSubmitting}>
              {isSubmitting ? '가입 중…' : '가입하고 계속하기'}
            </button>
            <p className={authPageStyles.switchLink}>
              이미 계정이 있나요? <Link className={authPageStyles.switchLinkAnchor} to="/login">로그인</Link>
            </p>
          </form>
        </section>
      </div>
    </main>
  )
}

function createLookupMessage(lookup: BusinessLookupState): { text: string; isError: boolean } | null {
  switch (lookup.status) {
    case 'found':
      return { text: '기업 정보를 확인했습니다. 가입할 기업을 선택해 주세요.', isError: false }
    case 'not-found':
      return { text: '국세청에 등록된 사업자를 찾지 못했습니다. 번호를 다시 확인해 주세요.', isError: true }
    case 'unavailable':
      return { text: '기업 정보를 지금 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.', isError: true }
    case 'invalid-number':
      return { text: '사업자등록번호 10자리를 입력해 주세요.', isError: true }
    case 'idle':
    case 'looking-up':
      return null
  }
}
