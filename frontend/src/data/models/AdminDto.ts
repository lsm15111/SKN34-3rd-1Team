import { z } from 'zod'

import type { AdminAccount, AdminAccountPage } from '../../domain/entities/AdminAccount'
import type { AdminRecruitmentPost, AdminRecruitmentPostPage } from '../../domain/entities/AdminRecruitmentPost'
import { accountRoleSchema, companyDtoSchema, toCompany } from './AccountDto'
import { linkedProgramDtoSchema, toLinkedProgram } from './RecruitmentPostDto'

export const adminAccountDtoSchema = z.object({
  id: z.number().int().positive(),
  email: z.string().trim().min(1).max(320),
  role: accountRoleSchema,
  company: companyDtoSchema,
  createdAt: z.string().datetime({ offset: true }),
})

export const adminAccountPageDtoSchema = z.object({
  items: z.array(adminAccountDtoSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalCount: z.number().int().nonnegative(),
})

export const adminRecruitmentPostDtoSchema = z.object({
  id: z.number().int().positive(),
  status: z.enum(['OPEN', 'CLOSED', 'HIDDEN']),
  title: z.string(),
  ourRole: z.enum(['LEAD', 'PARTICIPANT']),
  wantedRole: z.enum(['LEAD', 'PARTICIPANT', 'DEMAND']),
  closesOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  closedEarlyAt: z.string().datetime({ offset: true }).nullable(),
  hiddenAt: z.string().datetime({ offset: true }).nullable(),
  hiddenReason: z.string().nullable(),
  createdAt: z.string().datetime({ offset: true }),
  company: companyDtoSchema,
  program: linkedProgramDtoSchema.nullable(),
  proposalCount: z.number().int().nonnegative(),
})

export const adminRecruitmentPostPageDtoSchema = z.object({
  items: z.array(adminRecruitmentPostDtoSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalCount: z.number().int().nonnegative(),
})

export type AdminRecruitmentPostDto = z.infer<typeof adminRecruitmentPostDtoSchema>
export type AdminRecruitmentPostPageDto = z.infer<typeof adminRecruitmentPostPageDtoSchema>

export function toAdminRecruitmentPost(dto: AdminRecruitmentPostDto): AdminRecruitmentPost {
  return {
    id: dto.id,
    status: dto.status,
    title: dto.title,
    ourRole: dto.ourRole,
    wantedRole: dto.wantedRole,
    closesOn: dto.closesOn,
    closedEarlyAt: dto.closedEarlyAt,
    hiddenAt: dto.hiddenAt,
    hiddenReason: dto.hiddenReason,
    createdAt: dto.createdAt,
    company: toCompany(dto.company),
    program: dto.program ? toLinkedProgram(dto.program) : null,
    proposalCount: dto.proposalCount,
  }
}

export function toAdminRecruitmentPostPage(dto: AdminRecruitmentPostPageDto): AdminRecruitmentPostPage {
  return {
    items: dto.items.map(toAdminRecruitmentPost),
    page: dto.page,
    size: dto.size,
    totalCount: dto.totalCount,
  }
}

export type AdminAccountDto = z.infer<typeof adminAccountDtoSchema>
export type AdminAccountPageDto = z.infer<typeof adminAccountPageDtoSchema>

export function toAdminAccount(dto: AdminAccountDto): AdminAccount {
  return {
    id: dto.id,
    email: dto.email,
    role: dto.role,
    company: toCompany(dto.company),
    createdAt: dto.createdAt,
  }
}

export function toAdminAccountPage(dto: AdminAccountPageDto): AdminAccountPage {
  return {
    items: dto.items.map(toAdminAccount),
    page: dto.page,
    size: dto.size,
    totalCount: dto.totalCount,
  }
}
