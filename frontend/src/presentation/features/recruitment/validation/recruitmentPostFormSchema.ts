import { z } from 'zod'

import type { RecruitmentPostDraft } from '../../../../domain/entities/RecruitmentPost'

/** Core API의 ContactPatternPolicy와 같은 규칙입니다. 서버가 다시 검사합니다. */
const emailPattern = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/
const koreanPhonePattern = /(?<!\d)(\+82[\s-]?|0)(1[016789]|2|[3-6][1-5]|70|80)[\s-]?\d{3,4}[\s-]?\d{4}(?!\d)/

export function containsContact(text: string): boolean {
  return emailPattern.test(text) || koreanPhonePattern.test(text)
}

const noContact = (field: string) => ({
  message: `${field}에는 이메일·전화번호를 적지 마세요. 담당자 연락처는 제안이 수락된 뒤 공개됩니다.`,
})

export const recruitmentPostFormSchema = z.object({
  title: z.string().trim().min(1, '제목을 입력해 주세요.').max(80, '제목은 80자 이하여야 합니다.')
    .refine((value) => !containsContact(value), noContact('제목')),
  body: z.string().trim().min(1, '모집 소개를 입력해 주세요.').max(2000, '모집 소개는 2,000자 이하여야 합니다.')
    .refine((value) => !containsContact(value), noContact('모집 소개')),
  ourRole: z.enum(['LEAD', 'PARTICIPANT'], '우리 기업의 역할을 선택해 주세요.'),
  wantedRole: z.enum(['LEAD', 'PARTICIPANT', 'DEMAND'], '찾는 역할을 선택해 주세요.'),
  wantedCompanyCount: z.number('찾는 기업 수를 입력해 주세요.').int('정수로 입력해 주세요.')
    .min(1, '찾는 기업 수는 1 이상이어야 합니다.').max(10, '찾는 기업 수는 10 이하여야 합니다.'),
  wantedRegion: z.string().trim().max(60, '희망 지역은 60자 이하여야 합니다.'),
  requiredCapabilitiesText: z.string().refine((value) => splitCapabilities(value).length <= 10, '필요 역량은 10개 이하여야 합니다.')
    .refine((value) => splitCapabilities(value).every((item) => item.length <= 30), '역량 하나는 30자 이하여야 합니다.'),
  closesOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, '모집 마감일을 선택해 주세요.')
    .refine((value) => value >= todayIsoDate(), '모집 마감일은 오늘 이후여야 합니다.'),
})

export type RecruitmentPostFormValues = z.infer<typeof recruitmentPostFormSchema>

/** 쉼표·줄바꿈으로 나눈 역량 목록입니다. 빈 값과 중복은 버립니다. */
export function splitCapabilities(text: string): string[] {
  return Array.from(new Set(text.split(/[,\n]/).map((item) => item.trim()).filter((item) => item.length > 0)))
}

export function toRecruitmentPostDraft(values: RecruitmentPostFormValues): RecruitmentPostDraft {
  return {
    title: values.title,
    body: values.body,
    ourRole: values.ourRole,
    wantedRole: values.wantedRole,
    wantedCompanyCount: values.wantedCompanyCount,
    wantedRegion: values.wantedRegion,
    requiredCapabilities: splitCapabilities(values.requiredCapabilitiesText),
    closesOn: values.closesOn,
  }
}

export function toRecruitmentPostFormValues(draft: RecruitmentPostDraft): RecruitmentPostFormValues {
  return {
    title: draft.title,
    body: draft.body,
    ourRole: draft.ourRole,
    wantedRole: draft.wantedRole,
    wantedCompanyCount: draft.wantedCompanyCount,
    wantedRegion: draft.wantedRegion,
    requiredCapabilitiesText: draft.requiredCapabilities.join(', '),
    closesOn: draft.closesOn,
  }
}

/** 브라우저 로컬 날짜 기준 오늘(YYYY-MM-DD)입니다. 서버는 서울 기준으로 다시 검사합니다. */
export function todayIsoDate(now: Date = new Date()): string {
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
