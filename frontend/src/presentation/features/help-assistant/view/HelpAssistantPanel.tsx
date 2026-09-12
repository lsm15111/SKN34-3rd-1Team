import { type KeyboardEvent, useEffect, useMemo, useRef } from 'react'

import { helpAssistantGreeting } from '../viewmodel/helpAssistantMenu'
import { useHelpAssistantViewModel } from '../viewmodel/useHelpAssistantViewModel'
import { HelpAssistantPricing } from './HelpAssistantPricing'
import { helpAssistantStyles as s } from './HelpAssistant.styles'

const focusableSelector = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** 상담 화면처럼 보낸 시각을 함께 적습니다. 서울 기준으로 한 번만 계산합니다. */
function useOpenedAtLabel(): string {
  return useMemo(
    () => new Intl.DateTimeFormat('ko-KR', { timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(new Date()),
    [],
  )
}

/**
 * 도우미 패널입니다. 왼쪽 안내 말풍선과 오른쪽 메뉴 버튼으로만 움직이며 이 단계에서는 요청을 보내지 않습니다.
 * 열면 첫 메뉴에 포커스를 두고 Esc로 닫으며, 열려 있는 동안 Tab이 패널 안을 돕니다.
 */
export function HelpAssistantPanel({
  inApp,
  lifted,
  onClose,
}: {
  inApp: boolean
  lifted: boolean
  onClose: () => void
}) {
  const { turns, menu, plans, choose } = useHelpAssistantViewModel()
  const panelRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const openedAt = useOpenedAtLabel()

  useEffect(() => {
    panelRef.current?.querySelector<HTMLElement>('[data-help-assistant-first]')?.focus()
  }, [])

  useEffect(() => {
    const body = bodyRef.current
    if (body) body.scrollTop = body.scrollHeight
  }, [turns])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab' || !panelRef.current) return
    const focusable = [...panelRef.current.querySelectorAll<HTMLElement>(focusableSelector)]
    if (focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div
      aria-label="GovBiz 도우미"
      className={lifted ? `${s.panel} ${s.panelLifted}` : s.panel}
      ref={panelRef}
      role="dialog"
      onKeyDown={handleKeyDown}
    >
      <header className={s.header}>
        <h2 className={s.headerTitle}><span aria-hidden="true" className={s.headerDot} />GovBiz 도우미</h2>
        <button aria-label="도우미 닫기" className={s.headerClose} type="button" onClick={onClose}>✕</button>
      </header>

      <div className={s.body} ref={bodyRef}>
        {/* 지나간 대화만 알립니다. 메뉴는 늘 같은 자리에 있어 새로 읽어 줄 필요가 없습니다. */}
        <div aria-live="polite" className={s.log} role="log">
        {turns.map((turn) => {
          if (turn.role === 'choice') {
            return <div className={s.userTurn} key={turn.id}><p className={s.userBubble}>{turn.label}</p></div>
          }

          if (turn.role === 'reply') {
            return (
              <div className={s.botTurn} key={turn.id}>
                <div className={s.bubble}>
                  <p className={s.bubbleParagraph}>{turn.item.reply}</p>
                </div>
                {turn.item.id === 'pricing' && <HelpAssistantPricing inApp={inApp} plans={plans} />}
              </div>
            )
          }

          return (
            <div className={s.botTurn} key={turn.id}>
              <div className={s.bubble}>
                {helpAssistantGreeting.map((line) => <p className={s.bubbleParagraph} key={line}>{line}</p>)}
              </div>
              <p className={s.sender}>
                <span aria-hidden="true" className={s.senderMark}>G</span>
                GovBiz, {openedAt}
              </p>
            </div>
          )
        })}
        </div>

        <nav className={s.menuDock} aria-label="도우미 메뉴">
          {menu.map((item, index) => (
            <button
              className={s.menuButton}
              data-help-assistant-first={index === 0 ? '' : undefined}
              key={item.id}
              type="button"
              onClick={() => choose(item)}
            >
              <span aria-hidden="true" className={s.menuMark}>{item.mark}</span>
              {item.label}
            </button>
          ))}
        </nav>
      </div>
    </div>
  )
}
