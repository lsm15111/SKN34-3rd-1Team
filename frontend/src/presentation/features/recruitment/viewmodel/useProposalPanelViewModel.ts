import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'

import { appContainer } from '../../../../app/appContainer'
import type { RecruitmentPost } from '../../../../domain/entities/RecruitmentPost'
import type { RecruitmentProposal } from '../../../../domain/entities/RecruitmentProposal'
import type {
  ListSentProposalsUseCase,
  SendProposalUseCase,
  WithdrawProposalUseCase,
} from '../../../../domain/usecases/RecruitmentProposalUseCases'
import { proposalFormSchema, type ProposalFormValues } from '../validation/proposalFormSchema'

type SendUseCase = Pick<SendProposalUseCase, 'execute'>
type SentUseCase = Pick<ListSentProposalsUseCase, 'execute'>
type WithdrawUseCase = Pick<WithdrawProposalUseCase, 'execute'>

export const proposalPanelMessages = {
  sent: '제안을 보냈습니다. 작성 기업이 7일 안에 응답하지 않으면 자동으로 종료됩니다.',
  ownPost: '내 기업의 모집글에는 제안할 수 없습니다.',
  alreadyExists: '이 모집글에는 이미 제안을 보냈습니다. 철회·거절 뒤에도 다시 보낼 수 없습니다.',
  notOpen: '모집이 종료되어 제안할 수 없습니다.',
  contactInText: '메시지에는 이메일·전화번호를 적지 마세요. 담당자 연락처는 제안이 수락된 뒤 공개됩니다.',
  notFound: '모집글을 더 이상 볼 수 없습니다.',
  sendFailed: '제안을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.',
  withdrawn: '제안을 철회했습니다. 같은 모집글에는 다시 제안할 수 없습니다.',
  withdrawNotPending: '이미 결정됐거나 종료된 제안은 철회할 수 없습니다.',
  withdrawFailed: '제안을 철회하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  myProposalFailed: '보낸 제안 정보를 불러오지 못했습니다.',
} as const

type MyProposalState =
  | { status: 'none'; proposal: null }
  | { status: 'loading'; proposal: null }
  | { status: 'ready'; proposal: RecruitmentProposal }
  | { status: 'failed'; proposal: null }

/**
 * 모집글 상세 오른쪽의 제안 영역입니다. 다른 기업이면 제안 폼을, 이미 보낸 제안이 있으면 보낸 제안 목록에서 찾아 상태·철회를
 * 다룹니다. 보내거나 철회한 뒤에는 상세를 조용히 다시 읽어 제안 수·내 제안 상태를 맞춥니다.
 */
export function useProposalPanelViewModel(
  post: RecruitmentPost,
  onPostChanged: () => void,
  sendProposalUseCase: SendUseCase = appContainer.resolve('sendProposalUseCase'),
  listSentProposalsUseCase: SentUseCase = appContainer.resolve('listSentProposalsUseCase'),
  withdrawProposalUseCase: WithdrawUseCase = appContainer.resolve('withdrawProposalUseCase'),
) {
  const [myProposal, setMyProposal] = useState<MyProposalState>({ status: 'none', proposal: null })
  const [notice, setNotice] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isWithdrawing, setIsWithdrawing] = useState(false)
  const isMounted = useRef(true)
  const form = useForm<ProposalFormValues>({
    resolver: zodResolver(proposalFormSchema),
    defaultValues: { message: '' },
    mode: 'onSubmit',
  })

  const hasMyProposal = post.viewer.myProposalStatus !== null

  useEffect(() => {
    isMounted.current = true
    return () => {
      isMounted.current = false
    }
  }, [])

  useEffect(() => {
    if (!hasMyProposal) {
      setMyProposal({ status: 'none', proposal: null })
      return
    }
    // 보낸 직후에는 응답으로 받은 제안을 이미 갖고 있으므로 다시 읽지 않습니다.
    if (myProposal.status === 'ready' && myProposal.proposal.postId === post.id) return

    const controller = new AbortController()
    setMyProposal({ status: 'loading', proposal: null })
    listSentProposalsUseCase.execute(controller.signal)
      .then((proposals) => {
        if (!isMounted.current || controller.signal.aborted) return
        const mine = proposals.find((proposal) => proposal.postId === post.id)
        setMyProposal(mine ? { status: 'ready', proposal: mine } : { status: 'failed', proposal: null })
      })
      .catch(() => {
        if (!isMounted.current || controller.signal.aborted) return
        setMyProposal({ status: 'failed', proposal: null })
      })
    return () => controller.abort()
    // myProposal은 이 effect가 바꾸는 값이라 의존성에서 뺍니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasMyProposal, listSentProposalsUseCase, post.id])

  const submit = form.handleSubmit(async (values) => {
    if (isSubmitting) return
    setIsSubmitting(true)
    setNotice(null)
    try {
      const result = await sendProposalUseCase.execute(post.id, values.message)
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'sent':
          setMyProposal({ status: 'ready', proposal: result.proposal })
          setNotice(proposalPanelMessages.sent)
          form.reset()
          onPostChanged()
          return
        case 'own-post':
          setNotice(proposalPanelMessages.ownPost)
          return
        case 'already-exists':
          setNotice(proposalPanelMessages.alreadyExists)
          onPostChanged()
          return
        case 'not-open':
          setNotice(proposalPanelMessages.notOpen)
          onPostChanged()
          return
        case 'contact-in-text':
          form.setError('message', { message: proposalPanelMessages.contactInText })
          return
        case 'not-found':
          setNotice(proposalPanelMessages.notFound)
          return
      }
    } catch {
      if (!isMounted.current) return
      setNotice(proposalPanelMessages.sendFailed)
    } finally {
      if (isMounted.current) setIsSubmitting(false)
    }
  })

  async function withdraw() {
    if (isWithdrawing || myProposal.status !== 'ready') return
    setIsWithdrawing(true)
    setNotice(null)
    try {
      const result = await withdrawProposalUseCase.execute(myProposal.proposal.id)
      if (!isMounted.current) return
      switch (result.outcome) {
        case 'decided':
          setMyProposal({ status: 'ready', proposal: result.proposal })
          setNotice(proposalPanelMessages.withdrawn)
          onPostChanged()
          return
        case 'not-pending':
          setNotice(proposalPanelMessages.withdrawNotPending)
          onPostChanged()
          return
        case 'not-owner':
        case 'not-found':
          setNotice(proposalPanelMessages.withdrawFailed)
          return
      }
    } catch {
      if (!isMounted.current) return
      setNotice(proposalPanelMessages.withdrawFailed)
    } finally {
      if (isMounted.current) setIsWithdrawing(false)
    }
  }

  return {
    errors: form.formState.errors,
    hasMyProposal,
    isSubmitting,
    isWithdrawing,
    messageValue: form.watch('message'),
    myProposal,
    notice,
    registerField: form.register,
    submit,
    withdraw,
  }
}
