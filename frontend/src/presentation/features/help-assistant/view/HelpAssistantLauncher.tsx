import { useCallback, useRef, useState } from 'react'
import { useLocation } from 'react-router'

import { appPaths, isAppPath, publicPaths } from '../../../shared/routes/appPaths'
import { HelpAssistantPanel } from './HelpAssistantPanel'
import { helpAssistantStyles as s } from './HelpAssistant.styles'

/** 로그인·가입처럼 공용 껍데기를 쓰지 않는 단독 화면에서는 떠 있는 버튼을 두지 않습니다. */
const hiddenPaths: readonly string[] = [
  publicPaths.login,
  publicPaths.signup,
  publicPaths.oauthComplete,
  publicPaths.reportEmail,
  '/forgot-password',
  '/reset-password',
]

/** 검색 화면은 아래에 입력창이 있어 버튼을 그만큼 올립니다. 떠 있는 요소를 두 개 겹치지 않습니다. */
const liftedPaths: readonly string[] = [publicPaths.landing, appPaths.chat]

/**
 * 오른쪽 아래 도우미 버튼입니다. 같은 자리에서 열고 닫으며 열려 있는 동안에는 ✕가 됩니다.
 * 비로그인 화면에도 보입니다. 처음 온 사람이 가장 많이 막히기 때문입니다.
 */
export function HelpAssistantLauncher() {
  const { pathname } = useLocation()
  const normalized = pathname.replace(/\/+$/, '') || '/'
  const [isOpen, setIsOpen] = useState(false)
  const launcherRef = useRef<HTMLButtonElement>(null)

  const close = useCallback(() => {
    setIsOpen(false)
    launcherRef.current?.focus()
  }, [])

  if (hiddenPaths.includes(normalized)) return null

  const lifted = liftedPaths.includes(normalized)

  return (
    <>
      {isOpen && <HelpAssistantPanel inApp={isAppPath(normalized)} lifted={lifted} onClose={close} />}
      {/* 열려 있는 동안 닫기 버튼이 두 개로 들리지 않도록 보조기술 순서에서는 패널의 닫기만 남깁니다. */}
      <button
        aria-expanded={isOpen}
        aria-hidden={isOpen}
        aria-label="도우미 열기"
        className={`${s.launcher} ${isOpen ? s.launcherOpen : s.launcherClosed} ${lifted ? s.launcherLifted : ''}`}
        ref={launcherRef}
        tabIndex={isOpen ? -1 : undefined}
        type="button"
        onClick={() => (isOpen ? close() : setIsOpen(true))}
      >
        {isOpen ? '✕' : '?'}
      </button>
    </>
  )
}
