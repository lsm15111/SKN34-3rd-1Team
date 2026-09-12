import { describe, expect, it } from 'vitest'

import { appPaths, publicPaths } from '../routes/appPaths'
import { helpActionHref, helpEntriesForRoute, helpEntries, helpEntryById, relatedHelpEntries } from './helpContent'

const knownAppPaths = new Set<string>(Object.values(appPaths))

describe('help 항목 불변식', () => {
  it('id가 중복되지 않는다', () => {
    expect(new Set(helpEntries.map((entry) => entry.id)).size).toBe(helpEntries.length)
  })

  it('id는 kebab-case다', () => {
    for (const entry of helpEntries) expect(entry.id).toMatch(/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/)
  })

  it('막히는 지점 항목에는 갈 곳이 있다', () => {
    for (const entry of helpEntries.filter((candidate) => candidate.category === 'blocker')) {
      expect(entry.action, entry.id).toBeDefined()
    }
  })

  it('행동 경로는 내부 화면 경로다', () => {
    for (const entry of helpEntries) {
      if (!entry.action) continue
      expect(knownAppPaths.has(entry.action.to), entry.id).toBe(true)
    }
  })

  it('추천 경로는 실제 화면 경로다', () => {
    for (const entry of helpEntries) {
      for (const route of entry.routes) expect(knownAppPaths.has(route), `${entry.id} ${route}`).toBe(true)
    }
  })

  it('related는 존재하는 다른 항목을 가리킨다', () => {
    for (const entry of helpEntries) {
      for (const id of entry.related) {
        expect(id, entry.id).not.toBe(entry.id)
        expect(helpEntryById(id), `${entry.id} → ${id}`).toBeDefined()
      }
    }
  })

  it('질문은 사용자가 칠 말이라 물음표로 끝난다', () => {
    for (const entry of helpEntries) expect(entry.question.endsWith('?'), entry.id).toBe(true)
  })

  it('본문은 빈 문단 없이 한 문단 이상이다', () => {
    for (const entry of helpEntries) {
      expect(entry.body.length, entry.id).toBeGreaterThan(0)
      for (const paragraph of entry.body) expect(paragraph.trim(), entry.id).not.toBe('')
    }
  })

  it('요약과 한계는 비어 있지 않다', () => {
    for (const entry of helpEntries) {
      expect(entry.summary.trim(), entry.id).not.toBe('')
      expect(entry.limitation.trim(), entry.id).not.toBe('')
    }
  })

  it('갱신일은 YYYY-MM-DD다', () => {
    for (const entry of helpEntries) expect(entry.updatedOn).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('사과하는 문구를 쓰지 않는다', () => {
    for (const entry of helpEntries) {
      expect(`${entry.summary}${entry.body.join('')}`, entry.id).not.toContain('죄송')
    }
  })
})

describe('helpEntriesForRoute', () => {
  it('보고 있는 화면의 항목을 먼저 최대 3개까지 준다', () => {
    const entries = helpEntriesForRoute(appPaths.chat)
    expect(entries).toHaveLength(3)
    for (const entry of entries) {
      expect(entry.routes.length === 0 || entry.routes.includes(appPaths.chat), entry.id).toBe(true)
    }
  })

  it('공개 검색 화면은 내부 채팅 화면과 같은 항목을 준다', () => {
    expect(helpEntriesForRoute(publicPaths.landing).map((entry) => entry.id))
      .toEqual(helpEntriesForRoute(appPaths.chat).map((entry) => entry.id))
  })

  it('경로 변수가 있는 화면도 맞춘다', () => {
    expect(helpEntriesForRoute('/app/combination-reviews/12').map((entry) => entry.id))
      .toContain('review-input-revision')
  })

  it('맞는 항목이 없으면 전역 항목으로 채운다', () => {
    const entries = helpEntriesForRoute(appPaths.admin)
    expect(entries.length).toBeGreaterThan(0)
    for (const entry of entries) expect(entry.routes, entry.id).toHaveLength(0)
  })

  it('끝 슬래시가 있어도 같은 결과를 준다', () => {
    expect(helpEntriesForRoute(`${appPaths.chat}/`).map((entry) => entry.id))
      .toEqual(helpEntriesForRoute(appPaths.chat).map((entry) => entry.id))
  })
})

describe('relatedHelpEntries', () => {
  it('이어서 물어볼 항목을 순서대로 준다', () => {
    const entry = helpEntryById('relevance-score-meaning')
    expect(entry).toBeDefined()
    expect(relatedHelpEntries(entry!).map((related) => related.id)).toEqual(['eligibility-check-required'])
  })
})

describe('helpActionHref', () => {
  it('내부 화면에서는 경로를 그대로 쓴다', () => {
    expect(helpActionHref({ label: '검색 화면 열기', to: appPaths.chat }, true)).toBe(appPaths.chat)
  })

  it('공개 화면에서는 같은 내용의 공개 경로로 바꾼다', () => {
    expect(helpActionHref({ label: '검색 화면 열기', to: appPaths.chat }, false)).toBe(publicPaths.landing)
  })

  it('공개 화면에 대응이 없으면 로그인 뒤 돌아가게 한다', () => {
    expect(helpActionHref({ label: '기업 정보 등록하기', to: appPaths.profile }, false))
      .toBe(`${publicPaths.login}?next=${encodeURIComponent(appPaths.profile)}`)
  })
})
