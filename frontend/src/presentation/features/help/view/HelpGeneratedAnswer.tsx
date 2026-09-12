import type { ReactNode } from 'react'
import { Link } from 'react-router'

import type { SupportProgramEvidenceAnswer } from '../../../../domain/entities/SupportProgramEvidenceAnswer'
import { appPaths, supportProgramDetailPath } from '../../../shared/routes/appPaths'
import { supportProgramIdentityOf } from '../viewmodel/supportProgramIdentityOf'
import type { HelpEntry } from '../../../shared/help/helpTypes'
import type { HelpAbstention } from '../viewmodel/useHelpPanelViewModel'
import { HelpCitationChip } from './HelpCitationChip'
import { helpPanelStyles as s } from './HelpPanel.styles'

/** 명세 12장의 문구입니다. 화면에서 새로 짓지 않습니다. */
const abstentionNotices: Record<HelpAbstention, string> = {
  OUT_OF_SCOPE_PROGRAM: '공고 내용은 도움말에서 답하지 않습니다. 원문 질문에서 공고 문장을 인용해 답해 드립니다.',
  OUT_OF_SCOPE_GENERAL: '지원사업 제도 일반은 확인해 드릴 수 없습니다. 공고를 낸 기관 안내를 확인해 주세요.',
  NOT_IN_HELP: '그 내용은 아직 안내에 없습니다.',
}

/** 답변이 AI가 만든 문장일 때만 생성 표시를 붙입니다. 항목을 그대로 보여 준 답변에는 붙이지 않습니다. */
export function HelpGeneratedAnswer({
  text,
  citations,
}: {
  text: string
  citations: readonly HelpEntry[]
}) {
  return (
    <div className={s.answer}>
      <span className={s.answerFlag}>◈ AI 생성</span>
      <p className={s.answerParagraph}>{text}</p>
      {citations.length > 0 && <div className={s.citations}>
        {citations.map((entry) => <HelpCitationChip entry={entry} key={entry.id} />)}
      </div>}
    </div>
  )
}

/** 근거가 없으면 답을 만들지 않고 갈 곳을 줍니다. */
export function HelpAbstentionCard({
  status,
  inApp,
  search,
  onAskProgram,
  children,
}: {
  status: HelpAbstention
  inApp: boolean
  search: string
  onAskProgram?: () => void
  children?: ReactNode
}) {
  const identity = supportProgramIdentityOf(search)

  return (
    <div className={s.answer}>
      <p className={s.answerParagraph}>{abstentionNotices[status]}</p>
      {status === 'OUT_OF_SCOPE_PROGRAM' && <div className={s.actions}>
        {identity && onAskProgram
          ? <button className={s.actionPrimary} type="button" onClick={onAskProgram}>이 공고에 질문하기</button>
          : <Link className={s.actionPrimary} to={inApp ? appPaths.chat : '/'}>공고 검색 열기</Link>}
      </div>}
      {status === 'OUT_OF_SCOPE_GENERAL' && identity && <div className={s.actions}>
        <Link className={s.actionSoft} to={supportProgramDetailPath(identity, inApp)}>공고 원문 열기</Link>
      </div>}
      {children}
    </div>
  )
}

/** 실패를 답으로 숨기지 않습니다. 질문은 그대로 남깁니다. */
export function HelpFailureCard({ onRetry }: { onRetry: () => void }) {
  return (
    <div className={s.warning} role="alert">
      <p className={s.warningTitle}>답변을 받지 못했습니다</p>
      <p className={s.warningNote}>잠시 뒤 다시 시도해 주세요. 질문은 그대로 남아 있습니다.</p>
      <div className={s.actions}>
        <button className={s.actionSoft} type="button" onClick={onRetry}>다시 시도</button>
      </div>
    </div>
  )
}

/**
 * 같은 패널에서 이어서 답한 공고 원문 근거 답변입니다. 근거의 출처가 도움말이 아니라 공고 원문임을
 * 배지로 밝히고, 인용은 원문 링크로 직접 확인할 수 있게 둡니다.
 */
export function HelpProgramAnswer({ answer }: { answer: SupportProgramEvidenceAnswer }) {
  return (
    <div className={s.answer}>
      <span className={s.answerFlag}>◈ AI 생성 · 공고 원문 근거</span>
      <p className={s.answerParagraph}>{answer.answer}</p>
      {answer.citations.length > 0 && <div className={s.citations}>
        {answer.citations.map((citation) => (
          <a
            className={s.citation}
            href={citation.sourceUrl}
            key={`${citation.chunkOrder}-${citation.sourceUrl}`}
            rel="noreferrer"
            target="_blank"
          >
            <i aria-hidden="true" className={s.citationMark}>◈</i>
            근거 {citation.chunkOrder + 1} 원문 보기 ↗
          </a>
        ))}
      </div>}
    </div>
  )
}
