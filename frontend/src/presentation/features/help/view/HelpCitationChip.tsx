import { useId, useState } from 'react'

import type { HelpEntry } from '../../../shared/help/helpTypes'
import { helpPanelStyles as s } from './HelpPanel.styles'

/**
 * 답변이 근거로 쓴 도움말 항목입니다. 본문 옆 번호와 같은 번호를 달아 어느 문장의 근거인지 잇고,
 * 누르면 대화를 떠나지 않고 요약과 갱신일을 펼칩니다. 오래된 안내를 감추지 않으려고 갱신일을 함께 적습니다.
 */
export function HelpCitationChip({ entry, order }: { entry: HelpEntry; order: number }) {
  const popoverId = useId()
  const [isOpen, setIsOpen] = useState(false)

  return (
    <div>
      <button
        aria-controls={isOpen ? popoverId : undefined}
        aria-expanded={isOpen}
        className={s.citation}
        type="button"
        onClick={() => setIsOpen((open) => !open)}
      >
        <span aria-hidden="true" className={s.citationNumber}>{order}</span>
        {entry.title}
      </button>
      {isOpen && <div className={s.citationPopover} id={popoverId} role="note">
        <p className={s.citationPopoverBody}>{entry.summary}</p>
        <span className={s.citationPopoverDate}>{entry.updatedOn} 갱신</span>
      </div>}
    </div>
  )
}
