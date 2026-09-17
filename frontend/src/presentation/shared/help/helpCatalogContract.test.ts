import { readFileSync } from 'node:fs'

import { describe, expect, it } from 'vitest'

import { helpEntriesForSurface } from './helpContent'

/**
 * GovBiz 가이드의 AI 답변은 Core가 가진 도움말 카탈로그만 근거로 씁니다. 화면의 도움말(`helpContent.ts`)을 고치면
 * Core 리소스도 같이 고쳐야 하므로 두 원본이 어긋나면 실패합니다. 이동 경로는 서버 계약에 맞춰 질의(`?mode=`)를 뗀 값입니다.
 */
const catalogPath = new URL('../../../../../backend/core-api/src/main/resources/assistant/help-catalog.json', import.meta.url)

describe('Core 도움말 카탈로그 계약', () => {
  it('챗봇 표면 도움말과 Core 카탈로그가 같다', () => {
    const catalog = JSON.parse(readFileSync(catalogPath, 'utf8')) as { version: string; entries: unknown[] }
    const expected = helpEntriesForSurface('chatbot').map((entry) => ({
      id: entry.id,
      title: entry.title,
      question: entry.question,
      summary: entry.summary,
      body: [...entry.body],
      limitation: entry.limitation,
      audience: entry.audience,
      status: entry.status,
      routes: [...entry.routes],
      action: entry.action === null ? null : { label: entry.action.label, to: entry.action.to.split('?')[0] },
    }))

    expect(catalog.version).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(catalog.entries, 'helpContent.ts를 고쳤다면 backend/core-api/src/main/resources/assistant/help-catalog.json도 같은 내용으로 고치세요.').toEqual(expected)
  })
})
