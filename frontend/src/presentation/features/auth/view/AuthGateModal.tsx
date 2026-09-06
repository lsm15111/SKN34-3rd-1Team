import { useEffect, useRef } from 'react'
import { Link } from 'react-router'

import { anonymousSearchLimit } from '../state/usageSlice'
import { authGateModalStyles } from './AuthGateModal.styles'

type AuthGateModalProps = {
  onClose: () => void
}

/** 비로그인 무료 검색 횟수를 모두 쓴 뒤 가입·로그인을 안내합니다. Escape·배경 클릭으로 닫힙니다. */
export function AuthGateModal({ onClose }: AuthGateModalProps) {
  const primaryActionRef = useRef<HTMLAnchorElement>(null)

  useEffect(() => {
    primaryActionRef.current?.focus()

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.preventDefault()
      onClose()
    }

    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  return (
    <div className={authGateModalStyles.backdrop} onClick={onClose} role="presentation">
      <section
        className={authGateModalStyles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-gate-title"
        onClick={(event) => event.stopPropagation()}
      >
        <p className={authGateModalStyles.eyebrow}>KEEP EXPLORING</p>
        <h2 id="auth-gate-title" className={authGateModalStyles.title}>
          무료 검색 {anonymousSearchLimit}회를 모두 사용했어요
        </h2>
        <p className={authGateModalStyles.description}>
          사업자등록번호와 이메일만으로 가입하면 내 기업에 맞는 지원사업을 계속 찾아볼 수 있습니다.
        </p>
        <div className={authGateModalStyles.actions}>
          <Link ref={primaryActionRef} className={authGateModalStyles.primaryAction} to="/signup">
            간편 회원가입
          </Link>
          <Link className={authGateModalStyles.secondaryAction} to="/login">
            로그인
          </Link>
          <button className={authGateModalStyles.dismissButton} type="button" onClick={onClose}>
            나중에 할게요
          </button>
        </div>
      </section>
    </div>
  )
}
