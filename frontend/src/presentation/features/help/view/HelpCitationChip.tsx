import { useId, useState } from 'react'

import type { HelpEntry } from '../../../shared/help/helpTypes'
import { helpPanelStyles as s } from './HelpPanel.styles'

/**
 * 답변이 근거로 쓴 도움말 항목입니다. 누르면 대화를 떠나지 않고 요약과 갱신일을 먼저 보여 줍니다.
 * 오래된 안내를 감추지 않으려고 갱신일을 함께 적습니다.
 */
export function HelpCitationChip({ entry }: { entry: HelpEntry }) {
  const popoverId = useId()
  const [isOpen, setIsOpen] = useState(false)

  return (
    <span className="relative">
      <button
        aria-controls={isOpen ? popoverId : undefined}
        aria-expanded={isOpen}
        className={s.citation}
        type="button"
        onClick={() => setIsOpen((open) => !open)}
      >
        <i aria-hidden="true" className={s.citationMark}>◈</i>
        {entry.title}
      </button>
      {isOpen && <span className={s.citationPopover} id={popoverId} role="note">
        <strong className={s.citationPopoverTitle}>{entry.title}</strong>
        <span className={s.citationPopoverBody}>{entry.summary}</span>
        <span className={s.citationPopoverFooter}>
          <span className={s.citationPopoverDate}>{entry.updatedOn}</span>
        </span>
      </span>}
    </span>
  )
}
