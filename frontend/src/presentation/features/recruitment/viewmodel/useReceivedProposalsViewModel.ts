import { useCallback, useEffect, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentPost } from '../../../../domain/entities/RecruitmentPost'
import type { RecruitmentProposal } from '../../../../domain/entities/RecruitmentProposal'
import type { GetRecruitmentPostUseCase } from '../../../../domain/usecases/RecruitmentPostUseCases'
import type {
  DecideProposalUseCase,
  ListReceivedProposalsUseCase,
  ProposalDecision,
} from '../../../../domain/usecases/RecruitmentProposalUseCases'

type PostUseCase = Pick<GetRecruitmentPostUseCase, 'execute'>
type ReceivedUseCase = Pick<ListReceivedProposalsUseCase, 'execute'>
type DecideUseCase = Pick<DecideProposalUseCase, 'execute'>

export const receivedProposalMessages = {
  accepted: '제안을 수락했습니다. 이제 서로의 담당자 이메일이 공개됩니다.',
  declined: '제안을 거절했습니다.',
  notPending: '이미 결정됐거나 만료·종료된 제안입니다. 목록을 새로고침했습니다.',
  notOwner: '작성 기업만 제안을 결정할 수 있습니다.',
  failed: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

type ReceivedState =
  | { status: 'loading'; post: null; proposals: RecruitmentProposal[] }
  | { status: 'ready'; post: RecruitmentPost; proposals: RecruitmentProposal[] }
  | { status: 'not-owner'; post: null; proposals: RecruitmentProposal[] }
  | { status: 'not-found'; post: null; proposals: RecruitmentProposal[] }
  | { status: 'failed'; post: null; proposals: RecruitmentProposal[] }

/** 작성 기업이 자기 모집글에 받은 제안을 보고 수락·거절합니다. */
export function useReceivedProposalsViewModel(
  postId: number,
  getRecruitmentPostUseCase: PostUseCase = appContainer.resolve('getRecruitmentPostUseCase'),
  listReceivedProposalsUseCase: ReceivedUseCase = appContainer.resolve('listReceivedProposalsUseCase'),
  decideProposalUseCase: DecideUseCase = appContainer.resolve('decideProposalUseCase'),
) {
  const [state, setState] = useState<ReceivedState>({ status: 'loading', post: null, proposals: [] })
  const [notice, setNotice] = useState<string | null>(null)
  const [decidingId, setDecidingId] = useState<number | null>(null)
  const activeController = useRef<AbortController | null>(null)
  const isMounted = useRef(true)

  const load = useCallback(async () => {
    activeController.current?.abort()
    const controller = new AbortController()
    activeController.current = controller
    setState((current) => ({ status: 'loading', post: null, proposals: current.proposals }))
    try {
      const [post, received] = await Promise.all([
        getRecruitmentPostUseCase.execute(postId, controller.signal),
        listReceivedProposalsUseCase.execute(postId, controller.signal),
      ])
      if (!isMounted.current || controller.signal.aborted) return
      if (received.outcome === 'not-owner') return setState({ status: 'not-owner', post: null, proposals: [] })
      if (received.outcome === 'not-found' || !post) return setState({ status: 'not-found', post: null, proposals: [] })
      setState({ status: 'ready', post, proposals: received.proposals })
    } catch {
      if (!isMounted.current || controller.signal.aborted) return
      setState({ status: 'failed', post: null, proposals: [] })
    }
  }, [getRecruitmentPostUseCase, listReceivedProposalsUseCase, postId])

  useEffect(() => {
    isMounted.current = true
    void load()
    return () => {
      isMounted.current = false
      activeController.current?.abort()
    }
  }, [load])

  async function decide(proposalId: number, decision: ProposalDecision) {
    if (decidingId !== null || state.status !== 'ready') return
    setDecidingId(proposalId)
    setNotice(null)
    try {
      const result = await decideProposalUseCase.execute(proposalId, decision)
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'decided':
          setState((current) => current.status === 'ready'
            ? { ...current, proposals: current.proposals.map((item) => item.id === proposalId ? result.proposal : item) }
            : current)
          setNotice(decision === 'accept' ? receivedProposalMessages.accepted : receivedProposalMessages.declined)
          return
        case 'not-pending':
          setNotice(receivedProposalMessages.notPending)
          void load()
          return
        case 'not-owner':
          setNotice(receivedProposalMessages.notOwner)
          return
        case 'not-found':
          setNotice(receivedProposalMessages.failed)
          void load()
          return
      }
    } catch {
      if (!isMounted.current) return
      setNotice(receivedProposalMessages.failed)
    } finally {
      if (isMounted.current) setDecidingId(null)
    }
  }

  return {
    decide,
    decidingId,
    notice,
    reload: load,
    state,
  }
}
