import { type KeyboardEvent, type UIEvent, useEffect, useRef, useState } from 'react'

import type { HelpEntry } from '../../../shared/help/helpTypes'
import { useHelpPanelViewModel } from '../viewmodel/useHelpPanelViewModel'
import { HelpAnswerCard } from './HelpAnswerCard'
import { HelpAbstentionCard, HelpFailureCard, HelpGeneratedAnswer, HelpProgramAnswer } from './HelpGeneratedAnswer'
import { supportProgramIdentityOf } from '../viewmodel/supportProgramIdentityOf'
import { HelpSuggestionList } from './HelpSuggestionList'
import { helpPanelStyles as s } from './HelpPanel.styles'

const focusableSelector = 'a[href], button:not([disabled]), textarea, input, [tabindex]:not([tabindex="-1"])'

/**
 * 도움말 패널입니다. 머리와 입력줄은 고정하고 본문만 스크롤합니다. 열면 입력에 포커스를 두고 Esc로 닫으며,
 * 열려 있는 동안에만 Tab이 패널 안을 돕니다. 자동 스크롤은 바닥에 있을 때만 하고 아니면 알림만 띄웁니다.
 */
export function HelpPanel({
  pathname,
  search,
  inApp,
  lifted,
  onClose,
}: {
  pathname: string
  search: string
  inApp: boolean
  lifted: boolean
  onClose: () => void
}) {
  const { messages, suggestions, isPending, askEntry, askText, askProgram, stop } = useHelpPanelViewModel(pathname)
  const programIdentity = supportProgramIdentityOf(search)
  const panelRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const isAtBottom = useRef(true)
  const [hasUnseenAnswer, setHasUnseenAnswer] = useState(false)
  const [draft, setDraft] = useState('')

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const body = bodyRef.current
    if (!body || messages.length === 0) return
    if (isAtBottom.current) body.scrollTop = body.scrollHeight
    else setHasUnseenAnswer(true)
  }, [messages])

  function handleScroll(event: UIEvent<HTMLDivElement>) {
    const { scrollTop, scrollHeight, clientHeight } = event.currentTarget
    isAtBottom.current = scrollHeight - scrollTop - clientHeight < 24
    if (isAtBottom.current) setHasUnseenAnswer(false)
  }

  function scrollToLatest() {
    const body = bodyRef.current
    if (!body) return
    body.scrollTop = body.scrollHeight
    isAtBottom.current = true
    setHasUnseenAnswer(false)
  }

  function handlePanelKeyDown(event: KeyboardEvent<HTMLDivElement>) {
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

  function send() {
    void askText(draft)
    setDraft('')
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // 한글 조합 중의 Enter는 글자를 확정하는 키라 전송으로 쓰지 않습니다.
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
    event.preventDefault()
    if (!isPending) send()
  }

  function ask(entry: HelpEntry) {
    askEntry(entry)
    inputRef.current?.focus()
  }

  function retry(question: string) {
    void askText(question)
  }

  function questionBefore(index: number): string {
    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
      const candidate = messages[cursor]
      if (candidate.role === 'question') return candidate.text
    }
    return ''
  }

  return (
    <div
      aria-label="GovBiz 도움말"
      className={lifted ? `${s.panel} ${s.panelLifted}` : s.panel}
      ref={panelRef}
      role="dialog"
      onKeyDown={handlePanelKeyDown}
    >
      <header className={s.header}>
        <h2 className={s.headerTitle}><span aria-hidden="true" className={s.headerDot} />GovBiz 도움말</h2>
        <button aria-label="도움말 닫기" className={s.headerClose} type="button" onClick={onClose}>✕</button>
      </header>

      <div className={s.body} ref={bodyRef} onScroll={handleScroll}>
        {hasUnseenAnswer && <button className={s.latestToast} type="button" onClick={scrollToLatest}>↓ 최신 답변 보기</button>}
        {messages.length === 0 && <>
          <p className={s.scope}>
            <b className={s.scopeStrong}>화면 사용법을 안내합니다.</b><br />
            공고 내용은 답하지 않고 원문 질문으로 안내합니다.
          </p>
          <HelpSuggestionList entries={suggestions} label="이 화면에서 자주 묻는 질문" onSelect={ask} />
        </>}
        <div aria-live="polite" className="flex flex-col gap-2.5 empty:hidden" role="log">
          {messages.map((message, index) => {
            switch (message.role) {
              case 'question':
                return <p className={s.question} key={message.id}>{message.text}</p>
              case 'answer':
                return <HelpAnswerCard entry={message.entry} inApp={inApp} key={message.id} onAsk={ask} />
              case 'pending':
                return <div className={s.answer} key={message.id}>
                  <span className={s.pendingStatus} role="status">
                    <span aria-hidden="true" className={s.pendingDot} />답변을 준비하고 있습니다
                  </span>
                  <span aria-hidden="true" className={s.pendingLines}>
                    <span className={`${s.pendingLine} w-full`} />
                    <span className={`${s.pendingLine} w-4/5`} />
                    <span className={`${s.pendingLine} w-3/5`} />
                  </span>
                </div>
              case 'generated':
                return <HelpGeneratedAnswer citations={message.citations} key={message.id} text={message.text} />
              case 'abstained':
                return <HelpAbstentionCard
                  inApp={inApp}
                  key={message.id}
                  search={search}
                  status={message.status}
                  onAskProgram={programIdentity
                    ? () => void askProgram(questionBefore(index), programIdentity)
                    : undefined}
                >
                  {message.status === 'NOT_IN_HELP'
                    && <HelpSuggestionList entries={suggestions} label="이 화면에서 자주 묻는 질문" onSelect={ask} />}
                </HelpAbstentionCard>
              case 'program-answer':
                return <HelpProgramAnswer answer={message.answer} key={message.id} />
              case 'program-notice':
                return <p className={s.answer} key={message.id}>{message.message}</p>
              default:
                return message.reason === 'cancelled'
                  ? <p className={s.pendingStatus} key={message.id}>중지했습니다</p>
                  : <HelpFailureCard key={message.id} onRetry={() => retry(questionBefore(index))} />
            }
          })}
        </div>
      </div>

      <div className={s.footer}>
        <div className={s.composer}>
          <textarea
            aria-label="도움말 질문"
            className={s.composerInput}
            placeholder="무엇이든 물어보세요"
            ref={inputRef}
            rows={1}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleInputKeyDown}
          />
          {isPending
            ? <button aria-label="답변 중지" className={s.composerStop} type="button" onClick={stop}>■</button>
            : <button aria-label="질문 보내기" className={s.composerSend} disabled={draft.trim() === ''} type="button" onClick={send}>↑</button>}
        </div>
        <p className={s.footerNote}>도움말 범위 안에서만 답합니다</p>
      </div>
    </div>
  )
}
