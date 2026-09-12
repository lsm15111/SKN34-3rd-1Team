import { useCallback, useMemo, useRef, useState } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { HelpAnswerSource, HelpAnswerStatus } from '../../../../domain/entities/HelpAnswer'
import { HelpAnswerError } from '../../../../domain/errors/HelpAnswerError'
import type { SupportProgramEvidenceAnswer } from '../../../../domain/entities/SupportProgramEvidenceAnswer'
import { SupportProgramRequestError } from '../../../../domain/errors/SupportProgramRequestError'
import type { SupportProgramIdentity } from '../../../../domain/repositories/SupportProgramRepository'
import type { AskHelpQuestionUseCase } from '../../../../domain/usecases/AskHelpQuestionUseCase'
import type { AskSupportProgramEvidenceQuestionUseCase } from '../../../../domain/usecases/AskSupportProgramEvidenceQuestionUseCase'
import { supportProgramEvidenceMessages } from '../../../shared/support-program/supportProgramEvidenceMessages'
import { helpEntries, helpEntriesForRoute, helpEntryById, matchHelpEntry } from '../../../shared/help/helpContent'
import type { HelpEntry } from '../../../shared/help/helpTypes'

/** 항목 전량을 보내도 컨텍스트에 들어가므로 검색 단계 없이 한 번에 묻습니다. */
const helpAnswerSources: HelpAnswerSource[] = helpEntries.map((entry) => ({
  id: entry.id,
  title: entry.title,
  summary: entry.summary,
  body: [...entry.body],
  limitation: entry.limitation,
}))

/** 모델 실행(30초)에 여유를 두고 질문 요청 시간을 제한합니다. */
export const helpAnswerTimeoutMilliseconds = 45_000

/** 원문 수집과 근거 답변을 함께 기다리므로 원문 질문 화면과 같은 제한을 씁니다. */
export const programAnswerTimeoutMilliseconds = 70_000

export type HelpAbstention = Exclude<HelpAnswerStatus, 'ANSWERED'>

export type HelpMessageBody =
  | { role: 'question'; text: string }
  | { role: 'answer'; entry: HelpEntry }
  | { role: 'pending' }
  | { role: 'generated'; text: string; citations: HelpEntry[] }
  | { role: 'abstained'; status: HelpAbstention }
  | { role: 'program-answer'; answer: SupportProgramEvidenceAnswer }
  | { role: 'program-notice'; message: string }
  | { role: 'failed'; reason: 'rate-limited' | 'unavailable' | 'cancelled' }

export type HelpMessage = HelpMessageBody & { id: number }

type HelpQuestionUseCase = Pick<AskHelpQuestionUseCase, 'execute'>
type ProgramQuestionUseCase = Pick<AskSupportProgramEvidenceQuestionUseCase, 'execute'>

/**
 * 도움말 패널의 대화 상태입니다. 추천 질문과 같은 뜻의 질문은 가진 항목으로 바로 답해 AI를 부르지 않고,
 * 목록에 없는 자유 질문만 항목 전량을 근거로 AI에 묻습니다. 근거가 없으면 답을 만들지 않고 기권을 그립니다.
 */
export function useHelpPanelViewModel(
  pathname: string,
  useCase?: HelpQuestionUseCase,
  programUseCase?: ProgramQuestionUseCase,
) {
  const askHelpQuestion = useCase ?? appContainer.resolve('askHelpQuestionUseCase')
  const askProgramQuestion = programUseCase ?? appContainer.resolve('askSupportProgramEvidenceQuestionUseCase')
  const [messages, setMessages] = useState<HelpMessage[]>([])
  const nextId = useRef(0)
  const request = useRef<AbortController | null>(null)
  const suggestions = useMemo(() => helpEntriesForRoute(pathname), [pathname])

  const append = useCallback((...added: HelpMessageBody[]) => {
    setMessages((previous) => [...previous, ...added.map((message) => ({ ...message, id: (nextId.current += 1) }))])
  }, [])

  const settlePending = useCallback((settled: HelpMessageBody) => {
    setMessages((previous) => previous.map((message) => (
      message.role === 'pending' ? { ...settled, id: message.id } : message
    )))
  }, [])

  const askEntry = useCallback((entry: HelpEntry) => {
    append({ role: 'question', text: entry.question }, { role: 'answer', entry })
  }, [append])

  const askText = useCallback(async (text: string) => {
    const asked = text.trim()
    if (!asked || request.current) return

    const matched = matchHelpEntry(asked)
    if (matched) {
      append({ role: 'question', text: asked }, { role: 'answer', entry: matched })
      return
    }

    append({ role: 'question', text: asked }, { role: 'pending' })
    const controller = new AbortController()
    request.current = controller
    const timeout = setTimeout(() => controller.abort(), helpAnswerTimeoutMilliseconds)
    try {
      const answer = await askHelpQuestion.execute({ question: asked, entries: helpAnswerSources }, controller.signal)
      settlePending(answer.answerStatus === 'ANSWERED'
        ? {
          role: 'generated',
          text: answer.answer,
          citations: answer.citationEntryIds.flatMap((id) => {
            const entry = helpEntryById(id)
            return entry ? [entry] : []
          }),
        }
        : { role: 'abstained', status: answer.answerStatus })
    } catch (error) {
      if (controller.signal.aborted) settlePending({ role: 'failed', reason: 'cancelled' })
      else settlePending({ role: 'failed', reason: error instanceof HelpAnswerError ? error.reason : 'unavailable' })
    } finally {
      clearTimeout(timeout)
      request.current = null
    }
  }, [append, askHelpQuestion, settlePending])

  /**
   * 공고 내용은 도움말 항목에 없으므로 같은 패널에서 공고 원문 근거 답변에 넘깁니다.
   * 사용자가 화면을 옮기지 않아도 내부 문서와 외부 문서를 한 창에서 확인할 수 있습니다.
   */
  const askProgram = useCallback(async (question: string, identity: SupportProgramIdentity) => {
    if (request.current) return
    append({ role: 'pending' })
    const controller = new AbortController()
    request.current = controller
    const timeout = setTimeout(() => controller.abort(), programAnswerTimeoutMilliseconds)
    try {
      const result = await askProgramQuestion.execute({ ...identity, question }, controller.signal)
      if (result.outcome !== 'answer') {
        settlePending({ role: 'program-notice', message: supportProgramEvidenceMessages[result.outcome] })
      } else if (result.answer.answerStatus === 'INSUFFICIENT_EVIDENCE') {
        settlePending({ role: 'program-notice', message: supportProgramEvidenceMessages['insufficient-evidence'] })
      } else {
        settlePending({ role: 'program-answer', answer: result.answer })
      }
    } catch (error) {
      if (controller.signal.aborted) settlePending({ role: 'failed', reason: 'cancelled' })
      else {
        settlePending({
          role: 'failed',
          reason: error instanceof SupportProgramRequestError && error.reason === 'rate-limited'
            ? 'rate-limited'
            : 'unavailable',
        })
      }
    } finally {
      clearTimeout(timeout)
      request.current = null
    }
  }, [append, askProgramQuestion, settlePending])

  const stop = useCallback(() => {
    request.current?.abort()
  }, [])

  return {
    messages,
    suggestions,
    isPending: messages.some((message) => message.role === 'pending'),
    askEntry,
    askText,
    askProgram,
    stop,
  }
}
