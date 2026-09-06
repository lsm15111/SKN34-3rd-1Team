import { z } from 'zod'

import type { RecruitmentProposal } from '../../domain/entities/RecruitmentProposal'
import { companyDtoSchema, toCompany } from './AccountDto'

export const proposalStatusSchema = z.enum(['PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'EXPIRED', 'CLOSED'])

export const recruitmentProposalDtoSchema = z.object({
  id: z.number().int().positive(),
  postId: z.number().int().positive(),
  status: proposalStatusSchema,
  message: z.string(),
  createdAt: z.string().datetime({ offset: true }),
  decidedAt: z.string().datetime({ offset: true }).nullable(),
  company: companyDtoSchema,
  post: z.object({
    id: z.number().int().positive(),
    status: z.enum(['OPEN', 'CLOSED', 'HIDDEN']),
    title: z.string(),
    closesOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    companyName: z.string(),
  }),
  contactEmail: z.string().nullable(),
})

export const recruitmentProposalListDtoSchema = z.object({
  items: z.array(recruitmentProposalDtoSchema),
})

export type RecruitmentProposalDto = z.infer<typeof recruitmentProposalDtoSchema>

/** DTO를 복사해 View가 외부 HTTP 응답 객체를 직접 보유하지 않게 합니다. */
export function toRecruitmentProposal(dto: RecruitmentProposalDto): RecruitmentProposal {
  return {
    id: dto.id,
    postId: dto.postId,
    status: dto.status,
    message: dto.message,
    createdAt: dto.createdAt,
    decidedAt: dto.decidedAt,
    company: toCompany(dto.company),
    post: {
      id: dto.post.id,
      status: dto.post.status,
      title: dto.post.title,
      closesOn: dto.post.closesOn,
      companyName: dto.post.companyName,
    },
    contactEmail: dto.contactEmail,
  }
}
