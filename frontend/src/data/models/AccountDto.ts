import { z } from 'zod'

import type { Account, Company } from '../../domain/entities/Account'
import type { AuthSession } from '../../domain/entities/AuthSession'

/** Bizno 기업 확인·가입·내 계정 응답이 공유하는 기업 계약입니다. */
export const companyDtoSchema = z.object({
  businessNumber: z.string().regex(/^\d{10}$/),
  companyName: z.string().trim().min(1),
  businessStatus: z.string(),
})

export const businessLookupResponseDtoSchema = z.object({
  businesses: z.array(companyDtoSchema),
})

export const accountDtoSchema = z.object({
  email: z.string().trim().min(1).max(320),
  company: companyDtoSchema,
})

export const authSessionResponseDtoSchema = z.object({
  sessionToken: z.string().min(1),
  expiresAt: z.string().datetime({ offset: true }),
  account: accountDtoSchema,
})

export const currentAccountResponseDtoSchema = z.object({
  account: accountDtoSchema,
})

export type CompanyDto = z.infer<typeof companyDtoSchema>
export type AccountDto = z.infer<typeof accountDtoSchema>
export type AuthSessionResponseDto = z.infer<typeof authSessionResponseDtoSchema>

/** DTO를 복사해 View가 외부 HTTP 응답 객체를 직접 보유하지 않게 합니다. */
export function toCompany(dto: CompanyDto): Company {
  return {
    businessNumber: dto.businessNumber,
    companyName: dto.companyName,
    businessStatus: dto.businessStatus,
  }
}

export function toAccount(dto: AccountDto): Account {
  return {
    email: dto.email,
    company: toCompany(dto.company),
  }
}

export function toAuthSession(dto: AuthSessionResponseDto): AuthSession {
  return {
    sessionToken: dto.sessionToken,
    expiresAt: dto.expiresAt,
    account: toAccount(dto.account),
  }
}
