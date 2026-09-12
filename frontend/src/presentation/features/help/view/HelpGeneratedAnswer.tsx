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

/**
 * 모델이 만든 답변입니다. 생성 표시와 근거 수를 본문 아래 한 줄로 붙이고 인용에 같은 번호를 답니다.
 * 표시를 본문 위 배지로 올리면 답변보다 먼저 읽혀 정작 내용을 가립니다.
 */
export function HelpGeneratedAnswer({
  text,
  citations,
}: {
  text: string
  citations: readonly HelpEntry[]
}) {
  return (
    <div className={s.answer}>
      <p className={s.answerParagraph}>{text}</p>
      <p className={s.meta}>
        <span className={s.metaMark}>◈</span>
        {citations.length > 0 ? `AI 생성 · 도움말 ${citations.length}항목 근거` : 'AI 생성'}
      </p>
      {citations.length > 0 && <div className={s.citations}>
        {citations.map((entry, index) => <HelpCitationChip entry={entry} key={entry.id} order={index + 1} />)}
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
      {children && <div className={s.relatedBlock}>{children}</div>}
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
 * 같은 패널에서 이어서 답한 공고 원문 근거 답변입니다. 근거가 도움말이 아니라 공고 원문임을 메타 줄에
 * 밝히고, 인용은 원문 링크로 직접 확인할 수 있게 둡니다.
 */
export function HelpProgramAnswer({ answer }: { answer: SupportProgramEvidenceAnswer }) {
  return (
    <div className={s.answer}>
      <p className={s.answerParagraph}>{answer.answer}</p>
      <p className={s.meta}>
        <span className={s.metaMark}>◈</span>
        {`AI 생성 · 공고 원문 ${answer.citations.length}곳 근거`}
      </p>
      {answer.citations.length > 0 && <div className={s.citations}>
        {answer.citations.map((citation, index) => (
          <a
            className={s.citation}
            href={citation.sourceUrl}
            key={`${citation.chunkOrder}-${citation.sourceUrl}`}
            rel="noreferrer"
            target="_blank"
          >
            <span aria-hidden="true" className={s.citationNumber}>{index + 1}</span>
            근거 {index + 1} 원문 보기 ↗
          </a>
        ))}
      </div>}
    </div>
  )
}
