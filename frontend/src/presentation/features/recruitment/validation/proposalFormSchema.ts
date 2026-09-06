import { z } from 'zod'

import { PROPOSAL_MESSAGE_MAX_LENGTH } from '../../../../domain/entities/RecruitmentProposal'
import { containsContact } from './recruitmentPostFormSchema'

/** 제안 메시지 규칙입니다. Core API의 제안 검증·ContactPatternPolicy와 같습니다. */
export const proposalFormSchema = z.object({
  message: z.string().trim()
    .min(1, '제안 메시지를 입력해 주세요.')
    .max(PROPOSAL_MESSAGE_MAX_LENGTH, `제안 메시지는 ${PROPOSAL_MESSAGE_MAX_LENGTH}자 이하여야 합니다.`)
    .refine((value) => !containsContact(value), {
      message: '메시지에는 이메일·전화번호를 적지 마세요. 담당자 연락처는 제안이 수락된 뒤 공개됩니다.',
    }),
})

export type ProposalFormValues = z.infer<typeof proposalFormSchema>
