import { afterEach, describe, expect, it, vi } from 'vitest'

import type { AssistantQuestion } from '../../../domain/repositories/AssistantRepository'
import { askAssistantApi, AssistantApiError } from '../assistantApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

const question: AssistantQuestion = {
  message: '관심 공고 마감 언제야?',
  conversationId: '8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f',
  context: { route: '/app/chat', programSelected: false },
}

const finalAnswer = {
  intent: 'ACCOUNT_STATE',
  answer: '관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.',
  citations: [],
  clarificationQuestion: null,
  searchQuery: null,
  accountTopic: 'SAVED_PROGRAMS',
  navigation: { label: '관심 공고함 열기', to: '/app/saved-programs' },
  cards: [],
  actions: [],
}

/** 조각이 어디서 잘려 와도 같은 결과가 나오는지 보려고 덩어리를 쪼개 흘려보냅니다. */
function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
  return { ok: true, status: 200, headers: new Headers({ 'Content-Type': 'text/event-stream' }), body: stream } as unknown as Response
}

function event(name: string, data: unknown): string {
  return `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`
}

describe('askAssistantApi 스트리밍', () => {
  it('진행 상황과 답변 조각을 넘기고 마지막 답만 결과로 돌려준다', async () => {
    const body = [
      event('status', { phase: 'THINKING' }),
      event('status', { phase: 'READING' }),
      event('text', { delta: '관심 공고 2건 중 ' }),
      event('text', { delta: '가장 빠른 마감은 9월 30일입니다.' }),
      event('final', finalAnswer),
    ].join('')
    // 덩어리 경계를 일부러 이벤트 중간에 둡니다.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([body.slice(0, 30), body.slice(30, 120), body.slice(120)])))
    const phases: string[] = []
    const deltas: string[] = []

    const answer = await askAssistantApi(question, undefined, {
      onStatus: (phase) => phases.push(phase),
      onText: (delta) => deltas.push(delta),
    })

    expect(phases).toEqual(['THINKING', 'READING'])
    expect(deltas.join('')).toBe(finalAnswer.answer)
    expect(answer).toEqual(finalAnswer)
    const request = (globalThis.fetch as unknown as { mock: { calls: [string, RequestInit][] } }).mock.calls[0]![1]
    expect((request.headers as Record<string, string>).Accept).toBe('text/event-stream')
  })

  it('중간에 오류 이벤트가 오면 답을 만들지 않고 실패로 끝낸다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      event('status', { phase: 'THINKING' }),
      event('text', { delta: '쓰다 말았습니다' }),
      event('error', { code: 'AI_SERVICE_TIMEOUT' }),
      event('final', finalAnswer),
    ])))

    const error = await askAssistantApi(question).catch((thrown: unknown) => thrown)

    expect(error).toBeInstanceOf(AssistantApiError)
    expect((error as AssistantApiError).status).toBe(503)
    expect((error as AssistantApiError).code).toBe('AI_SERVICE_TIMEOUT')
  })

  it('마지막 답 없이 연결이 끊기면 조각만으로 답을 만들지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([event('text', { delta: '끊긴 답' })])))

    const error = await askAssistantApi(question).catch((thrown: unknown) => thrown)

    expect(error).toBeInstanceOf(AssistantApiError)
    expect((error as AssistantApiError).code).toBe('ASSISTANT_STREAM_INCOMPLETE')
  })

  it('모르는 이벤트는 넘기고 계약을 어긴 마지막 답은 거절한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      event('keep-alive', { note: '무시합니다' }),
      event('final', finalAnswer),
    ])))
    await expect(askAssistantApi(question)).resolves.toEqual(finalAnswer)

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      event('final', { ...finalAnswer, navigation: { label: '바깥으로', to: 'https://evil.example' } }),
    ])))
    await expect(askAssistantApi(question)).rejects.toThrow()
  })

  it('중간에 Accept가 바뀌어 JSON으로 오면 그대로 한 번에 읽는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify(finalAnswer), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    ))
    const deltas: string[] = []

    await expect(askAssistantApi(question, undefined, { onText: (delta) => deltas.push(delta) })).resolves.toEqual(finalAnswer)
    expect(deltas).toEqual([])
  })
})
