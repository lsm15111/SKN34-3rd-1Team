import { Link, useLocation } from 'react-router'

import { useAuthSession } from '../auth/hooks/useAuthSession'
import { appPaths, publicPaths } from '../routes/appPaths'
import { appHeaderStyles } from './AppHeader.styles'

/** 경로별 현재 화면 이름입니다. 등록되지 않은 경로는 이름을 표시하지 않습니다. */
const isLandingPath = (pathname: string) => pathname === publicPaths.landing
const isPricingPath = (pathname: string) => pathname === publicPaths.pricing || pathname === `${publicPaths.pricing}/`
const isPartnersPath = (pathname: string) => pathname === publicPaths.partners || pathname.startsWith(`${publicPaths.partners}/`)

const pageTitles: Array<{ matches: (pathname: string) => boolean; title: string }> = [
  { matches: isLandingPath, title: 'AI 채팅' },
  { matches: isPricingPath, title: '요금제' },
  { matches: isPartnersPath, title: '파트너 모집' },
  { matches: (pathname) => pathname === publicPaths.supportProgramDetail, title: '공고 상세' },
  { matches: (pathname) => pathname === publicPaths.supportProgramQuestion, title: '원문 질문' },
  { matches: (pathname) => pathname.startsWith('/examples/sample-item'), title: '상태관리 비교 예제' },
]

/**
 * 로그인 전 화면 위에 놓이는 앱 헤더입니다. 브랜드, 가운데의 현재 화면 이름, 화면 이동과 계정 진입점만 담당하며
 * 특정 페이지 ViewModel에 속하지 않습니다. 로그인 상태는 shared의 `useAuthSession`에서 읽습니다.
 */
export function AppHeader() {
  const { pathname } = useLocation()
  const isLanding = isLandingPath(pathname)
  const isPricing = isPricingPath(pathname)
  const isPartners = isPartnersPath(pathname)
  // 검색·파트너 모집·요금제는 로그인 전 공개 화면이라 같은 마케팅 헤더를 공유합니다.
  const isMarketingPage = isLanding || isPricing || isPartners
  const currentTitle = pageTitles.find((page) => page.matches(pathname))?.title ?? null

  return (
    <header className={isMarketingPage ? appHeaderStyles.landingHeader : appHeaderStyles.header} aria-label="앱 헤더">
      {/* 같은 홈 주소에서도 대화·필터 상태를 비우고 처음 화면으로 돌아갑니다. */}
      <Link className={isMarketingPage ? appHeaderStyles.landingBrand : appHeaderStyles.brand}
        to={publicPaths.landing} reloadDocument aria-label="GovBiz 홈으로">
        <span className={isMarketingPage ? appHeaderStyles.landingBrandMark : appHeaderStyles.brandMark} aria-hidden="true">G</span>
        <span>
          <strong className={isMarketingPage ? appHeaderStyles.landingBrandTitle : appHeaderStyles.brandTitle}>GovBiz</strong>
          <span className={isMarketingPage ? 'sr-only' : appHeaderStyles.brandSubtitle}>지원사업 탐색 도우미</span>
        </span>
      </Link>

      <p className={isMarketingPage ? 'sr-only' : appHeaderStyles.currentPage} aria-current="page">
        {currentTitle}
      </p>

      <nav className={isMarketingPage ? appHeaderStyles.landingNav : appHeaderStyles.nav} aria-label="화면 이동">
        <div className={isMarketingPage ? appHeaderStyles.landingNavLinks : 'contents'}>
          {isMarketingPage ? <>
            <Link className={appHeaderStyles.landingNavLink} to={publicPaths.landing} aria-current={isLanding ? 'page' : undefined}>지원사업 찾기</Link>
            <Link className={appHeaderStyles.landingNavLink} to={publicPaths.partners} aria-current={isPartners ? 'page' : undefined}>파트너 모집</Link>
          </> : null}
          <Link
            className={isMarketingPage ? appHeaderStyles.landingNavLink : appHeaderStyles.navLink}
            to={publicPaths.pricing}
            aria-current={isPricing ? 'page' : undefined}
          >
            요금제
          </Link>
        </div>
        <AccountMenu isMarketingPage={isMarketingPage} />
      </nav>
    </header>
  )
}

/**
 * 세션 복원 전에는 아무것도 그리지 않아 로그인 버튼이 깜빡이지 않게 합니다. 로그인 뒤에는 작업 화면 링크와
 * 로그아웃을, 로그인 전에는 회원가입·로그인과 개발 빌드 전용 시드 로그인을 보여 줍니다.
 */
function AccountMenu({ isMarketingPage }: { isMarketingPage: boolean }) {
  const { account, status, logOut, logInAsDeveloper, isDevLoggingIn, devLogInError } = useAuthSession()

  if (status === 'unknown') return null

  if (account) {
    return (
      <div className={isMarketingPage ? appHeaderStyles.landingAccountLinks : appHeaderStyles.account}>
        <Link className={isMarketingPage ? appHeaderStyles.landingAccountButton : appHeaderStyles.loginButton} to={appPaths.chat}>
          작업 화면
        </Link>
        <span className={appHeaderStyles.accountEmail} title={account.email}>{account.email}</span>
        <button className={appHeaderStyles.logoutButton} type="button" onClick={() => void logOut()}>
          로그아웃
        </button>
      </div>
    )
  }

  return (
    <div className={isMarketingPage ? appHeaderStyles.landingAccountLinks : appHeaderStyles.account}>
      <Link className={isMarketingPage ? appHeaderStyles.landingAccountButton : appHeaderStyles.navLink} to="/signup">
        회원가입
      </Link>
      <Link className={isMarketingPage ? appHeaderStyles.landingAccountButton : appHeaderStyles.loginButton} to="/login">
        로그인
      </Link>
      {import.meta.env.DEV ? (
        <span className={appHeaderStyles.devLogin}>
          <button
            className={appHeaderStyles.devLoginButton}
            type="button"
            disabled={isDevLoggingIn}
            onClick={() => void logInAsDeveloper('ADMIN')}
          >
            개발 로그인 · 관리자
          </button>
          <button
            className={appHeaderStyles.devLoginButton}
            type="button"
            disabled={isDevLoggingIn}
            onClick={() => void logInAsDeveloper('MEMBER')}
          >
            개발 로그인 · 회원
          </button>
          <button
            className={appHeaderStyles.devLoginButton}
            type="button"
            disabled={isDevLoggingIn}
            onClick={() => void logInAsDeveloper('COMPANY')}
          >
            개발 로그인 · 기업
          </button>
          {devLogInError ? <span className={appHeaderStyles.devLoginError} role="alert">{devLogInError}</span> : null}
        </span>
      ) : null}
    </div>
  )
}
