import { describe, expect, it } from 'vitest'

import { findHelpEntry, helpEntries } from '../help/helpContent'
import {
  type AssistantMessage,
  type AssistantSession,
  assistantHelpTopics,
  conversationDateLabel,
  freeTextAnswer,
  helpAnswer,
  isMenuReplies,
  pageQuestionLimit,
  quickRepliesFor,
  topicAnswer,
} from './assistantConversation'
import { assistantMessages } from './assistantMessages'

const guest: AssistantSession = { isAuthenticated: false, hasCompany: false, contactUrl: null }
const member: AssistantSession = { isAuthenticated: true, hasCompany: false, contactUrl: null }
const company: AssistantSession = { isAuthenticated: true, hasCompany: true, contactUrl: 'https://pf.kakao.com/_x/chat' }

function bot(message: AssistantMessage) {
  if (message.role !== 'assistant') throw new Error('assistant message expected')
  return message
}

describe('quickRepliesFor', () => {
  it.each([
    ['/', ['search-confirm-card', 'search-score-meaning']],
    ['/app/chat', ['search-confirm-card', 'search-score-meaning']],
    ['/app/chat/', ['search-confirm-card', 'search-score-meaning']],
    ['/app/saved-programs', ['saved-programs-pipeline']],
    ['/app/combination-reviews/12/runs/3', ['review-save-vs-run', 'review-input-revision']],
    ['/app/reports', ['daily-report']],
    ['/app/proposals', ['proposal-box']],
    ['/app/pricing', []],
    ['/partners', ['partner-write-requires-company']],
  ])('%s 화면은 그 화면 도움말 질문만 앞에 둔다', (pathname, ids) => {
    const replies = quickRepliesFor(member, pathname)
    const page = replies.slice(0, replies.findIndex((reply) => reply.kind === 'topic'))
    expect(page.map((reply) => reply.helpId)).toEqual(ids)
    expect(page.length).toBeLessThanOrEqual(pageQuestionLimit)
  })

  it('주제·상태·문의 순서는 로그인·기업·채널 설정에 따라 정해진다', () => {
    const tail = (session: AssistantSession) => quickRepliesFor(session, '/app/pricing').map((reply) => reply.label)
    const topics = assistantHelpTopics.map((topic) => topic.label)
    expect(tail(guest)).toEqual([...topics, assistantMessages.quickLoginBenefits])
    expect(tail(member)).toEqual([...topics, assistantMessages.quickSavedPrograms])
    expect(tail(company)).toEqual([...topics, assistantMessages.quickSavedPrograms, assistantMessages.quickReceivedProposals, assistantMessages.quickContact])
  })

  it('메뉴 여부는 주제 알약이 있는지로 판단한다', () => {
    expect(isMenuReplies(quickRepliesFor(guest, '/'))).toBe(true)
    expect(isMenuReplies(bot(topicAnswer(assistantHelpTopics[0]!)).followUps)).toBe(false)
    expect(isMenuReplies([])).toBe(false)
  })
})

describe('helpAnswer 권한 안내', () => {
  it.each(helpEntries.filter((entry) => entry.surfaces.includes('chatbot')).map((entry) => [entry.id, entry] as const))(
    '%s 항목은 비로그인에게 로그인 화면으로 튕기는 버튼을 주지 않는다',
    (_id, entry) => {
      const answer = bot(helpAnswer(entry, '/', guest))
      for (const button of answer.card?.buttons ?? []) {
        expect(button.to.startsWith('/app')).toBe(false)
      }
      if (entry.audience === 'public') expect(answer.paragraphs).not.toContain(assistantMessages.helpNeedsLogin)
      else expect(answer.paragraphs).toContain(assistantMessages.helpNeedsLogin)
    },
  )

  it('앱 안에서 로그인한 회원에게는 원래 경로 버튼을 그대로 준다', () => {
    const entry = findHelpEntry('saved-programs-pipeline')!
    const answer = bot(helpAnswer(entry, '/app/chat', member))
    expect(answer.card?.buttons).toEqual([{ label: entry.action!.label, to: entry.action!.to }])
    expect(answer.paragraphs).not.toContain(assistantMessages.helpNeedsLogin)
  })

  it('검색처럼 공개 경로가 있는 기능은 비로그인에게도 공개 경로 그대로 준다', () => {
    const entry = findHelpEntry('search-confirm-card')!
    expect(bot(helpAnswer(entry, '/', guest)).card?.buttons).toEqual([{ label: entry.action!.label, to: '/' }])
  })
})

describe('freeTextAnswer 이동 버튼', () => {
  const base = { citations: [], clarificationQuestion: null, searchQuery: null, accountTopic: null, cards: [] }

  it('AI가 고른 회원 화면 이동은 비로그인에게 로그인하고 여는 버튼으로 바꾼다', () => {
    const answer = bot(freeTextAnswer(
      { ...base, intent: 'OUT_OF_SCOPE', answer: '범위 밖이에요.', navigation: null },
      { pathname: '/', search: '', session: guest, returnTo: '/' },
    ))
    expect(answer.card).toBeNull()

    const program = bot(freeTextAnswer(
      { ...base, intent: 'PROGRAM_QUESTION', answer: '공고를 골라 주세요.', navigation: { label: '관심 공고함 열기', to: '/app/saved-programs' } },
      { pathname: '/', search: '', session: guest, returnTo: '/' },
    ))
    expect(program.card?.buttons).toEqual([{ label: assistantMessages.loginAndOpen('관심 공고함 열기'), to: `/login?next=${encodeURIComponent('/app/saved-programs')}` }])
  })

  it('검색 답에는 가이드가 찾은 공고 카드와 검색어를 미리 채우는 버튼이 함께 붙는다', () => {
    const answer = bot(freeTextAnswer(
      {
        ...base, intent: 'SEARCH', answer: '모집 중인 공고 두 건을 찾았습니다.', searchQuery: '서울 창업 지원금',
        navigation: { label: '검색 화면에서 찾기', to: '/app/chat' },
        cards: [{ kind: 'PROGRAM', id: 'KSTARTUP:174520', title: '예비창업패키지', subtitle: '창업진흥원 · 2026-10-10', reason: '10월 10일까지 모집합니다.', to: '/app/support-programs/detail?sourceCode=KSTARTUP&sourceProgramId=174520' }],
      },
      { pathname: '/app/chat', search: '', session: member, returnTo: '/app/chat' },
    ))
    expect(answer.card?.rows.map((row) => row.title)).toEqual(['예비창업패키지'])
    expect(answer.card?.buttons).toEqual([{ label: '검색 화면에서 찾기', to: '/app/chat', searchQuery: '서울 창업 지원금' }])
    expect(answer.source).toBe(assistantMessages.searchSource)
  })

  it('신청 준비·중복 검토 카드는 종류 태그와 상세 경로를 그대로 쓴다', () => {
    const answer = bot(freeTextAnswer(
      {
        ...base, intent: 'ACCOUNT_STATE', answer: '준비 중 1건입니다.', accountTopic: 'APPLICATION_PREPARATIONS',
        navigation: { label: '신청 준비 열기', to: '/app/application-preparations' },
        cards: [
          { kind: 'PREPARATION', id: '31', title: '서울 AI 실증 지원사업', subtitle: '준비 중 · 2026-09-16', reason: '아직 준비 중입니다.', to: '/app/application-preparations/31' },
          { kind: 'REVIEW', id: '41', title: '혁신바우처와 R&D', subtitle: '검토 완료 · 2026-09-15', reason: '최근 실행이 끝났습니다.', to: '/app/combination-reviews/41' },
        ],
      },
      { pathname: '/app/saved-programs', search: '', session: member, returnTo: '/app/saved-programs' },
    ))
    expect(answer.card?.rows.map((row) => row.tag?.label)).toEqual([assistantMessages.cardPreparation, assistantMessages.cardReview])
    expect(answer.card?.rows.map((row) => row.to)).toEqual(['/app/application-preparations/31', '/app/combination-reviews/41'])
    expect(answer.source).toBe(assistantMessages.aiToolSource(assistantMessages.preparationsSource))
  })

  it('불명확한 질문 뒤 메뉴도 지금 화면 기준이다', () => {
    const answer = bot(freeTextAnswer(
      { ...base, intent: 'UNCLEAR', answer: null, clarificationQuestion: '무엇이 궁금하세요?', navigation: null },
      { pathname: '/app/reports', search: '', session: company, returnTo: '/app/reports' },
    ))
    expect(answer.followUps[0]?.helpId).toBe('daily-report')
  })
})

describe('conversationDateLabel', () => {
  const now = new Date('2026-09-17T13:00:00Z') // 서울 22:00

  it.each([
    ['2026-09-17T00:00:00Z', '오늘'], // 서울 09:00
    ['2026-09-16T15:00:00Z', '오늘'], // 서울 9/17 00:00
    ['2026-09-16T14:59:59Z', '9월 16일'], // 서울 9/16 23:59
    ['2025-12-31T15:30:00Z', '1월 1일'],
    ['not-a-date', '오늘'],
  ])('%s → %s', (startedAt, label) => {
    expect(conversationDateLabel(startedAt, now)).toBe(label)
  })
})
