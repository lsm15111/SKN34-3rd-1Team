import { describe, expect, it } from 'vitest'

import { normalizeRecruitmentPostDraft } from '../../../../domain/usecases/RecruitmentPostUseCases'
import {
  containsContact,
  recruitmentPostFormSchema,
  splitCapabilities,
  todayIsoDate,
  toRecruitmentPostDraft,
} from './recruitmentPostFormSchema'

const validValues = {
  title: 'AI 실증 과제 참여기관 구합니다',
  body: '라벨링을 맡아 주실 참여기관을 찾습니다.',
  ourRole: 'LEAD' as const,
  wantedRole: 'PARTICIPANT' as const,
  wantedCompanyCount: 1,
  wantedRegion: ' 서울·경기 ',
  requiredCapabilitiesText: '데이터 구축, 라벨링 운영,, 데이터 구축\n품질 검수',
  closesOn: todayIsoDate(),
}

describe('recruitmentPostFormSchema', () => {
  it('accepts a valid draft and converts the capability text into a deduplicated list', () => {
    const parsed = recruitmentPostFormSchema.parse(validValues)
    const draft = toRecruitmentPostDraft(parsed)

    expect(draft.requiredCapabilities).toEqual(['데이터 구축', '라벨링 운영', '품질 검수'])
    expect(draft.wantedRegion).toBe('서울·경기')
    expect(normalizeRecruitmentPostDraft({ ...draft, title: '  제목  ', requiredCapabilities: [' a ', 'a', ''] }))
      .toMatchObject({ title: '제목', requiredCapabilities: ['a'] })
  })

  it('rejects contact details, past closing dates and oversized capability lists with Korean messages', () => {
    const contact = recruitmentPostFormSchema.safeParse({ ...validValues, body: '문의 010-1234-5678' })
    const past = recruitmentPostFormSchema.safeParse({ ...validValues, closesOn: '2000-01-01' })
    const tooMany = recruitmentPostFormSchema.safeParse({ ...validValues, requiredCapabilitiesText: Array.from({ length: 11 }, (_, index) => `역량${index}`).join(',') })
    const count = recruitmentPostFormSchema.safeParse({ ...validValues, wantedCompanyCount: Number.NaN })

    expect(contact.success).toBe(false)
    expect(contact.error?.issues[0]?.message).toContain('이메일·전화번호')
    expect(past.error?.issues[0]?.message).toBe('모집 마감일은 오늘 이후여야 합니다.')
    expect(tooMany.error?.issues[0]?.message).toBe('필요 역량은 10개 이하여야 합니다.')
    expect(count.error?.issues[0]?.message).toBe('찾는 기업 수를 입력해 주세요.')
  })

  it('detects the same contact patterns as the server', () => {
    expect(containsContact('manager@company.co.kr')).toBe(true)
    expect(containsContact('02-123-4567')).toBe(true)
    expect(containsContact('사업자등록번호 124-81-00998')).toBe(false)
    expect(splitCapabilities(' , ')).toEqual([])
  })
})
