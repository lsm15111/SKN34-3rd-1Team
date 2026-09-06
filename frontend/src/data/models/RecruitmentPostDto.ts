import { z } from 'zod'

import type { LinkedProgram, RecruitmentPost, RecruitmentPostPage } from '../../domain/entities/RecruitmentPost'
import { companyDtoSchema, toCompany } from './AccountDto'
import { proposalStatusSchema } from './RecruitmentProposalDto'

const recruitmentRoleSchema = z.enum(['LEAD', 'PARTICIPANT', 'DEMAND'])

export const linkedProgramDtoSchema = z.object({
  sourceCode: z.string().min(1),
  sourceProgramId: z.string().min(1),
  title: z.string(),
  organization: z.string(),
  status: z.enum(['OPEN', 'UPCOMING', 'CLOSED', 'UNKNOWN']),
  applicationPeriod: z.string(),
  applicationEndDate: z.string().nullable(),
  targetDescription: z.string(),
  sourceUrl: z.string().url(),
})

export const recruitmentPostDtoSchema = z.object({
  id: z.number().int().positive(),
  status: z.enum(['OPEN', 'CLOSED', 'HIDDEN']),
  title: z.string(),
  body: z.string(),
  ourRole: z.enum(['LEAD', 'PARTICIPANT']),
  wantedRole: recruitmentRoleSchema,
  wantedCompanyCount: z.number().int().min(1).max(10),
  wantedRegion: z.string(),
  requiredCapabilities: z.array(z.string()).max(10),
  closesOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  closedEarlyAt: z.string().datetime({ offset: true }).nullable(),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
  company: companyDtoSchema,
  program: linkedProgramDtoSchema.nullable(),
  proposalCount: z.number().int().nonnegative(),
  viewer: z.object({ isOwner: z.boolean(), myProposalStatus: proposalStatusSchema.nullable() }),
})

export const recruitmentPostPageDtoSchema = z.object({
  items: z.array(recruitmentPostDtoSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalCount: z.number().int().nonnegative(),
})

export const recruitmentPostListDtoSchema = z.object({
  items: z.array(recruitmentPostDtoSchema),
})

export type RecruitmentPostDto = z.infer<typeof recruitmentPostDtoSchema>
export type RecruitmentPostPageDto = z.infer<typeof recruitmentPostPageDtoSchema>

function toLinkedProgram(dto: z.infer<typeof linkedProgramDtoSchema>): LinkedProgram {
  return {
    sourceCode: dto.sourceCode,
    sourceProgramId: dto.sourceProgramId,
    title: dto.title,
    organization: dto.organization,
    status: dto.status,
    applicationPeriod: dto.applicationPeriod,
    applicationEndDate: dto.applicationEndDate,
    targetDescription: dto.targetDescription,
    sourceUrl: dto.sourceUrl,
  }
}

/** DTO를 복사해 View가 외부 HTTP 응답 객체를 직접 보유하지 않게 합니다. */
export function toRecruitmentPost(dto: RecruitmentPostDto): RecruitmentPost {
  return {
    id: dto.id,
    status: dto.status,
    title: dto.title,
    body: dto.body,
    ourRole: dto.ourRole,
    wantedRole: dto.wantedRole,
    wantedCompanyCount: dto.wantedCompanyCount,
    wantedRegion: dto.wantedRegion,
    requiredCapabilities: [...dto.requiredCapabilities],
    closesOn: dto.closesOn,
    closedEarlyAt: dto.closedEarlyAt,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    company: toCompany(dto.company),
    program: dto.program ? toLinkedProgram(dto.program) : null,
    proposalCount: dto.proposalCount,
    viewer: { isOwner: dto.viewer.isOwner, myProposalStatus: dto.viewer.myProposalStatus },
  }
}

export function toRecruitmentPostPage(dto: RecruitmentPostPageDto): RecruitmentPostPage {
  return {
    items: dto.items.map(toRecruitmentPost),
    page: dto.page,
    size: dto.size,
    totalCount: dto.totalCount,
  }
}
