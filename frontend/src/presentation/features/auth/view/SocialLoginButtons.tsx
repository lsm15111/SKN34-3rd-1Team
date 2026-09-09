import type { OAuthProvider } from '../../../../domain/entities/OAuthProvider'
import { type SocialLoginIntent, useSocialLoginViewModel } from '../viewmodel/useSocialLoginViewModel'
import { authPageStyles } from './AuthPage.styles'

const providerButtonStyles: Record<OAuthProvider, string> = {
  google: authPageStyles.socialButtonGoogle,
  kakao: authPageStyles.socialButtonKakao,
}

const providerMarks: Record<OAuthProvider, string> = {
  google: 'G',
  kakao: 'K',
}

/** Google·카카오 버튼입니다. 설정된 제공처가 없으면 아무것도 그리지 않아 이메일 폼만 남습니다. */
export function SocialLoginButtons({ intent }: { intent: SocialLoginIntent }) {
  const { buttons, dividerText, hint } = useSocialLoginViewModel(intent)
  if (buttons.length === 0) return null

  return (
    <div className={authPageStyles.socialSection} aria-label="소셜 로그인">
      <div className={authPageStyles.socialDivider}>
        <span className={authPageStyles.dividerLine} aria-hidden="true" />
        <span className={authPageStyles.dividerText}>{dividerText}</span>
        <span className={authPageStyles.dividerLine} aria-hidden="true" />
      </div>
      {buttons.map((button) => (
        <a key={button.provider} className={`${authPageStyles.socialButton} ${providerButtonStyles[button.provider]}`} href={button.href}>
          <span className={authPageStyles.socialButtonIcon} aria-hidden="true">{providerMarks[button.provider]}</span>
          {button.label}
        </a>
      ))}
      <p className={authPageStyles.fieldHint}>{hint}</p>
    </div>
  )
}
