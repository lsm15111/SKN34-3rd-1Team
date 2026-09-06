import { Link, useParams } from 'react-router'

import { RequireSignIn } from '../../auth/view/RequireSignIn'
import { useReceivedProposalsViewModel } from '../viewmodel/useReceivedProposalsViewModel'
import { ProposalCard } from './ProposalCard'
import { recruitmentStyles } from './Recruitment.styles'

/** 작성 기업이 자기 모집글에 받은 제안을 보고 수락·거절하는 화면(/partners/:postId/proposals)입니다. */
export function ReceivedProposalsPage() {
  const params = useParams()
  const postId = Number.parseInt(params.postId ?? '', 10)

  return (
    <RequireSignIn>
      {Number.isInteger(postId) && postId > 0
        ? <ReceivedProposalsContent key={postId} postId={postId} />
        : <Unavailable title="모집글을 찾을 수 없습니다" description="주소가 올바르지 않습니다. 내 모집글에서 다시 선택해 주세요." />}
    </RequireSignIn>
  )
}

function ReceivedProposalsContent({ postId }: { postId: number }) {
  const { decide, decidingId, notice, state } = useReceivedProposalsViewModel(postId)

  if (state.status === 'loading' && !state.post) {
    return <main className={recruitmentStyles.narrowPage}><p className={recruitmentStyles.hint}>받은 제안을 불러오는 중입니다.</p></main>
  }
  if (state.status === 'not-owner') {
    return <Unavailable title="작성 기업만 볼 수 있습니다" description="받은 제안은 모집글을 쓴 기업에게만 보입니다." />
  }
  if (state.status === 'not-found') {
    return <Unavailable title="모집글을 찾을 수 없습니다" description="삭제됐거나 더 이상 볼 수 없는 모집글입니다." />
  }
  if (state.status === 'failed') {
    return <Unavailable title="받은 제안을 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." />
  }

  const post = state.post

  return (
    <main className={recruitmentStyles.narrowPage}>
      <header className={recruitmentStyles.header}>
        <div>
          <Link className={recruitmentStyles.backLink} to={`/partners/${post.id}`}>← 모집글 상세</Link>
          <p className={recruitmentStyles.eyebrow}>받은 제안</p>
          <h1 className={recruitmentStyles.title}>{post.title}</h1>
          <p className={recruitmentStyles.description}>
            받은 제안 {state.proposals.length}건. 수락하면 서로의 담당자 이메일이 공개되고, 7일 안에 응답하지 않은 제안은 자동으로 종료됩니다.
          </p>
        </div>
      </header>
      {notice ? <p className={recruitmentStyles.notice} role="status">{notice}</p> : null}
      {state.proposals.length === 0 ? (
        <p className={recruitmentStyles.empty}>아직 받은 제안이 없습니다.</p>
      ) : (
        <div className={recruitmentStyles.cardList}>
          {state.proposals.map((proposal) => (
            <ProposalCard
              key={proposal.id}
              proposal={proposal}
              perspective="received"
              actions={proposal.status === 'PENDING' ? (
                <>
                  <button
                    type="button"
                    className={recruitmentStyles.primaryButton}
                    onClick={() => void decide(proposal.id, 'accept')}
                    disabled={decidingId !== null}
                  >
                    {decidingId === proposal.id ? '처리 중…' : '수락'}
                  </button>
                  <button
                    type="button"
                    className={recruitmentStyles.secondaryButton}
                    onClick={() => void decide(proposal.id, 'decline')}
                    disabled={decidingId !== null}
                  >
                    거절
                  </button>
                </>
              ) : null}
            />
          ))}
        </div>
      )}
    </main>
  )
}

function Unavailable({ title, description }: { title: string; description: string }) {
  return (
    <main className={recruitmentStyles.narrowPage}>
      <Link className={recruitmentStyles.backLink} to="/partners/mine">← 내 모집글</Link>
      <h1 className={recruitmentStyles.title}>{title}</h1>
      <p className={recruitmentStyles.description}>{description}</p>
    </main>
  )
}
