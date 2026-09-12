import { Link } from 'react-router'

import { helpActionHref, relatedHelpEntries } from '../../../shared/help/helpContent'
import type { HelpEntry } from '../../../shared/help/helpTypes'
import { HelpCitationChip } from './HelpCitationChip'
import { HelpSuggestionList } from './HelpSuggestionList'
import { helpPanelStyles as s } from './HelpPanel.styles'

/**
 * 결론 → 근거 → 행동 순서를 항상 지킵니다. 이 단계의 답변은 우리가 쓴 도움말 항목을 그대로 보여 주는
 * 것이라 AI 생성 표시를 붙이지 않습니다. 자유 질문에 모델이 만든 답을 붙일 때 표시를 켭니다.
 */
export function HelpAnswerCard({
  entry,
  inApp,
  onAsk,
}: {
  entry: HelpEntry
  inApp: boolean
  onAsk: (entry: HelpEntry) => void
}) {
  const related = relatedHelpEntries(entry)

  return (
    <>
      <div className={s.answer}>
        {entry.status === 'preparing' && <span className={s.answerPreparing}>준비 중</span>}
        <p className={s.answerLead}>{entry.summary}</p>
        {entry.body.map((paragraph) => <p className={s.answerParagraph} key={paragraph}>{paragraph}</p>)}
        <p className={s.answerLimitation}>{entry.limitation}</p>
        <div className={s.citations}>
          <HelpCitationChip entry={entry} />
        </div>
        {entry.action && <div className={s.actions}>
          <Link className={s.actionPrimary} to={helpActionHref(entry.action, inApp)}>{entry.action.label}</Link>
        </div>}
      </div>
      <HelpSuggestionList entries={related} label="이어서 물어보기" onSelect={onAsk} />
    </>
  )
}
