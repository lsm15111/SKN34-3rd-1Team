import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentProposal } from '../../../../domain/entities/RecruitmentProposal'
import type {
  ListSentProposalsUseCase,
  WithdrawProposalUseCase,
} from '../../../../domain/usecases/RecruitmentProposalUseCases'

type SentUseCase = Pick<ListSentProposalsUseCase, 'execute'>
type WithdrawUseCase = Pick<WithdrawProposalUseCase, 'execute'>

export const sentProposalMessages = {
  withdrawn: '제안을 철회했습니다. 같은 모집글에는 다시 제안할 수 없습니다.',
  notPending: '이미 결정됐거나 종료된 제안은 철회할 수 없습니다. 목록을 새로고침했습니다.',
  failed: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type SentState =
  | { status: 'loading'; proposals: RecruitmentProposal[] }
  | { status: 'ready'; proposals: RecruitmentProposal[] }
  | { status: 'error'; proposals: RecruitmentProposal[] }

/** 내 기업이 보낸 제안 전체를 보고, 대기 중인 제안을 철회합니다. */
export function useSentProposalsViewModel(
  listSentProposalsUseCase: SentUseCase = appContainer.resolve('listSentProposalsUseCase'),
  withdrawProposalUseCase: WithdrawUseCase = appContainer.resolve('withdrawProposalUseCase'),
) {
  const [state, setState] = useState<SentState>({ status: 'loading', proposals: [] })
  const [notice, setNotice] = useState<string | null>(null)
  const [withdrawingId, setWithdrawingId] = useState<number | null>(null)
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', proposals: current.proposals }))
    try {
      const proposals = await listSentProposalsUseCase.execute(controller.signal)
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'ready', proposals })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState((current) => ({ status: 'error', proposals: current.proposals }))
    }
  }, [listSentProposalsUseCase])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  async function withdraw(proposalId: number) {
    if (withdrawingId !== null) return
    setWithdrawingId(proposalId)
    setNotice(null)
    try {
      const result = await withdrawProposalUseCase.execute(proposalId)
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'decided':
          setState((current) => ({
            status: 'ready',
            proposals: current.proposals.map((item) => item.id === proposalId ? result.proposal : item),
          }))
          setNotice(sentProposalMessages.withdrawn)
          return
        case 'not-pending':
          setNotice(sentProposalMessages.notPending)
          void load()
          return
        case 'not-owner':
        case 'not-found':
          setNotice(sentProposalMessages.failed)
          void load()
          return
      }
    } catch {
      if (!isMounted.current) return
      setNotice(sentProposalMessages.failed)
    } finally {
      if (isMounted.current) setWithdrawingId(null)
    }
  }

  return {
    isLoading: state.status === 'loading',
    loadFailed: state.status === 'error',
    notice,
    proposals: state.proposals,
    reload: load,
    withdraw,
    withdrawingId,
  }
}
