import { z } from 'zod'

import type { AssistantQuestion } from '../../domain/repositories/AssistantRepository'
import { getCoreApiBaseUrl } from './coreApiConfig'
import { assistantAnswerDtoSchema, type AssistantAnswerDto } from '../models/AssistantAnswerDto'

const ASSISTANT_MESSAGES_PATH = '/api/v1/assistant/messages'
/** SSE는 빈 줄 하나로 덩어리를 나눕니다. */
const EVENT_SEPARATOR = '\n\n'

/** 도우미 endpoint의 HTTP 상태와 ProblemDetail `code`를 Repository가 업무 결과로 바꿀 수 있게 합니다. */
export class AssistantApiError extends Error {
  readonly status: number
  readonly code: string | null
  /** 429 응답의 `retryAfterSeconds`. 없거나 정수가 아니면 null입니다. */
  readonly retryAfterSeconds: number | null

  constructor(status: number, code: string | null, retryAfterSeconds: number | null = null) {
    super(`Core API returned HTTP ${status} for the assistant request.`)
    this.name = 'AssistantApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** 답을 만드는 동안 오는 알림입니다. 진행 단계와 아직 검증 전인 답변 조각이라 화면은 "만드는 중"으로만 씁니다. */
export type AssistantProgress = {
  onStatus?: (phase: AssistantStreamPhase) => void
  onText?: (delta: string) => void
}

export const assistantStreamPhaseSchema = z.enum(['THINKING', 'READING'])
export type AssistantStreamPhase = z.infer<typeof assistantStreamPhaseSchema>

const statusEventSchema = z.object({ phase: assistantStreamPhaseSchema })
const textEventSchema = z.object({ delta: z.string().min(1).max(600) })
const errorEventSchema = z.object({ code: z.string().min(1).max(64) })

/**
 * 비로그인도 물을 수 있지만 세션 쿠키가 있으면 함께 보내 회원 상태 답을 받습니다.
 *
 * 답이 만들어지는 동안을 보여 주려고 SSE로 받습니다. 중간에 오는 조각은 [progress]로만 넘기고, 화면에 확정으로 남는 답은
 * 마지막 `final` 이벤트뿐입니다. 사이에 무언가가 Accept를 바꿔 JSON으로 오면 그대로 한 번에 읽습니다.
 */
export async function askAssistantApi(
  question: AssistantQuestion, signal?: AbortSignal, progress?: AssistantProgress,
): Promise<AssistantAnswerDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${ASSISTANT_MESSAGES_PATH}`, {
    method: 'POST',
    headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json' },
    body: JSON.stringify(question),
    credentials: 'include',
    signal,
  })
  if (!response.ok) {
    const problem = await readProblem(response)
    throw new AssistantApiError(response.status, problem.code, problem.retryAfterSeconds)
  }
  const streamed = response.headers.get('Content-Type')?.includes('text/event-stream') === true && response.body !== null
  if (!streamed) return assistantAnswerDtoSchema.parse(await response.json())
  return assistantAnswerDtoSchema.parse(await readFinalEvent(response.body!, progress))
}

/** SSE 본문을 덩어리째 읽습니다. 마지막 답이 오기 전에 끊기면 지어내지 않고 오류로 끝냅니다. */
async function readFinalEvent(body: ReadableStream<Uint8Array>, progress?: AssistantProgress): Promise<unknown> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let final: unknown = null
  try {
    for (;;) {
      const { done, value } = await reader.read()
      buffer += done ? '' : decoder.decode(value, { stream: true })
      let boundary = buffer.indexOf(EVENT_SEPARATOR)
      while (boundary !== -1) {
        final = readEvent(buffer.slice(0, boundary), progress) ?? final
        buffer = buffer.slice(boundary + EVENT_SEPARATOR.length)
        boundary = buffer.indexOf(EVENT_SEPARATOR)
      }
      if (done) break
    }
  } finally {
    reader.releaseLock()
  }
  if (final === null) throw new AssistantApiError(503, 'ASSISTANT_STREAM_INCOMPLETE')
  return final
}

/** 덩어리 하나를 읽어 `final`이면 그 데이터를 돌려줍니다. 모르는 이름은 무시하고 `error`는 그대로 올립니다. */
function readEvent(block: string, progress?: AssistantProgress): unknown {
  const lines = block.split('\n')
  const name = lines.find((line) => line.startsWith('event:'))?.slice('event:'.length).trim()
  const raw = lines.find((line) => line.startsWith('data:'))?.slice('data:'.length)
  if (name === undefined || raw === undefined) return null
  const data: unknown = JSON.parse(raw)
  if (name === 'status') {
    progress?.onStatus?.(statusEventSchema.parse(data).phase)
    return null
  }
  if (name === 'text') {
    progress?.onText?.(textEventSchema.parse(data).delta)
    return null
  }
  if (name === 'error') throw new AssistantApiError(503, errorEventSchema.parse(data).code)
  return name === 'final' ? data : null
}

async function readProblem(response: Response): Promise<{ code: string | null; retryAfterSeconds: number | null }> {
  try {
    const payload: unknown = await response.json()
    if (typeof payload !== 'object' || payload === null) return { code: null, retryAfterSeconds: null }
    const record = payload as { code?: unknown; retryAfterSeconds?: unknown }
    return {
      code: typeof record.code === 'string' ? record.code : null,
      retryAfterSeconds: Number.isInteger(record.retryAfterSeconds) ? (record.retryAfterSeconds as number) : null,
    }
  } catch {
    return { code: null, retryAfterSeconds: null }
  }
}
