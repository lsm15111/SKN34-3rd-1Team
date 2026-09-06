import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { proposalStatusLabels, type RecruitmentProposal } from '../../../../domain/entities/RecruitmentProposal'
import { formatDate } from '../viewmodel/recruitmentDates'
import { proposalStatusClassName, recruitmentStyles } from './Recruitment.styles'

type ProposalCardProps = {
  proposal: RecruitmentProposal
  /** received는 제안 기업을, sent는 대상 모집글을 머리글로 보여 줍니다. */
  perspective: 'received' | 'sent'
  actions?: ReactNode
}

/** 받은/보낸 제안 목록이 함께 쓰는 제안 카드입니다. 수락된 제안에서만 상대 담당자 이메일이 보입니다. */
export function ProposalCard({ proposal, perspective, actions }: ProposalCardProps) {
  return (
    <article className={recruitmentStyles.card} aria-label={`제안 ${proposal.id}`}>
      <div className={recruitmentStyles.cardTop}>
        {perspective === 'received' ? (
          <div className={recruitmentStyles.cardCompany}>
            <span className={recruitmentStyles.companyMark} aria-hidden="true">{proposal.company.companyName.slice(0, 1)}</span>
            <span>{proposal.company.companyName}</span>
            <span className={recruitmentStyles.chip}>사업자등록번호 {proposal.company.businessNumber}</span>
          </div>
        ) : (
          <div className={recruitmentStyles.cardCompany}>
            <Link className={recruitmentStyles.cardTitleLink} to={`/partners/${proposal.post.id}`}>{proposal.post.title}</Link>
            <span className={recruitmentStyles.chip}>{proposal.post.companyName}</span>
          </div>
        )}
        <span className={proposalStatusClassName(proposal.status)}>{proposalStatusLabels[proposal.status]}</span>
      </div>
      <p className={recruitmentStyles.proposalMessage}>{proposal.message}</p>
      <p className={recruitmentStyles.cardProgram}>
        보낸 날짜 {formatDate(proposal.createdAt)}
        {proposal.decidedAt ? ` · 결정 ${formatDate(proposal.decidedAt)}` : ''}
        {perspective === 'sent' ? ` · 모집 마감일 ${proposal.post.closesOn}` : ''}
      </p>
      {proposal.contactEmail ? (
        <p className={recruitmentStyles.contactBox}>
          담당자 이메일 <a className={recruitmentStyles.contactLink} href={`mailto:${proposal.contactEmail}`}>{proposal.contactEmail}</a>
        </p>
      ) : null}
      {actions ? <div className={recruitmentStyles.cardActions}>{actions}</div> : null}
    </article>
  )
}
