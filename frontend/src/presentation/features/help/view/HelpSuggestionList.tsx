import type { HelpEntry } from '../../../shared/help/helpTypes'
import { helpPanelStyles as s } from './HelpPanel.styles'

/**
 * 추천 질문 목록입니다. 문구는 항목의 `question`을 그대로 쓰며 매뉴얼 제목으로 바꾸지 않습니다.
 * 테두리 없는 행으로 두어 답변보다 눈에 덜 띄게 합니다. 좁은 화면은 키보드가 올라오면 보이는
 * 높이가 절반이라 세 번째 항목을 숨깁니다.
 */
export function HelpSuggestionList({
  label,
  entries,
  onSelect,
}: {
  label: string
  entries: readonly HelpEntry[]
  onSelect: (entry: HelpEntry) => void
}) {
  if (entries.length === 0) return null

  return (
    <section className={s.suggestions} aria-label={label}>
      <span className={s.suggestionsLabel}>{label}</span>
      {entries.map((entry, index) => (
        <button
          className={index >= 2 ? `${s.suggestionButton} max-chat:hidden` : s.suggestionButton}
          key={entry.id}
          type="button"
          onClick={() => onSelect(entry)}
        >
          <span aria-hidden="true" className={s.suggestionMark}>›</span>
          <span>{entry.question}</span>
        </button>
      ))}
    </section>
  )
}
