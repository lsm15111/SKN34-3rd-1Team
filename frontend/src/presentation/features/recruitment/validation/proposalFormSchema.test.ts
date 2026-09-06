import { describe, expect, it } from 'vitest'

import { proposalFormSchema } from './proposalFormSchema'

describe('proposalFormSchema', () => {
  it('trims the message and keeps it within 500 characters', () => {
    expect(proposalFormSchema.parse({ message: '  참여하고 싶습니다.  ' })).toEqual({ message: '참여하고 싶습니다.' })
    expect(proposalFormSchema.safeParse({ message: '가'.repeat(500) }).success).toBe(true)
    expect(proposalFormSchema.safeParse({ message: '가'.repeat(501) }).error?.issues[0]?.message)
      .toBe('제안 메시지는 500자 이하여야 합니다.')
    expect(proposalFormSchema.safeParse({ message: '   ' }).error?.issues[0]?.message).toBe('제안 메시지를 입력해 주세요.')
  })

  it('rejects contact details with the same rule as the server', () => {
    expect(proposalFormSchema.safeParse({ message: '연락은 partner@vision.co.kr' }).error?.issues[0]?.message)
      .toContain('이메일·전화번호')
    expect(proposalFormSchema.safeParse({ message: '사업자등록번호 220-81-62517 기업입니다' }).success).toBe(true)
  })
})
