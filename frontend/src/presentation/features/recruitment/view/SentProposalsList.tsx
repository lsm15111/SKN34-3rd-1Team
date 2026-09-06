import { useSentProposalsViewModel } from '../viewmodel/useSentProposalsViewModel'
import { ProposalCard } from './ProposalCard'
import { recruitmentStyles } from './Recruitment.styles'

/** 내 모집글 화면의 "보낸 제안" 탭입니다. 대기 중인 제안은 철회할 수 있습니다. */
export function SentProposalsList() {
  const { isLoading, loadFailed, notice, proposals, reload, withdraw, withdrawingId } = useSentProposalsViewModel()

  function handleWithdraw(proposalId: number) {
    if (!window.confirm('제안을 철회할까요? 철회하면 같은 모집글에 다시 제안할 수 없습니다.')) return
    void withdraw(proposalId)
  }

  return (
    <>
      {notice ? <p className={recruitmentStyles.notice} role="status">{notice}</p> : null}
      {loadFailed ? (
        <p className={recruitmentStyles.error} role="alert">
          보낸 제안을 불러오지 못했습니다.{' '}
          <button type="button" className={recruitmentStyles.backLink} onClick={() => void reload()}>다시 시도</button>
        </p>
      ) : null}
      {proposals.length === 0 ? (
        <p className={recruitmentStyles.empty}>
          {isLoading ? '불러오는 중입니다.' : '아직 보낸 제안이 없습니다. 모집글 상세에서 참여를 제안해 보세요.'}
        </p>
      ) : (
        <div className={recruitmentStyles.cardList}>
          {proposals.map((proposal) => (
            <ProposalCard
              key={proposal.id}
              proposal={proposal}
              perspective="sent"
              actions={proposal.status === 'PENDING' ? (
                <button
                  type="button"
                  className={recruitmentStyles.dangerButton}
                  onClick={() => handleWithdraw(proposal.id)}
                  disabled={withdrawingId !== null}
                >
                  {withdrawingId === proposal.id ? '철회 중…' : '제안 철회'}
                </button>
              ) : null}
            />
          ))}
        </div>
      )}
    </>
  )
}
