import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router'

import { appContainer } from '../../../app/appContainer'
import { useAppDispatch } from '../../../app/hooks'
import type { AskAssistantUseCase } from '../../../domain/usecases/AskAssistantUseCase'
import { isValidAssistantMessage } from '../../../domain/usecases/AskAssistantUseCase'
import type { BrowseSavedSupportProgramsUseCase } from '../../../domain/usecases/SavedSupportProgramUseCases'
import type { IsAssistantAiEnabled } from '../../../data/config/assistantAi'
import type { KakaoChannelChatUrl } from '../../../data/config/kakaoChannel'
import { draftChanged } from '../../features/chat/state/chatSlice'
import { useAuthSession } from '../auth/hooks/useAuthSession'
import { findHelpEntry } from '../help/helpContent'
import { useReceivedProposals } from '../partner-proposal/useReceivedProposals'
import {
  type AssistantCardButton,
  type AssistantMessage,
  type AssistantQuickReply,
  contactAnswer,
  conversationDateLabel,
  findAssistantHelpTopic,
  freeTextAnswer,
  freeTextFailure,
  freeTextFallback,
  greetingMessages,
  helpAnswer,
  isAssistantHiddenOn,
  isComposerScreen,
  isMenuReplies,
  loginBenefitsAnswer,
  loginPromptAnswer,
  otherQuestionReply,
  programIdentityFrom,
  quickRepliesFor,
  receivedProposalsAnswer,
  savedProgramsAnswer,
  topicAnswer,
  userMessage,
} from './assistantConversation'
import { assistantMessages } from './assistantMessages'

type SavedProgramsUseCase = Pick<BrowseSavedSupportProgramsUseCase, 'execute'>
type AskUseCase = Pick<AskAssistantUseCase, 'execute'>

/**
 * 자유 질문은 이 시간 안에 답이 없으면 끊고 다시 시도를 안내합니다. 도구 에이전트의 관심 공고 질문은 분류 → 원문 확보(최대 6초) →
 * 근거 판단·답의 두 번 호출이라 20초를 넘길 수 있어 그보다 넉넉히 둡니다.
 */
export const assistantAnswerTimeoutMs = 45_000

/** 대화는 브라우저 세션 동안만 남습니다. 탭을 닫으면 사라지고 서버에는 보내지 않습니다. */
export const assistantConversationStorageKey = 'govbiz.assistant.conversation'
const labelShownStorageKey = 'govbiz.assistant.labelShown'
/** 첫 방문에 런처 옆 라벨을 보여 주는 시간입니다. */
export const assistantLauncherLabelMs = 5_000

/**
 * 세션에 남기는 대화입니다. [owner]는 대화를 시작한 계정(비로그인은 null)이라, 다른 계정으로 바뀐 뒤에는 복원하지 않습니다.
 * [startedAt]은 대화 구분선의 날짜이고, [conversationId]는 서버가 최근 대화를 찾는 id입니다.
 */
type StoredConversation = {
  messages: AssistantMessage[]
  quickReplies: AssistantQuickReply[]
  owner: string | null
  startedAt: string
  conversationId: string
}

/** 서버 대화 저장소의 키가 되는 새 대화 id입니다. */
function newConversationId(): string {
  return crypto.randomUUID()
}

function readStored(): StoredConversation | null {
  try {
    const raw = window.sessionStorage.getItem(assistantConversationStorageKey)
    if (raw === null) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return null
    const record = parsed as Partial<StoredConversation>
    if (!Array.isArray(record.messages) || !Array.isArray(record.quickReplies)) return null
    if (typeof record.owner !== 'string' && record.owner !== null) return null
    if (typeof record.startedAt !== 'string' || typeof record.conversationId !== 'string') return null
    return { messages: record.messages, quickReplies: record.quickReplies, owner: record.owner, startedAt: record.startedAt, conversationId: record.conversationId }
  } catch {
    return null
  }
}

function writeStored(value: StoredConversation | null) {
  try {
    if (value === null) window.sessionStorage.removeItem(assistantConversationStorageKey)
    else window.sessionStorage.setItem(assistantConversationStorageKey, JSON.stringify(value))
  } catch {
    // 저장이 막힌 브라우저에서는 대화가 새로고침에 남지 않을 뿐입니다.
  }
}

function readLabelShown(): boolean {
  try {
    return window.sessionStorage.getItem(labelShownStorageKey) === '1'
  } catch {
    return true
  }
}

/**
 * GovBiz 가이드 위젯의 대표 ViewModel입니다. 열림·대화·빠른 답변·라벨·안 읽음 배지를 소유하고,
 * 상태 질문은 기존 UseCase(관심 공고함·받은 제안함)로 답하고, 자유 질문만 Core의 도우미 API로 보냅니다.
 */
export function useAssistantViewModel(
  browseSavedPrograms: SavedProgramsUseCase = appContainer.resolve('browseSavedSupportProgramsUseCase'),
  kakaoChannelChatUrl: KakaoChannelChatUrl = appContainer.resolve('kakaoChannelChatUrl'),
  askAssistant: AskUseCase = appContainer.resolve('askAssistantUseCase'),
  isAssistantAiEnabled: IsAssistantAiEnabled = appContainer.resolve('isAssistantAiEnabled'),
) {
  const dispatchToStore = useAppDispatch()
  const { pathname, search } = useLocation()
  const { account, isAuthenticated, hasCompany, status } = useAuthSession()
  const receivedProposals = useReceivedProposals()
  // 채널 주소는 빌드 환경값이라 인스턴스 동안 고정입니다. 대화 규칙 함수에는 값으로 넘겨 환경을 직접 읽지 않게 합니다.
  const contactUrl = useMemo(() => kakaoChannelChatUrl(), [kakaoChannelChatUrl])
  // 모델 호출은 빌드 스위치로만 켭니다. 꺼져 있으면 자유 입력을 주제 알약으로 돌려보내 비용이 들지 않습니다.
  const aiEnabled = useMemo(() => isAssistantAiEnabled(), [isAssistantAiEnabled])
  const session = useMemo(() => ({ isAuthenticated, hasCompany, contactUrl }), [isAuthenticated, hasCompany, contactUrl])
  // 로그인 복원 중에는 계정을 모르므로 대화 주인을 판단하지 않습니다.
  const owner = status === 'unknown' ? undefined : (account?.email ?? null)
  const [stored] = useState(readStored)
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState<AssistantMessage[]>(() => stored?.messages ?? [])
  const [quickReplies, setQuickReplies] = useState<AssistantQuickReply[]>(() => stored?.quickReplies ?? [])
  const [startedAt, setStartedAt] = useState(() => stored?.startedAt ?? new Date().toISOString())
  const [conversationId, setConversationId] = useState(() => stored?.conversationId ?? newConversationId())
  const [isTyping, setIsTyping] = useState(false)
  const conversationOwnerRef = useRef<string | null | undefined>(stored?.owner)
  // 진행 중인 요청과 대화 세대입니다. 새 대화·계정 전환으로 세대가 바뀌면 늦게 온 답을 버립니다.
  const pendingRef = useRef<AbortController | null>(null)
  const generationRef = useRef(0)
  const [hasUnread, setHasUnread] = useState(false)
  const [showLabel, setShowLabel] = useState(() => !readLabelShown())
  const isOpenRef = useRef(isOpen)
  isOpenRef.current = isOpen

  // 첫 방문 라벨은 몇 초 뒤 접히고 그 세션에는 다시 보이지 않습니다.
  useEffect(() => {
    if (!showLabel) return
    const timer = setTimeout(() => {
      setShowLabel(false)
      try { window.sessionStorage.setItem(labelShownStorageKey, '1') } catch { /* 세션 저장 불가 */ }
    }, assistantLauncherLabelMs)
    return () => clearTimeout(timer)
  }, [showLabel])

  useEffect(() => {
    if (messages.length === 0) {
      writeStored(null)
      return
    }
    // 계정을 아직 모르면 저장을 미룹니다. 주인이 없는 대화가 다른 계정에 복원되지 않게 합니다.
    const conversationOwner = conversationOwnerRef.current ?? owner
    if (conversationOwner === undefined) return
    writeStored({ messages, quickReplies, owner: conversationOwner, startedAt, conversationId })
  }, [conversationId, messages, owner, quickReplies, startedAt])

  const routeReplies = useCallback(() => quickRepliesFor(session, pathname), [pathname, session])

  /** 진행 중인 답을 취소하고 세대를 올립니다. 이미 보낸 요청의 답이 와도 새 대화에 붙지 않습니다. */
  const cancelPending = useCallback(() => {
    pendingRef.current?.abort()
    pendingRef.current = null
    generationRef.current += 1
    setIsTyping(false)
  }, [])

  useEffect(() => () => { pendingRef.current?.abort() }, [])

  // 로그아웃·다른 계정 로그인이면 이전 계정의 대화(관심 공고 제목 등)를 지우고 다음 질문에 싣지 않습니다.
  // 비로그인 대화에는 계정 정보가 없으므로 로그인 뒤에도 이어 갑니다.
  useEffect(() => {
    if (owner === undefined) return
    const previous = conversationOwnerRef.current
    conversationOwnerRef.current = owner
    if (previous === undefined || previous === null || previous === owner) return
    cancelPending()
    setMessages([])
    setQuickReplies([])
    setStartedAt(new Date().toISOString())
    setConversationId(newConversationId())
    writeStored(null)
  }, [cancelPending, owner])

  // 화면을 옮기거나 로그인 상태가 바뀌면, 메뉴를 보고 있을 때만 새 기준 메뉴로 바꿉니다.
  useEffect(() => {
    setQuickReplies((current) => (isMenuReplies(current) ? routeReplies() : current))
  }, [routeReplies])

  const append = useCallback((next: AssistantMessage[], followUps: AssistantQuickReply[]) => {
    setMessages((current) => [...current, ...next])
    setQuickReplies(followUps)
    if (!isOpenRef.current) setHasUnread(true)
  }, [])

  const open = useCallback(() => {
    setIsOpen(true)
    setHasUnread(false)
    setShowLabel(false)
    if (messages.length === 0) {
      setMessages(greetingMessages())
      setQuickReplies(routeReplies())
      setStartedAt(new Date().toISOString())
    } else if (quickReplies.length === 0) {
      setQuickReplies(routeReplies())
    }
  }, [messages.length, quickReplies.length, routeReplies])

  const close = useCallback(() => setIsOpen(false), [])

  const startNewConversation = useCallback(() => {
    cancelPending()
    setMessages(greetingMessages())
    setQuickReplies(routeReplies())
    setStartedAt(new Date().toISOString())
    setConversationId(newConversationId())
  }, [cancelPending, routeReplies])

  const returnTo = `${pathname}${search}`

  /**
   * 자유 질문을 Core에 보냅니다. 대화 id와 현재 화면 경로·공고 선택 여부만 싣고, 최근 대화와 근거 도움말은 Core가 가진 것을 씁니다.
   * 45초 안에 답이 없으면 끊고 다시 시도를 안내합니다.
   * 답을 기다리는 동안에는 새 질문을 받지 않고, 그 사이 대화가 바뀌면 늦게 온 답을 버립니다.
   */
  const submitText = useCallback(async (text: string) => {
    const trimmed = text.trim()
    if (trimmed === '' || pendingRef.current !== null) return
    const asked = userMessage(trimmed)
    if (!aiEnabled || !isValidAssistantMessage(trimmed)) {
      append([asked, freeTextFallback()], routeReplies())
      return
    }
    setMessages((current) => [...current, asked])
    setQuickReplies([])
    setIsTyping(true)
    const controller = new AbortController()
    pendingRef.current = controller
    const generation = generationRef.current
    const timer = setTimeout(() => controller.abort(), assistantAnswerTimeoutMs)
    try {
      const result = await askAssistant.execute({
        message: trimmed,
        conversationId,
        context: { route: pathname.replace(/\/+$/, '') || '/', programSelected: programIdentityFrom(pathname, search) !== null },
      }, controller.signal)
      if (generation !== generationRef.current) return
      const answer = result.outcome === 'answered'
        ? freeTextAnswer(result.answer, { pathname, search, session, returnTo })
        : freeTextFailure(trimmed, result.outcome === 'rate-limited' ? assistantMessages.rateLimited(result.retryAfterSeconds) : assistantMessages.unavailable)
      append([answer], answer.role === 'assistant' && answer.followUps.length > 0 ? answer.followUps : routeReplies())
    } catch {
      if (generation !== generationRef.current) return
      const answer = freeTextFailure(trimmed, assistantMessages.loadFailed)
      append([answer], answer.role === 'assistant' ? answer.followUps : [])
    } finally {
      clearTimeout(timer)
      if (generation === generationRef.current) {
        pendingRef.current = null
        setIsTyping(false)
      }
    }
  }, [aiEnabled, append, askAssistant, conversationId, pathname, returnTo, routeReplies, search, session])

  /** 검색 이동 버튼은 검색 입력창에 도우미가 고른 검색어를 미리 채웁니다. 검색 자체는 사용자가 보낼 때 시작합니다. */
  const prepareNavigation = useCallback((button: AssistantCardButton) => {
    if (button.searchQuery !== undefined) dispatchToStore(draftChanged(button.searchQuery))
  }, [dispatchToStore])

  const pickQuickReply = useCallback(async (reply: AssistantQuickReply) => {
    // 답을 기다리는 중에는 알약도 받지 않습니다. 화면도 그동안 알약을 숨깁니다.
    if (pendingRef.current !== null) return
    if (reply.kind === 'other') {
      setQuickReplies(routeReplies())
      return
    }
    if (reply.kind === 'retry') {
      await submitText(reply.text ?? '')
      return
    }
    const asked = userMessage(reply.label)
    setMessages((current) => [...current, asked])
    setQuickReplies([])

    if (reply.kind === 'topic') {
      const topic = reply.topicId === undefined ? undefined : findAssistantHelpTopic(reply.topicId)
      const answer = topic === undefined ? freeTextFallback() : topicAnswer(topic)
      append([answer], answer.role === 'assistant' && answer.followUps.length > 0 ? answer.followUps : routeReplies())
      return
    }
    if (reply.kind === 'help') {
      const entry = reply.helpId === undefined ? undefined : findHelpEntry(reply.helpId)
      const answer = entry === undefined ? freeTextFallback() : helpAnswer(entry, pathname, session)
      append([answer], answer.role === 'assistant' && answer.followUps.length > 0 ? answer.followUps : routeReplies())
      return
    }
    if (reply.kind === 'login-benefits') {
      const answer = loginBenefitsAnswer(returnTo)
      append([answer], answer.role === 'assistant' ? answer.followUps : [])
      return
    }
    if (reply.kind === 'contact') {
      const answer = contactAnswer(contactUrl)
      append([answer], answer.role === 'assistant' ? answer.followUps : [])
      return
    }
    if (!isAuthenticated) {
      const answer = loginPromptAnswer(returnTo)
      append([answer], [otherQuestionReply])
      return
    }
    if (reply.kind === 'received-proposals') {
      const answer = receivedProposalsAnswer(receivedProposals, session)
      append([answer], answer.role === 'assistant' ? answer.followUps : [])
      return
    }
    // 관심 공고는 UseCase로 읽습니다. 실패해도 대화를 막지 않고 안내로 남깁니다.
    const controller = new AbortController()
    pendingRef.current = controller
    const generation = generationRef.current
    setIsTyping(true)
    try {
      const saved = await browseSavedPrograms.execute()
      if (generation !== generationRef.current) return
      const answer = savedProgramsAnswer(saved, new Date())
      append([answer], answer.role === 'assistant' ? answer.followUps : [])
    } catch {
      if (generation !== generationRef.current) return
      append([{ ...freeTextFallback(), paragraphs: [assistantMessages.loadFailed], tone: 'warn', followUps: [reply, otherQuestionReply] } as AssistantMessage], [reply, otherQuestionReply])
    } finally {
      if (generation === generationRef.current) {
        pendingRef.current = null
        setIsTyping(false)
      }
    }
  }, [append, browseSavedPrograms, contactUrl, isAuthenticated, pathname, receivedProposals, returnTo, routeReplies, session, submitText])

  return {
    isHidden: isAssistantHiddenOn(pathname),
    isLifted: isComposerScreen(pathname),
    isOpen,
    open,
    close,
    toggle: () => { if (isOpen) close(); else open() },
    showLabel: showLabel && !isOpen,
    hasUnread: hasUnread && !isOpen,
    messages,
    quickReplies,
    isTyping,
    dateLabel: conversationDateLabel(startedAt, new Date()),
    pickQuickReply: (reply: AssistantQuickReply) => { void pickQuickReply(reply) },
    submitText: (text: string) => { void submitText(text) },
    prepareNavigation,
    startNewConversation,
  }
}

export type AssistantViewModel = ReturnType<typeof useAssistantViewModel>
