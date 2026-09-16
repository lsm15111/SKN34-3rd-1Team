import { describe, expect, it } from 'vitest'
import { supportsEvidenceQuestion } from './SupportProgramEvidenceAnswer'

describe('supportsEvidenceQuestion', () => {
  it.each(['BIZINFO', 'MSIT'])('supports %s evidence questions', (sourceCode) => {
    expect(supportsEvidenceQuestion(sourceCode)).toBe(true)
  })

  it.each(['KSTARTUP', 'CNTRADE_NOTICE', 'msit', '', undefined])('does not support %s', (sourceCode) => {
    expect(supportsEvidenceQuestion(sourceCode)).toBe(false)
  })
})
