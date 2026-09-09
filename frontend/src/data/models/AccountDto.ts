import { z } from 'zod'

import type { Account } from '../../domain/entities/Account'
import type { AuthSession } from '../../domain/entities/AuthSession'

export const accountRoleSchema = z.enum(['USER', 'ADMIN'])
export const accountTierSchema = z.enum(['MEMBER', 'COMPANY', 'ADMIN'])

export const accountCompanySummaryDtoSchema = z.object({
  companyName: z.string().trim().min(1),
  businessNumber: z.string().regex(/^\d{10}$/),
})

export const accountDtoSchema = z.object({
  email: z.string().trim().min(1).max(320),
  role: accountRoleSchema,
  tier: accountTierSchema,
  emailVerified: z.boolean(),
  company: accountCompanySummaryDtoSchema.nullable().optional().transform((value) => value ?? null),
})

/** 세션 토큰은 HttpOnly 쿠키로만 오므로 본문에는 만료 시각과 계정만 있습니다. */
export const authSessionResponseDtoSchema = z.object({
  expiresAt: z.string().datetime({ offset: true }),
  account: accountDtoSchema,
})

export const currentAccountResponseDtoSchema = z.object({
  account: accountDtoSchema,
})

/** 설정된 소셜 로그인 제공처 키입니다. 모르는 값은 버리고 아는 것만 남깁니다. */
export const oauthProvidersResponseDtoSchema = z.object({
  providers: z.array(z.string()),
})

export type AccountDto = z.infer<typeof accountDtoSchema>
export type AuthSessionResponseDto = z.infer<typeof authSessionResponseDtoSchema>

/** DTO를 복사해 View가 외부 HTTP 응답 객체를 직접 보유하지 않게 합니다. */
export function toAccount(dto: AccountDto): Account {
  return {
    email: dto.email,
    role: dto.role,
    tier: dto.tier,
    emailVerified: dto.emailVerified,
    company: dto.company === null ? null : { companyName: dto.company.companyName, businessNumber: dto.company.businessNumber },
  }
}

export function toAuthSession(dto: AuthSessionResponseDto): AuthSession {
  return {
    expiresAt: dto.expiresAt,
    account: toAccount(dto.account),
  }
}
