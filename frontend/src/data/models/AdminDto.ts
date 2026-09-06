import { z } from 'zod'

import type { AdminAccount, AdminAccountPage } from '../../domain/entities/AdminAccount'
import { accountRoleSchema, companyDtoSchema, toCompany } from './AccountDto'

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
