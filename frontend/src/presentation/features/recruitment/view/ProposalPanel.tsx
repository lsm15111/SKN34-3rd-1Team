import { Link, useLocation } from 'react-router'

import type { RecruitmentPost } from '../../../../domain/entities/RecruitmentPost'
import { PROPOSAL_MESSAGE_MAX_LENGTH, proposalStatusLabels } from '../../../../domain/entities/RecruitmentProposal'
import { useAuthSessionViewModel } from '../../auth/viewmodel/useAuthSessionViewModel'
import { useProposalPanelViewModel } from '../viewmodel/useProposalPanelViewModel'
import { proposalStatusClassName, recruitmentStyles } from './Recruitment.styles'

type ProposalPanelProps = {
  post: RecruitmentPost
  onPostChanged: () => void
}

/** 모집글 상세의 제안 영역입니다. 로그인·작성 기업 여부·기존 제안 유무에 따라 다른 내용을 보여 줍니다. */
export function ProposalPanel({ post, onPostChanged }: ProposalPanelProps) {
  const session = useAuthSessionViewModel()
  const location = useLocation()

  if (session.status === 'unknown') {
    return <Section><p className={recruitmentStyles.hint}>로그인 상태를 확인하고 있습니다.</p></Section>
  }
  if (!session.isAuthenticated) {
    return (
      <Section>
        <p className={recruitmentStyles.description}>로그인한 기업만 참여를 제안할 수 있습니다.</p>
        <Link
          className={`${recruitmentStyles.primaryButton} mt-4`}
          to="/login"
          state={{ from: `${location.pathname}${location.search}` }}
        >
          로그인하고 제안하기
        </Link>
      </Section>
    )
  }
  if (post.viewer.isOwner) {
    return (
      <Section>
        <p className={recruitmentStyles.description}>이 글에 받은 제안 {post.proposalCount}건</p>
        <Link className={`${recruitmentStyles.primaryButton} mt-4`} to={`/partners/${post.id}/proposals`}>
          받은 제안 보기
        </Link>
      </Section>
    )
  }
  return <ProposalForm post={post} onPostChanged={onPostChanged} />
}

function ProposalForm({ post, onPostChanged }: ProposalPanelProps) {
  const viewModel = useProposalPanelViewModel(post, onPostChanged)
  const { errors, hasMyProposal, isSubmitting, isWithdrawing, messageValue, myProposal, notice, registerField, submit, withdraw } = viewModel

  function handleWithdraw() {
    if (!window.confirm('제안을 철회할까요? 철회하면 같은 모집글에 다시 제안할 수 없습니다.')) return
    void withdraw()
  }

  if (hasMyProposal) {
    const status = myProposal.status === 'ready' ? myProposal.proposal.status : post.viewer.myProposalStatus
    return (
      <Section>
        {notice ? <p className={recruitmentStyles.notice} role="status">{notice}</p> : null}
        <p className={recruitmentStyles.description}>
          내 기업이 보낸 제안{' '}
          {status ? <span className={proposalStatusClassName(status)}>{proposalStatusLabels[status]}</span> : null}
        </p>
        {myProposal.status === 'ready' ? (
          <>
            <p className={recruitmentStyles.proposalMessage}>{myProposal.proposal.message}</p>
            {myProposal.proposal.contactEmail ? (
              <p className={recruitmentStyles.contactBox}>
                작성 담당자 이메일{' '}
                <a className={recruitmentStyles.contactLink} href={`mailto:${myProposal.proposal.contactEmail}`}>
                  {myProposal.proposal.contactEmail}
                </a>
              </p>
            ) : null}
            {myProposal.proposal.status === 'PENDING' ? (
              <button type="button" className={`${recruitmentStyles.dangerButton} mt-4`} onClick={handleWithdraw} disabled={isWithdrawing}>
                {isWithdrawing ? '철회 중…' : '제안 철회'}
              </button>
            ) : null}
          </>
        ) : null}
        {myProposal.status === 'loading' ? <p className={recruitmentStyles.hint}>보낸 제안을 불러오는 중입니다.</p> : null}
        {myProposal.status === 'failed' ? <p className={recruitmentStyles.hint}>보낸 제안 정보를 불러오지 못했습니다. 내 모집글의 보낸 제안 탭에서 확인해 주세요.</p> : null}
        <p className={recruitmentStyles.hint}>
          <Link className={recruitmentStyles.backLink} to="/partners/mine?tab=sent">보낸 제안 전체 보기</Link>
        </p>
      </Section>
    )
  }

  if (post.status !== 'OPEN') {
    return <Section><p className={recruitmentStyles.description}>모집이 종료되어 제안할 수 없습니다.</p></Section>
  }

  return (
    <Section>
      {notice ? <p className={recruitmentStyles.notice} role="status">{notice}</p> : null}
      <form className="grid gap-3" noValidate onSubmit={submit}>
        <label className={recruitmentStyles.field} htmlFor="proposal-message">
          제안 메시지
          <textarea
            id="proposal-message"
            className={recruitmentStyles.textarea}
            maxLength={PROPOSAL_MESSAGE_MAX_LENGTH}
            placeholder="우리 기업이 맡을 수 있는 일과 관련 경험을 적어 주세요."
            aria-invalid={errors.message ? true : undefined}
            {...registerField('message')}
          />
          <span className={recruitmentStyles.fieldHint}>
            {messageValue.length}/{PROPOSAL_MESSAGE_MAX_LENGTH}자. 연락처·이메일은 적지 마세요. 수락되면 서로의 담당자 이메일이 자동으로 공개됩니다.
          </span>
          {errors.message?.message ? <span className={recruitmentStyles.fieldError}>{errors.message.message}</span> : null}
        </label>
        <button type="submit" className={recruitmentStyles.primaryButton} disabled={isSubmitting}>
          {isSubmitting ? '보내는 중…' : '참여 제안 보내기'}
        </button>
        <p className={recruitmentStyles.hint}>한 기업은 모집글 하나에 한 번만 제안할 수 있고, 7일 안에 응답이 없으면 자동으로 종료됩니다.</p>
      </form>
    </Section>
  )
}

function Section({ children }: { children: React.ReactNode }) {
  return (
    <section className={recruitmentStyles.section} aria-labelledby="proposal-title">
      <h2 id="proposal-title" className={recruitmentStyles.sectionTitle}>참여 제안</h2>
      {children}
    </section>
  )
}
